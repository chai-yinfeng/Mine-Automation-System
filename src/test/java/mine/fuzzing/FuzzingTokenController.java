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

    private final Map<String, long[]> delaySequences = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> iterationCounters = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> iterationGates = new ConcurrentHashMap<>();
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
        this.maxIterationsPerThread = data.remainingBytes() > 4 ? data.consumeInt(5, 20) : 10;
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
            
            // For each role, we need to find all registered instances and initialize them
            // We'll iterate through all possible instance IDs (a reasonable upper bound)
            for (int instanceId = 0; instanceId < 20; instanceId++) {
                ThreadToken token = new ThreadToken(role, instanceId);
                Thread thread = tokenRegistry.getThread(token);
                
                // Only initialize if this instance actually exists in the registry
                if (thread != null) {
                    String uniqueKey = token.getUniqueId();
                    delaySequences.put(uniqueKey, delays.clone());
                    iterationCounters.put(uniqueKey, new AtomicInteger(0));
                    
                    // Initialize gating semaphores (start with 0 permits - threads must wait)
                    if (useGating) {
                        iterationGates.put(uniqueKey, new Semaphore(0));
                    }
                }
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

            // Stop thread after max iterations to prevent infinite running
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
            }
        }

        // If gating timed out, interrupt to signal potential deadlock
        if (useGating && !acquiredPermission) {
            Thread.currentThread().interrupt();
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
            for (String key : iterationGates.keySet()) {
                if (key.startsWith(role.name() + "_")) {
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
            for (String key : iterationGates.keySet()) {
                if (key.startsWith(role.name() + "_")) {
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
        for (Map.Entry<String, AtomicInteger> entry : iterationCounters.entrySet()) {
            if (entry.getKey().startsWith(role.name() + "_")) {
                total += entry.getValue().get();
            }
        }
        return total;
    }
}
