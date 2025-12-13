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
        // Generate delay sequences for each role
        int sequenceLength = data.remainingBytes() > 8 ? data.consumeInt(10, 50) : 10;
        for (ThreadToken.Role role : ThreadToken.Role.values()) {
            long[] delays = new long[sequenceLength];
            for (int i = 0; i < sequenceLength && data.remainingBytes() > 4; i++) {
                delays[i] = data.consumeLong(0, 100);
            }
            delaySequences.put(role.name(), delays);
            iterationCounters.put(role.name(), new AtomicInteger(0));

            // Initialize gating semaphores (start with 0 permits - threads must wait)
            if (useGating) {
                iterationGates.put(role.name(), new Semaphore(0));
            }
        }
    }

    @Override
    public void onLoopIteration(ThreadToken token) {
        if (token == null) return;

        String roleKey = token.getRole().name();

        // Track iteration count and get current iteration number atomically
        AtomicInteger counter = iterationCounters.get(roleKey);
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
            Semaphore gate = iterationGates.get(roleKey);
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
        long delay = getDelayForIteration(roleKey, currentIteration);
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
     * Release a thread for one more loop iteration (when gating is enabled).
     *
     * @param role The role to release
     */
    public void releaseIteration(ThreadToken.Role role) {
        if (useGating) {
            Semaphore gate = iterationGates.get(role.name());
            if (gate != null) {
                gate.release();
            }
        }
    }

    /**
     * Release multiple iterations for a role.
     */
    public void releaseIterations(ThreadToken.Role role, int count) {
        if (useGating) {
            Semaphore gate = iterationGates.get(role.name());
            if (gate != null) {
                gate.release(count);
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
     * Get current iteration count for a role.
     */
    public int getIterationCount(ThreadToken.Role role) {
        AtomicInteger counter = iterationCounters.get(role.name());
        return counter != null ? counter.get() : 0;
    }
}
