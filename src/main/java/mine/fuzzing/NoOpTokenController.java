package mine.fuzzing;

/**
 * Default no-op implementation of TokenController.
 * Used when fuzzing is disabled to ensure zero overhead
 * in normal simulation runs.
 */
public class NoOpTokenController implements TokenController {
    
    @Override
    public void onLoopIteration(ThreadToken token) {
        // No-op: normal simulation continues unaffected
    }
    
    @Override
    public void beforeOperation(ThreadToken token, String operation) {
        // No-op: normal simulation continues unaffected
    }
    
    @Override
    public void afterOperation(ThreadToken token, String operation) {
        // No-op: normal simulation continues unaffected
    }
}
