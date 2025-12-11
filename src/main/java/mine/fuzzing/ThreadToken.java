package mine.fuzzing;

/**
 * Immutable token metadata assigned to thread instances at construction.
 * Tokens identify thread roles and enable fuzz-driven control without
 * affecting normal simulation behavior.
 */
public final class ThreadToken {
    
    public enum Role {
        PRODUCER,
        CONSUMER,
        OPERATOR,
        MINER,
        ENGINE,
        CART
    }
    
    private final Role role;
    private final int instanceId;
    private final String uniqueId;
    
    /**
     * Creates a new thread token.
     * 
     * @param role The type of thread (producer, consumer, etc.)
     * @param instanceId The instance number for this role (e.g., miner #0, miner #1)
     */
    public ThreadToken(Role role, int instanceId) {
        this.role = role;
        this.instanceId = instanceId;
        this.uniqueId = role.name() + "_" + instanceId;
    }
    
    public Role getRole() {
        return role;
    }
    
    public int getInstanceId() {
        return instanceId;
    }
    
    public String getUniqueId() {
        return uniqueId;
    }
    
    @Override
    public String toString() {
        return uniqueId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThreadToken that = (ThreadToken) o;
        return instanceId == that.instanceId && role == that.role;
    }
    
    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }
}
