package mine.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fuzzing-driven implementation of TokenController that enables fine-grained
 * control of thread loop iterations. Each thread can be gated at the start of
 * its loop iteration, allowing reproducible exploration of thread interleavings
 * and deadlock scenarios.
 */
public class FuzzingTokenController implements TokenController {

    private static final int MAX_INSTANCE_ID = 20;
    
    private final Map<String, long[]> delaySequences = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> iterationCounters = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> iterationGates = new ConcurrentHashMap<>();
    private final Map<ThreadToken.Role, java.util.List<String>> roleToKeys = new ConcurrentHashMap<>();
    private final long defaultDelay;
    private final int maxIterationsPerThread;
    private final boolean useGating;
    private final long gateTimeoutMs;

    /**
     * Create a fuzzing controller with fine-grained loop control.
     *
     * @param data Fuzz input provider
     * @param tokenRegistry Registry to know which tokens exist
     * @param useGating If true, threads wait for explicit permission to proceed each iteration
     */
    public FuzzingTokenController(FuzzedDataProvider data, ThreadTokenRegistry tokenRegistry, boolean useGating) {
        this.useGating = useGating;
        this.defaultDelay = data.remainingBytes() > 4 ? data.consumeLong(0, 50) : 0;
        // Set to Integer.MAX_VALUE to allow continuous operation without iteration limit
        // Previously limited to 5-20 iterations, causing threads to exit prematurely
        this.maxIterationsPerThread = Integer.MAX_VALUE;
        this.gateTimeoutMs = 2000; // Shorter timeout for fuzzing
        System.out.println("fuzz data: " + data);
        System.out.println("size: " + data.remainingBytes());
        // Generate delay sequences for each registered thread instance
        int sequenceLength = data.remainingBytes() > 8 ? data.consumeInt(10, 50) : 10;
        
        // Initialize for each role type to maintain some backwards compatibility
        // but we'll use uniqueId as the actual key for instance-specific control
        for (ThreadToken.Role role : ThreadToken.Role.values()) {
            // Generate delays for this role type - will be used as template for instances
            long[] delays = new long[sequenceLength];
            for (int i = 0; i < sequenceLength && data.remainingBytes() > 4; i++) {
                delays[i] = data.consumeLong(0, 100);
            }
            
            // Track keys for this role for efficient role-based operations
            java.util.List<String> keysForRole = new java.util.ArrayList<>();
            
            // For each role, we need to find all registered instances and initialize them
            // We'll iterate through all possible instance IDs (a reasonable upper bound)
            for (int instanceId = 0; instanceId < MAX_INSTANCE_ID; instanceId++) {
                ThreadToken token = new ThreadToken(role, instanceId);
                Thread thread = tokenRegistry.getThread(token);
                
                // Only initialize if this instance actually exists in the registry
                if (thread != null) {
                    String uniqueKey = token.getUniqueId();
                    delaySequences.put(uniqueKey, delays.clone());
                    iterationCounters.put(uniqueKey, new AtomicInteger(0));
                    keysForRole.add(uniqueKey);
                    
                    // Initialize gating semaphores (start with 0 permits - threads must wait)
                    if (useGating) {
                        iterationGates.put(uniqueKey, new Semaphore(0));
                    }
                }
            }
            
            // Store the mapping for efficient role-based operations
            if (!keysForRole.isEmpty()) {
                roleToKeys.put(role, keysForRole);
            }
        }
    }

    @Override
    public void onLoopIteration(ThreadToken token) {
        if (token == null) return;

        // Use uniqueId to ensure each instance has its own gate/counter/delays
        String uniqueKey = token.getUniqueId();

        // Track iteration count and get current iteration number atomically
        AtomicInteger counter = iterationCounters.get(uniqueKey);
        int currentIteration = -1;
        if (counter != null) {
            currentIteration = counter.getAndIncrement();

            // Check against max iterations (set to Integer.MAX_VALUE for continuous operation)
            // Note: With Integer.MAX_VALUE, this check will effectively never trigger, allowing
            // threads to run indefinitely. Simulation termination is handled externally via stopAll().
            if (currentIteration >= maxIterationsPerThread) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // If gating is enabled, wait for permission to proceed
        boolean acquiredPermission = true;
        if (useGating) {
            Semaphore gate = iterationGates.get(uniqueKey);
            if (gate != null) {
                try {
                    // Wait for permission with timeout to prevent fuzzer hanging
                    acquiredPermission = gate.tryAcquire(gateTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        // Apply fuzz-driven delay using the captured iteration number
        // (even if timed out, we still apply delay for consistent behavior)
        long delay = getDelayForIteration(uniqueKey, currentIteration);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Note: We do NOT interrupt on timeout. The timeout is just to prevent
        // the fuzzer from hanging forever. The thread continues its normal execution.
        // Previous behavior (interrupting on timeout) caused threads to exit their main
        // loop entirely, as the interrupt flag triggered loop termination conditions.
        if (useGating && !acquiredPermission) {
            // Log timeout for debugging but don't interrupt the thread
            // Using System.err as this is fuzzing/test code and we want immediate output
            System.err.println("Warning: Token gate timeout for " + uniqueKey + " at iteration " + currentIteration);
        }
    }

    /**
     * Release a specific thread instance for one more loop iteration (when gating is enabled).
     *
     * @param token The token identifying the thread instance to release
     */
    public void releaseIteration(ThreadToken token) {
        if (useGating) {
            Semaphore gate = iterationGates.get(token.getUniqueId());
            if (gate != null) {
                gate.release();
            }
        }
    }

    /**
     * Release multiple iterations for a specific thread instance.
     *
     * @param token The token identifying the thread instance to release
     * @param count Number of iterations to release
     */
    public void releaseIterations(ThreadToken token, int count) {
        if (useGating) {
            Semaphore gate = iterationGates.get(token.getUniqueId());
            if (gate != null) {
                gate.release(count);
            }
        }
    }
    
    /**
     * Release one iteration for all instances of a given role (backwards compatibility).
     * This releases one iteration for each registered instance of the role.
     *
     * @param role The role to release
     */
    public void releaseIteration(ThreadToken.Role role) {
        if (useGating) {
            java.util.List<String> keys = roleToKeys.get(role);
            if (keys != null) {
                for (String key : keys) {
                    Semaphore gate = iterationGates.get(key);
                    if (gate != null) {
                        gate.release();
                    }
                }
            }
        }
    }

    /**
     * Release multiple iterations for all instances of a role (backwards compatibility).
     * This releases the specified count for each registered instance of the role.
     *
     * @param role The role to release
     * @param count Number of iterations to release for each instance
     */
    public void releaseIterations(ThreadToken.Role role, int count) {
        if (useGating) {
            java.util.List<String> keys = roleToKeys.get(role);
            if (keys != null) {
                for (String key : keys) {
                    Semaphore gate = iterationGates.get(key);
                    if (gate != null) {
                        gate.release(count);
                    }
                }
            }
        }
    }

    @Override
    public void beforeOperation(ThreadToken token, String operation) {
        // Hook available for future fine-grained control
    }

    @Override
    public void afterOperation(ThreadToken token, String operation) {
        // Hook available for future fine-grained control
    }

    private long getDelayForIteration(String roleKey, int iteration) {
        long[] delays = delaySequences.get(roleKey);
        if (delays == null || delays.length == 0 || iteration < 0) {
            return defaultDelay;
        }

        if (iteration >= delays.length) {
            return delays[delays.length - 1]; // Use last delay
        }

        return delays[iteration];
    }

    /**
     * Get current iteration count for a specific thread instance.
     *
     * @param token The token identifying the thread instance
     * @return The iteration count for this specific instance
     */
    public int getIterationCount(ThreadToken token) {
        AtomicInteger counter = iterationCounters.get(token.getUniqueId());
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Get total iteration count across all instances of a role (backwards compatibility).
     * This sums the iteration counts of all registered instances of the role.
     *
     * @param role The role to query
     * @return The sum of iteration counts for all instances of this role
     */
    public int getIterationCount(ThreadToken.Role role) {
        int total = 0;
        java.util.List<String> keys = roleToKeys.get(role);
        if (keys != null) {
            for (String key : keys) {
                AtomicInteger counter = iterationCounters.get(key);
                if (counter != null) {
                    total += counter.get();
                }
            }
        }
        return total;
    }
}
