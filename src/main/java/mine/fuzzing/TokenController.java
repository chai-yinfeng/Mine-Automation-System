package mine.fuzzing;

/**
 * Interface for controlling thread behavior based on tokens.
 * Implementations can inject fuzz-driven delays or other behaviors
 * at designated hook points in thread loops.
 */
public interface TokenController {
    
    /**
     * Hook point called at the start of a thread's main loop iteration.
     * Allows fuzzing to inject delays or control thread scheduling.
     * 
     * @param token The token of the calling thread
     */
    void onLoopIteration(ThreadToken token);
    
    /**
     * Hook point called before a critical operation (e.g., acquiring a lock).
     * 
     * @param token The token of the calling thread
     * @param operation Description of the operation about to occur
     */
    void beforeOperation(ThreadToken token, String operation);
    
    /**
     * Hook point called after a critical operation completes.
     * 
     * @param token The token of the calling thread
     * @param operation Description of the operation that completed
     */
    void afterOperation(ThreadToken token, String operation);
}
