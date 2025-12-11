package mine.fuzzing;

/**
 * Global provider for accessing the token controller and registry.
 * Allows thread classes to access fuzzing hooks without constructor changes.
 * Defaults to no-op behavior when fuzzing is not active.
 */
public class TokenControllerProvider {
    
    private static ThreadTokenRegistry registry = new ThreadTokenRegistry();
    private static TokenController controller = new NoOpTokenController();
    
    /**
     * Get the current token registry.
     */
    public static ThreadTokenRegistry getRegistry() {
        return registry;
    }
    
    /**
     * Get the current token controller.
     */
    public static TokenController getController() {
        return controller;
    }
    
    /**
     * Set the token controller (typically for fuzzing).
     * 
     * @param newController The controller to use
     */
    public static void setController(TokenController newController) {
        controller = newController != null ? newController : new NoOpTokenController();
    }
    
    /**
     * Set the token registry (typically for fuzzing).
     * 
     * @param newRegistry The registry to use
     */
    public static void setRegistry(ThreadTokenRegistry newRegistry) {
        registry = newRegistry != null ? newRegistry : new ThreadTokenRegistry();
    }
    
    /**
     * Reset to default (no-op) behavior.
     * Call this between test runs to ensure clean state.
     */
    public static void reset() {
        controller = new NoOpTokenController();
        registry = new ThreadTokenRegistry();
    }
}
