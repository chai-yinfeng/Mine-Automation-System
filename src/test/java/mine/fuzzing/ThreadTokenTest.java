package mine.fuzzing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThreadToken immutable metadata class.
 */
public class ThreadTokenTest {
    
    @Test
    public void testTokenCreation() {
        ThreadToken token = new ThreadToken(ThreadToken.Role.PRODUCER, 0);
        assertEquals(ThreadToken.Role.PRODUCER, token.getRole());
        assertEquals(0, token.getInstanceId());
        assertEquals("PRODUCER_0", token.getUniqueId());
    }
    
    @Test
    public void testTokenEquality() {
        ThreadToken token1 = new ThreadToken(ThreadToken.Role.MINER, 1);
        ThreadToken token2 = new ThreadToken(ThreadToken.Role.MINER, 1);
        ThreadToken token3 = new ThreadToken(ThreadToken.Role.MINER, 2);
        ThreadToken token4 = new ThreadToken(ThreadToken.Role.ENGINE, 1);
        
        assertEquals(token1, token2);
        assertNotEquals(token1, token3);
        assertNotEquals(token1, token4);
        assertEquals(token1.hashCode(), token2.hashCode());
    }
    
    @Test
    public void testTokenToString() {
        ThreadToken token = new ThreadToken(ThreadToken.Role.ENGINE, 5);
        assertEquals("ENGINE_5", token.toString());
    }
    
    @Test
    public void testAllRoles() {
        // Ensure all expected roles exist
        ThreadToken.Role[] roles = ThreadToken.Role.values();
        assertTrue(roles.length >= 6);
        
        boolean hasProducer = false;
        boolean hasConsumer = false;
        boolean hasOperator = false;
        boolean hasMiner = false;
        boolean hasEngine = false;
        
        for (ThreadToken.Role role : roles) {
            if (role == ThreadToken.Role.PRODUCER) hasProducer = true;
            if (role == ThreadToken.Role.CONSUMER) hasConsumer = true;
            if (role == ThreadToken.Role.OPERATOR) hasOperator = true;
            if (role == ThreadToken.Role.MINER) hasMiner = true;
            if (role == ThreadToken.Role.ENGINE) hasEngine = true;
        }
        
        assertTrue(hasProducer);
        assertTrue(hasConsumer);
        assertTrue(hasOperator);
        assertTrue(hasMiner);
        assertTrue(hasEngine);
    }
}
