package mine.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fuzzing-driven implementation of TokenController.
 * Consumes fuzz input to inject controlled delays or behaviors
 * at thread hook points, enabling exploration of different
 * thread interleavings and timing scenarios.
 */
public class FuzzingTokenController implements TokenController {
    
    private final Map<String, Long> delaySequence = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> sequenceIndices = new ConcurrentHashMap<>();
    private final long defaultDelay;
    
    /**
     * Create a fuzzing controller with precomputed delay sequences.
     * 
     * @param data Fuzz input provider
     * @param tokenRegistry Registry to know which tokens exist
     */
    public FuzzingTokenController(FuzzedDataProvider data, ThreadTokenRegistry tokenRegistry) {
        // Consume a default delay value for when sequences are exhausted
        this.defaultDelay = data.remainingBytes() > 4 ? data.consumeLong(0, 100) : 0;
        
        // Generate delay sequences for each token type
        // Limited to keep fuzz input compact
        if (data.remainingBytes() > 8) {
            int sequenceLength = data.consumeInt(1, 10);
            for (ThreadToken.Role role : ThreadToken.Role.values()) {
                if (data.remainingBytes() > 4) {
                    long delay = data.consumeLong(0, 50);
                    delaySequence.put(role.name(), delay);
                }
            }
        }
    }
    
    @Override
    public void onLoopIteration(ThreadToken token) {
        if (token == null) return;
        
        long delay = getDelayForToken(token);
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Override
    public void beforeOperation(ThreadToken token, String operation) {
        // For now, we primarily control via loop iteration
        // But this hook is available for more fine-grained control
    }
    
    @Override
    public void afterOperation(ThreadToken token, String operation) {
        // For now, we primarily control via loop iteration
        // But this hook is available for more fine-grained control
    }
    
    private long getDelayForToken(ThreadToken token) {
        String key = token.getRole().name();
        return delaySequence.getOrDefault(key, defaultDelay);
    }
}
