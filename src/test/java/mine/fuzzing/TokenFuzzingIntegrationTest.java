package mine.fuzzing;

import mine.Params;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating token-based fuzzing capabilities.
 * Validates that the token infrastructure integrates correctly with
 * the simulation and doesn't affect normal operation.
 */
public class TokenFuzzingIntegrationTest {
    
    @AfterEach
    public void cleanup() {
        // Reset to default state after each test
        Params.resetPauseProvider();
        TokenControllerProvider.reset();
    }
    
    @Test
    public void testTokenRegistration() {
        // Create simulation
        MineSimulation sim = new MineSimulation();
        
        // Register tokens
        ThreadTokenRegistry registry = new ThreadTokenRegistry();
        sim.registerThreadTokens(registry);
        
        // Verify all threads are registered with correct roles
        assertTrue(registry.size() > 0);
        
        // Verify we can look up threads by token
        ThreadToken producerToken = new ThreadToken(ThreadToken.Role.PRODUCER, 0);
        Thread producer = registry.getThread(producerToken);
        assertNotNull(producer);
        
        ThreadToken consumerToken = new ThreadToken(ThreadToken.Role.CONSUMER, 0);
        Thread consumer = registry.getThread(consumerToken);
        assertNotNull(consumer);
        
        ThreadToken operatorToken = new ThreadToken(ThreadToken.Role.OPERATOR, 0);
        Thread operator = registry.getThread(operatorToken);
        assertNotNull(operator);
        
        // Verify miners are registered (at least STATIONS miners)
        ThreadToken miner0Token = new ThreadToken(ThreadToken.Role.MINER, 0);
        Thread miner0 = registry.getThread(miner0Token);
        assertNotNull(miner0);
    }
    
    @Test
    public void testTokenBasedThreadSelection() throws InterruptedException {
        // Create simulation
        MineSimulation sim = new MineSimulation();
        
        // Register tokens
        ThreadTokenRegistry registry = new ThreadTokenRegistry();
        sim.registerThreadTokens(registry);
        
        // Select and start threads by token
        ThreadToken producerToken = new ThreadToken(ThreadToken.Role.PRODUCER, 0);
        Thread producer = registry.getThread(producerToken);
        
        Thread[] allThreads = sim.getAllThreads();
        for (int i = 0; i < allThreads.length; i++) {
            if (allThreads[i] == producer) {
                sim.startThread(i);
                break;
            }
        }
        
        // Verify thread was started
        assertTrue(producer.isAlive());
        
        // Clean up
        Thread.sleep(100); // Let it run briefly
        sim.stopAll();
    }
    
    @Test
    public void testNoOpControllerHasNoImpact() throws InterruptedException {
        // Verify that default no-op controller doesn't affect simulation
        TokenController controller = TokenControllerProvider.getController();
        assertTrue(controller instanceof NoOpTokenController);
        
        // Create and run simulation briefly
        MineSimulation sim = new MineSimulation();
        ThreadTokenRegistry registry = new ThreadTokenRegistry();
        sim.registerThreadTokens(registry);
        
        MineProgress.reset();
        sim.startAll();
        
        // Let simulation run briefly
        Thread.sleep(500);
        
        // Verify progress is being made (simulation is running)
        long progress = MineProgress.snapshot();
        assertTrue(progress > 0, "Simulation should make progress");
        
        // Clean up
        sim.stopAll();
    }
    
    @Test
    public void testCompatibilityWithDeadlockWatcher() throws InterruptedException {
        // Verify token infrastructure is compatible with deadlock detection
        MineSimulation sim = new MineSimulation();
        ThreadTokenRegistry registry = new ThreadTokenRegistry();
        sim.registerThreadTokens(registry);
        
        MineProgress.reset();
        sim.startAll();
        
        // Run deadlock watcher for a short time
        DeadlockWatcher watcher = new DeadlockWatcher(1000);
        
        // Should not throw any assertion errors
        assertDoesNotThrow(() -> watcher.watch());
        
        // Clean up
        sim.stopAll();
    }
    
    @Test
    public void testRoleBasedTokenLookup() {
        MineSimulation sim = new MineSimulation();
        ThreadTokenRegistry registry = new ThreadTokenRegistry();
        sim.registerThreadTokens(registry);
        
        // Verify we can distinguish between different instances of same role
        ThreadToken engine0 = new ThreadToken(ThreadToken.Role.ENGINE, 0);
        ThreadToken engine1 = new ThreadToken(ThreadToken.Role.ENGINE, 1);
        
        Thread t0 = registry.getThread(engine0);
        Thread t1 = registry.getThread(engine1);
        
        // Should get different threads
        assertNotNull(t0);
        assertNotNull(t1);
        assertNotEquals(t0, t1);
    }
}
