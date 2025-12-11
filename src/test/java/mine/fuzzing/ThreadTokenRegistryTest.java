package mine.fuzzing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThreadTokenRegistry.
 */
public class ThreadTokenRegistryTest {
    
    private ThreadTokenRegistry registry;
    
    @BeforeEach
    public void setUp() {
        registry = new ThreadTokenRegistry();
    }
    
    @Test
    public void testRegisterAndGetByThread() {
        Thread thread = new Thread();
        ThreadToken token = new ThreadToken(ThreadToken.Role.PRODUCER, 0);
        
        registry.register(thread, token);
        
        ThreadToken retrieved = registry.getToken(thread);
        assertEquals(token, retrieved);
    }
    
    @Test
    public void testRegisterAndGetByToken() {
        Thread thread = new Thread();
        ThreadToken token = new ThreadToken(ThreadToken.Role.MINER, 3);
        
        registry.register(thread, token);
        
        Thread retrieved = registry.getThread(token);
        assertEquals(thread, retrieved);
    }
    
    @Test
    public void testMultipleRegistrations() {
        Thread thread1 = new Thread();
        Thread thread2 = new Thread();
        ThreadToken token1 = new ThreadToken(ThreadToken.Role.ENGINE, 0);
        ThreadToken token2 = new ThreadToken(ThreadToken.Role.ENGINE, 1);
        
        registry.register(thread1, token1);
        registry.register(thread2, token2);
        
        assertEquals(2, registry.size());
        assertEquals(token1, registry.getToken(thread1));
        assertEquals(token2, registry.getToken(thread2));
        assertEquals(thread1, registry.getThread(token1));
        assertEquals(thread2, registry.getThread(token2));
    }
    
    @Test
    public void testUnregisteredThread() {
        Thread thread = new Thread();
        ThreadToken token = registry.getToken(thread);
        assertNull(token);
    }
    
    @Test
    public void testUnregisteredToken() {
        ThreadToken token = new ThreadToken(ThreadToken.Role.CONSUMER, 0);
        Thread thread = registry.getThread(token);
        assertNull(thread);
    }
    
    @Test
    public void testClear() {
        Thread thread = new Thread();
        ThreadToken token = new ThreadToken(ThreadToken.Role.OPERATOR, 0);
        
        registry.register(thread, token);
        assertEquals(1, registry.size());
        
        registry.clear();
        assertEquals(0, registry.size());
        assertNull(registry.getToken(thread));
        assertNull(registry.getThread(token));
    }
    
    @Test
    public void testCurrentThreadToken() {
        // Register current thread
        Thread current = Thread.currentThread();
        ThreadToken token = new ThreadToken(ThreadToken.Role.PRODUCER, 0);
        
        registry.register(current, token);
        
        ThreadToken retrieved = registry.getCurrentThreadToken();
        assertEquals(token, retrieved);
    }
    
    @Test
    public void testThreadSafety() throws InterruptedException {
        // Simple concurrency test
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                ThreadToken token = new ThreadToken(ThreadToken.Role.MINER, idx);
                registry.register(Thread.currentThread(), token);
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        assertEquals(10, registry.size());
    }
}
