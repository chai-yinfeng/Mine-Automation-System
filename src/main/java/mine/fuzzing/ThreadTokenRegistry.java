package mine.fuzzing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that manages token assignments for thread instances.
 * Tokens are assigned at thread construction/startup and remain immutable.
 * Thread-safe to allow concurrent registration during simulation setup.
 */
public class ThreadTokenRegistry {
    
    private final Map<Thread, ThreadToken> threadToToken = new ConcurrentHashMap<>();
    private final Map<ThreadToken, Thread> tokenToThread = new ConcurrentHashMap<>();
    
    /**
     * Register a thread with its assigned token.
     * Should be called during thread construction or before thread start.
     * 
     * @param thread The thread instance
     * @param token The immutable token for this thread
     */
    public void register(Thread thread, ThreadToken token) {
        threadToToken.put(thread, token);
        tokenToThread.put(token, thread);
    }
    
    /**
     * Get the token for a given thread.
     * 
     * @param thread The thread to look up
     * @return The token, or null if not registered
     */
    public ThreadToken getToken(Thread thread) {
        return threadToToken.get(thread);
    }
    
    /**
     * Get the thread for a given token.
     * 
     * @param token The token to look up
     * @return The thread, or null if not registered
     */
    public Thread getThread(ThreadToken token) {
        return tokenToThread.get(token);
    }
    
    /**
     * Get the token for the current thread.
     * 
     * @return The token, or null if current thread is not registered
     */
    public ThreadToken getCurrentThreadToken() {
        return threadToToken.get(Thread.currentThread());
    }
    
    /**
     * Clear all registrations. Useful for cleanup between test runs.
     */
    public void clear() {
        threadToToken.clear();
        tokenToThread.clear();
    }
    
    /**
     * Get the number of registered threads.
     */
    public int size() {
        return threadToToken.size();
    }
    
    /**
     * Get all registered tokens.
     * Returns a defensive copy to prevent external modification.
     * 
     * @return A collection of all registered tokens
     */
    public java.util.Collection<ThreadToken> getAllTokens() {
        return new java.util.ArrayList<>(tokenToThread.keySet());
    }
}
