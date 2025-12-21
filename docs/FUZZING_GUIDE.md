# Fuzzing Guide

This guide explains how to perform fuzz testing on the Mine Automation System using Jazzer, including the advanced token-controlled thread fuzzing extension.

## Quick Start

### Maven Plugin (Recommended)

```bash
# Run all tests (including @Test and @FuzzTest)
mvn test

# --- Regression mode ---
unset JAZZER_FUZZ
mvn -Dtest=mine.fuzzing.MineSystemFuzz test

# --- Fuzzing mode ---
export JAZZER_FUZZ=1
mvn -Dtest=mine.fuzzing.MineSystemFuzz test
```

### Jazzer CLI (Advanced)

Requires [Jazzer CLI](https://github.com/CodeIntelligenceTesting/jazzer) installed:

```bash
# Compile project
mvn -q -DskipTests test-compile

# Build classpath
CP="target/test-classes:target/classes:$(mvn -q -DincludeScope=test dependency:build-classpath -Dmdep.path)"

# Run fuzzing
jazzer \
  --cp="$CP" \
  --target_class=mine.fuzzing.MineFuzzTarget \
  --instrumentation_includes='mine.**' \
  --reproducer_path=target/jazzer-repros \
  test-artifacts/corpus \
  -- \
  -max_len=4096 \
  -timeout=50 \
  -max_total_time=120;
```

## Corpus Management and Configuration

### Understanding Data Consumption in Token-Based Fuzzing

The token-controlled thread fuzzing approach in this project has **unique data consumption characteristics** that differ from typical fuzz targets. Understanding these characteristics is critical for effective fuzzing.

#### The Data Consumption Challenge

**Problem**: Token-based fuzzing consumes significantly more corpus data than traditional fuzzers.

**Why?**
1. **Per-iteration control**: Each thread loop iteration requires fuzzer input to decide which thread to advance
2. **Fine-grained decisions**: Every decision point consumes 4 bytes (int) from the corpus:
   - Which thread role to release? (4 bytes)
   - Which instance of that role? (4 bytes)  
   - How many iterations to release? (4 bytes)
   - Delay duration? (8 bytes for long)

3. **Controller initialization**: The `FuzzingTokenController` pre-generates delay sequences:
   ```java
   int sequenceLength = data.consumeInt(10, 50);  // 4 bytes
   for (int i = 0; i < sequenceLength; i++) {
       delays[i] = data.consumeLong(0, 100);      // 8 bytes per delay
   }
   // For 6 roles × 4 instances × 50 delays × 8 bytes = 9,600 bytes just for initialization!
   ```

4. **Gated mode**: In gated iteration mode, each step of execution consumes input:
   ```java
   while (data.remainingBytes() > 1) {
       int tokenIdx = data.consumeInt(0, allTokens.size() - 1);  // 4 bytes
       // ... grant token and execute ...
   }
   ```

**Result**: A typical run can consume **4,000-50,000 bytes** of corpus data, compared to 100-1,000 bytes for traditional fuzzers.

#### Configuration Parameters

To accommodate this high data consumption, the fuzzing configuration requires careful tuning:

##### 1. **Maximum Input Length: `-max_len=4096`**

```bash
jazzer \
  --cp="$CP" \
  --target_class=mine.fuzzing.MineFuzzTarget \
  -- \
  -max_len=4096
```

**Why 4096 bytes?**
- **Minimum viable**: Enough for controller initialization (~10,000 bytes) + some iteration control
- **Balanced**: Not so large that mutation is ineffective
- **Practical**: Most interesting behaviors emerge within this range

**What happens with different sizes?**
- **Too small** (<1,000 bytes): Fuzzer exits early due to insufficient data
  ```java
  if (data.remainingBytes() < 200) {
      return;  // Not enough data to initialize controller
  }
  ```
- **Too large** (>10,000 bytes): Diminishing returns, slower mutation convergence

##### 2. **Timeout: `-timeout=50`**

```bash
jazzer \
  -- \
  -timeout=50
```

**Why 50 seconds?**
- **Thread coordination**: Gated mode releases threads one at a time with delays
- **Deadlock detection**: `AsyncDeadlockWatcher` monitors for 15 seconds of no progress
- **Safe margin**: Allows fuzzer to complete execution + cleanup before timeout

**Timeout calculation**:
```
Maximum execution time = (# of tokens) × (release delay) + (overhead)
                      ≈ 12 tokens × 0.5s × iterations + 15s watcher timeout
                      ≈ 30-40 seconds typical
```

Setting timeout to 50s provides buffer for slower executions.

##### 3. **Corpus Directory: `test-artifacts/corpus`**

```bash
jazzer \
  test-artifacts/corpus \
  -- \
  ...
```

**Why a custom corpus?**

Unlike typical fuzzers that can start with an empty corpus, token-based fuzzing benefits from **seed inputs** with appropriate size and structure.

**Corpus structure**:
```
test-artifacts/
├── corpus/
│   ├── seed_5000_random     # 5,000 byte random seed
│   ├── seed_10000_random    # 10,000 byte random seed
│   └── interesting_*        # Discovered interesting inputs
├── crashes/                  # Crash-inducing inputs
└── timeouts/                 # Timeout-inducing inputs
```

**Seed generation strategy**:
- Start with large (5,000-50,000 bytes) random inputs
- Jazzer will mutate and minimize them
- Interesting inputs are automatically saved to corpus

### Debugging with SimpleFuzzerTest

During development, debugging fuzzer behavior can be challenging because:
- Jazzer CLI has overhead (JNI, instrumentation)
- Input bytes are opaque binary blobs
- Hard to reproduce specific scenarios

**Solution**: `SimpleFuzzerTest.java` - A standalone debugging utility

#### What is SimpleFuzzerTest?

Located at `src/test/java/mine/fuzzing/SimpleFuzzerTest.java`, this class provides:

1. **Mock FuzzedDataProvider**: Implements the Jazzer API without requiring Jazzer CLI
2. **Controllable input**: Generate deterministic test data with fixed seed
3. **Standalone execution**: Run via `main()` method for quick iteration
4. **Debug visibility**: Print exactly what the fuzzer is consuming

#### Implementation Overview

```java
public class SimpleFuzzerTest {
    // Mock implementation of FuzzedDataProvider
    static class SimpleMockProvider implements FuzzedDataProvider {
        private final byte[] data;
        private int pos = 0;
        
        public SimpleMockProvider(byte[] data) {
            this.data = data;
        }
        
        @Override
        public int consumeInt(int min, int max) {
            if (pos + 4 > data.length) return min;
            int val = ByteBuffer.wrap(data, pos, 4).getInt();
            pos += 4;
            long range = (long) max - min + 1;
            return (int) (min + (Math.abs((long) val) % range));
        }
        
        @Override
        public long consumeLong(long min, long max) {
            if (pos + 8 > data.length) return min;
            long val = ByteBuffer.wrap(data, pos, 8).getLong();
            pos += 8;
            long range = max - min + 1;
            return min + (Math.abs(val) % range);
        }
        
        @Override
        public int remainingBytes() {
            return Math.max(0, data.length - pos);
        }
        
        // ... other methods ...
    }
    
    public static void main(String[] args) {
        // Generate 50,000 bytes of test data (deterministic seed)
        byte[] testData = generateTestData(50000);
        
        SimpleMockProvider provider = new SimpleMockProvider(testData);
        
        // Run the fuzz target with mock data
        MineFuzzTarget.fuzzerTestOneInput(provider);
    }
    
    private static byte[] generateTestData(int size) {
        Random rand = new Random(42);  // Fixed seed for reproducibility
        byte[] data = new byte[size];
        rand.nextBytes(data);
        return data;
    }
}
```

#### Usage

**Quick debugging run**:
```bash
# Compile
mvn test-compile

# Run directly
java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath)" \
  mine.fuzzing.SimpleFuzzerTest
```

**Advantages over Jazzer CLI**:
- **Fast iteration**: No fuzzer startup overhead
- **Reproducible**: Same input every time (fixed random seed)
- **Debuggable**: Can attach debugger, add breakpoints
- **Customizable**: Easy to modify input size/pattern in code

**When to use**:
- ✅ Testing fuzzer logic changes
- ✅ Debugging data consumption issues
- ✅ Validating token controller behavior
- ✅ Reproducing specific scenarios

**When NOT to use**:
- ❌ Finding new bugs (use actual Jazzer for that)
- ❌ Coverage-guided exploration (no feedback loop)
- ❌ Corpus minimization (no mutation)

### Corpus Generation Strategy

Given the high data consumption, corpus generation requires strategy:

#### Method 1: Manual Seed Creation

```bash
# Generate 5KB random seed
dd if=/dev/urandom of=test-artifacts/corpus/seed_5000 bs=1 count=5000

# Generate 10KB random seed
dd if=/dev/urandom of=test-artifacts/corpus/seed_10000 bs=1 count=10000

# Generate 50KB random seed (for extended fuzzing)
dd if=/dev/urandom of=test-artifacts/corpus/seed_50000 bs=1 count=50000
```

#### Method 2: Using SimpleFuzzerTest Output

The SimpleFuzzerTest can be modified to output successful inputs:

```java
public static void main(String[] args) {
    byte[] testData = generateTestData(50000);
    SimpleMockProvider provider = new SimpleMockProvider(testData);
    
    try {
        MineFuzzTarget.fuzzerTestOneInput(provider);
        
        // If successful, save as corpus seed
        Files.write(Paths.get("test-artifacts/corpus/seed_working"), testData);
        System.out.println("✓ Saved working seed to corpus");
    } catch (Exception e) {
        System.err.println("✗ Execution failed");
    }
}
```

#### Method 3: Jazzer Auto-Generation

Start fuzzing with minimal corpus, Jazzer will automatically:
1. Mutate initial seeds
2. Save interesting inputs that discover new coverage
3. Minimize corpus to essential inputs

```bash
# Start with one seed
echo "Random initial seed" | dd of=test-artifacts/corpus/seed_initial bs=1 count=4096

# Jazzer will grow the corpus automatically
export JAZZER_FUZZ=1
mvn -Dtest=mine.fuzzing.MineSystemFuzz test
```

### Best Practices

1. **Start small, scale up**:
   - Begin with 1,000-byte seeds for quick iteration
   - Increase to 5,000-10,000 bytes for thorough testing
   - Use 50,000+ bytes for extended stress testing

2. **Monitor consumption**:
   ```java
   int initialBytes = data.remainingBytes();
   System.err.println("Input size: " + initialBytes + " bytes");
   // ... fuzzing ...
   System.err.println("Consumed: " + (initialBytes - data.remainingBytes()) + " bytes");
   ```

3. **Tune timeouts based on corpus size**:
   - Small corpus (1-5KB): `-timeout=30`
   - Medium corpus (5-10KB): `-timeout=50`
   - Large corpus (10KB+): `-timeout=60`

4. **Use SimpleFuzzerTest for debugging**:
   - Test logic changes quickly
   - Validate data consumption patterns
   - Debug specific scenarios

5. **Leverage Jazzer's corpus minimization**:
   ```bash
   # Periodically minimize corpus
   jazzer \
     --cp="$CP" \
     --target_class=mine.fuzzing.MineFuzzTarget \
     -merge=1 \
     test-artifacts/corpus_minimized \
     test-artifacts/corpus
   ```

## Token-Controlled Thread Fuzzing Extension

### Overview

The token-controlled fuzzing extension enables fuzz-driven exploration of thread scheduling and interleaving without modifying the normal simulation behavior. This novel approach addresses a fundamental challenge in concurrent system testing: **how to systematically explore different thread interleavings while maintaining reproducibility**.

Traditional fuzzing approaches struggle with concurrent systems because thread scheduling is non-deterministic. This extension solves that problem by:

1. **Assigning immutable tokens** to each thread instance at construction time
2. **Providing fine-grained control hooks** at loop iteration boundaries
3. **Enabling deterministic replay** of specific thread interleavings
4. **Maintaining zero overhead** when fuzzing is disabled

### Architecture

The token system consists of four core components that work together to enable controlled thread scheduling exploration:

#### 1. ThreadToken (`mine.fuzzing.ThreadToken`)

**Purpose**: Immutable metadata that uniquely identifies each thread instance in the simulation.

**Key Features**:
- **Role-based identification**: Each token has a `Role` enum (PRODUCER, CONSUMER, OPERATOR, MINER, ENGINE, CART) that categorizes the thread's function
- **Instance differentiation**: An `instanceId` distinguishes multiple threads of the same role (e.g., miner #0, miner #1, miner #2)
- **Unique identifier**: Combines role and instance ID into a string like "MINER_2" for easy tracking

**Implementation Details** (from `ThreadToken.java`):
```java
public final class ThreadToken {
    public enum Role {
        PRODUCER, CONSUMER, OPERATOR, MINER, ENGINE, CART
    }
    
    private final Role role;           // Thread's functional role
    private final int instanceId;      // Instance number for this role
    private final String uniqueId;     // "ROLE_INSTANCE" format
    
    public ThreadToken(Role role, int instanceId) {
        this.role = role;
        this.instanceId = instanceId;
        this.uniqueId = role.name() + "_" + instanceId;
    }
}
```

**Example**: A simulation with 3 miners creates tokens:
- `ThreadToken(Role.MINER, 0)` → "MINER_0"
- `ThreadToken(Role.MINER, 1)` → "MINER_1"  
- `ThreadToken(Role.MINER, 2)` → "MINER_2"

This allows the fuzzer to target specific miners individually or all miners collectively.

#### 2. ThreadTokenRegistry (`mine.fuzzing.ThreadTokenRegistry`)

**Purpose**: Thread-safe registry managing bidirectional mappings between threads and tokens.

**Key Features**:
- **Concurrent registration**: Uses `ConcurrentHashMap` for thread-safe operations during simulation setup
- **Bidirectional lookup**: Can find token from thread, or thread from token
- **Current thread lookup**: `getCurrentThreadToken()` allows a thread to discover its own token
- **Clean state management**: `clear()` ensures no state leakage between test runs

**Implementation Details** (from `ThreadTokenRegistry.java`):
```java
public class ThreadTokenRegistry {
    private final Map<Thread, ThreadToken> threadToToken = new ConcurrentHashMap<>();
    private final Map<ThreadToken, Thread> tokenToThread = new ConcurrentHashMap<>();
    
    public void register(Thread thread, ThreadToken token) {
        threadToToken.put(thread, token);
        tokenToThread.put(token, thread);
    }
    
    public ThreadToken getCurrentThreadToken() {
        return threadToToken.get(Thread.currentThread());
    }
    
    public Thread getThread(ThreadToken token) {
        return tokenToThread.get(token);
    }
}
```

**Registration Process** (from `MineSimulation.java`):
```java
public void registerThreadTokens(ThreadTokenRegistry registry) {
    // Register single-instance threads
    registry.register(producer, new ThreadToken(ThreadToken.Role.PRODUCER, 0));
    registry.register(consumer, new ThreadToken(ThreadToken.Role.CONSUMER, 0));
    registry.register(operator, new ThreadToken(ThreadToken.Role.OPERATOR, 0));

    // Register multiple miner instances
    for (int i = 0; i < miners.length; i++) {
        registry.register(miners[i], new ThreadToken(ThreadToken.Role.MINER, i));
    }

    // Register engine instances
    int engineId = 0;
    for (Engine e : engines) {
        registry.register(e, new ThreadToken(ThreadToken.Role.ENGINE, engineId++));
    }
}
```

#### 3. TokenController Interface (`mine.fuzzing.TokenController`)

**Purpose**: Defines hook points where thread behavior can be controlled or monitored.

**Hook Points**:
- **`onLoopIteration(ThreadToken token)`**: Called at the start of each thread's main loop iteration
  - Primary control point for fuzzing
  - Allows injecting delays or blocking thread progression
  - Receives the calling thread's token for targeted control

- **`beforeOperation(ThreadToken token, String operation)`**: Called before critical operations
  - Future extension point for fine-grained control
  - Could control lock acquisition, queue operations, etc.

- **`afterOperation(ThreadToken token, String operation)`**: Called after critical operations
  - Future extension point for monitoring
  - Could verify invariants, collect coverage data, etc.

**Implementation Details** (from `TokenController.java`):
```java
public interface TokenController {
    void onLoopIteration(ThreadToken token);
    void beforeOperation(ThreadToken token, String operation);
    void afterOperation(ThreadToken token, String operation);
}
```

**Two Implementations**:

1. **NoOpTokenController** (default) - Zero overhead for normal simulation:
```java
public class NoOpTokenController implements TokenController {
    @Override
    public void onLoopIteration(ThreadToken token) {
        // No-op: threads run normally without any control or overhead
    }
    // ... other methods also no-op
}
```

2. **FuzzingTokenController** (fuzzing mode) - Detailed below in "Fuzzing Controller Implementation" section.

#### 4. TokenControllerProvider (`mine.fuzzing.TokenControllerProvider`)

**Purpose**: Global singleton accessor for registry and controller, enabling thread classes to access fuzzing infrastructure without constructor changes.

**Key Features**:
- **Default no-op behavior**: Starts with `NoOpTokenController` to ensure zero impact on normal simulation
- **Dependency injection point**: Fuzzing harnesses inject their controllers here
- **Clean state management**: `reset()` ensures each test starts fresh

**Implementation Details** (from `TokenControllerProvider.java`):
```java
public class TokenControllerProvider {
    private static ThreadTokenRegistry registry = new ThreadTokenRegistry();
    private static TokenController controller = new NoOpTokenController();
    
    public static ThreadTokenRegistry getRegistry() {
        return registry;
    }
    
    public static TokenController getController() {
        return controller;
    }
    
    public static void setController(TokenController newController) {
        controller = newController != null ? newController : new NoOpTokenController();
    }
    
    public static void setRegistry(ThreadTokenRegistry newRegistry) {
        registry = newRegistry != null ? newRegistry : new ThreadTokenRegistry();
    }
    
    public static void reset() {
        controller = new NoOpTokenController();
        registry = new ThreadTokenRegistry();
    }
}
```

**Design Rationale**: Using a static provider allows thread classes to remain unchanged structurally. Each thread just needs one hook line:

```java
// In Miner.java, Engine.java, Operator.java, etc.
while (!this.isInterrupted()) {
    // [FUZZING-HOOK] Token-based control
    mine.fuzzing.ThreadToken token = mine.fuzzing.TokenControllerProvider.getRegistry().getCurrentThreadToken();
    mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(token);
    
    // ... rest of thread's work ...
}
```

### Thread Integration

The hook is placed at the **start of each thread's main loop**, before any simulation work occurs. This placement is critical because:

1. **Fine-grained control**: Controls each loop iteration independently, not just thread startup
2. **Consistent interception**: Guarantees the fuzzer can control every iteration
3. **Clean boundaries**: Loop iteration is a natural unit of thread progress
4. **Minimal intrusion**: Single line per thread class, no structural changes

**Example from `Miner.java`**:
```java
public class Miner extends Thread {
    protected Station station;

    public Miner(Station station) {
        this.station = station;
    }

    public void run() {
        while (!this.isInterrupted()) {
            try {
                // [FUZZING-HOOK] Token-based control
                mine.fuzzing.ThreadToken token = mine.fuzzing.TokenControllerProvider.getRegistry().getCurrentThreadToken();
                mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(token);
                
                // Optional: logging for debugging
                if (token != null) {
                    MineLogger.log("MINER", "iteration start [" + token.getUniqueId() + "]");
                }
                
                // Normal miner work
                sleep(Params.MINING_TIME);
                this.station.depositGem();
                sleep(Params.minerPause());
            }
            catch (InterruptedException e) {
                this.interrupt();
            }
        }
    }
}
```

**Similar hooks exist in**:
- `Engine.java` - Ore transport threads
- `Operator.java` - Ore processing thread
- `Producer.java` - Producer coordinator
- `Consumer.java` - Consumer coordinator

### Fuzzing Controller Implementation

The `FuzzingTokenController` is the sophisticated component that actually implements fuzz-driven thread control. It supports two distinct operation modes.

#### Mode 1: Free-Running Mode (Default)

**Purpose**: Inject fuzz-driven delays while letting threads run autonomously.

**Use Case**: Explore timing-related race conditions by varying thread execution speeds.

**Behavior**:
- Each thread calls `onLoopIteration()` at loop start
- Controller injects a delay based on fuzz input
- Thread continues after delay without blocking
- No explicit fuzzer control needed after startup

**Implementation Details** (from `FuzzingTokenController.java`):
```java
public FuzzingTokenController(FuzzedDataProvider data, 
                              ThreadTokenRegistry tokenRegistry, 
                              boolean useGating) {
    this.useGating = useGating;
    this.defaultDelay = data.remainingBytes() > 4 ? data.consumeLong(0, 50) : 0;
    this.maxIterationsPerThread = Integer.MAX_VALUE; // Continuous operation
    
    // Generate per-instance delay sequences from fuzz input
    int sequenceLength = data.remainingBytes() > 8 ? data.consumeInt(10, 50) : 10;
    
    for (ThreadToken.Role role : ThreadToken.Role.values()) {
        long[] delays = new long[sequenceLength];
        for (int i = 0; i < sequenceLength && data.remainingBytes() > 4; i++) {
            delays[i] = data.consumeLong(0, 100); // 0-100ms delays
        }
        
        // For each registered instance of this role
        for (int instanceId = 0; instanceId < MAX_INSTANCE_ID; instanceId++) {
            ThreadToken token = new ThreadToken(role, instanceId);
            Thread thread = tokenRegistry.getThread(token);
            
            if (thread != null) {
                String uniqueKey = token.getUniqueId();
                delaySequences.put(uniqueKey, delays.clone());
                iterationCounters.put(uniqueKey, new AtomicInteger(0));
            }
        }
    }
}

@Override
public void onLoopIteration(ThreadToken token) {
    if (token == null) return;
    
    String uniqueKey = token.getUniqueId();
    
    // Track iteration count per instance
    AtomicInteger counter = iterationCounters.get(uniqueKey);
    int currentIteration = counter != null ? counter.getAndIncrement() : -1;
    
    // Apply fuzz-driven delay for this specific instance and iteration
    long delay = getDelayForIteration(uniqueKey, currentIteration);
    if (delay > 0) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

**Example Usage**:
```java
// Fuzzing harness initialization
ThreadTokenRegistry registry = new ThreadTokenRegistry();
sim.registerThreadTokens(registry);
TokenControllerProvider.setRegistry(registry);

// Enable free-running mode (useGating = false)
FuzzingTokenController controller = new FuzzingTokenController(data, registry, false);
TokenControllerProvider.setController(controller);

// Start simulation - threads run with varied delays
sim.startAll();
```

**Delay Sequence Logic**:
```java
private long getDelayForIteration(String roleKey, int iteration) {
    long[] delays = delaySequences.get(roleKey);
    if (delays == null || delays.length == 0 || iteration < 0) {
        return defaultDelay;
    }
    
    // Use delay sequence, repeat last delay when sequence exhausted
    if (iteration >= delays.length) {
        return delays[delays.length - 1];
    }
    
    return delays[iteration];
}
```

**Key Insight**: Each thread instance has its own delay sequence, so "MINER_0" can have completely different timing than "MINER_1", allowing the fuzzer to explore asymmetric interleavings.

#### Mode 2: Gated Iteration Mode (Controlled Interleaving)

**Purpose**: Explicitly control the exact order and timing of thread iterations.

**Use Case**: 
- Reproduce specific race conditions
- Test deadlock scenarios
- Verify fixes for known interleaving bugs
- Systematically explore state space

**Behavior**:
- Each thread calls `onLoopIteration()` and **blocks on a semaphore**
- Thread waits indefinitely until fuzzer grants it a "token" (permit)
- Fuzzer explicitly releases iterations: `controller.releaseIterations(role, count)`
- Enables deterministic replay of specific interleavings

**Implementation Details** (from `FuzzingTokenController.java`):
```java
// Initialization with gating enabled
public FuzzingTokenController(FuzzedDataProvider data, 
                              ThreadTokenRegistry tokenRegistry, 
                              boolean useGating) {
    this.useGating = true; // Enable gating
    
    // ... delay sequence setup ...
    
    // Initialize gating semaphores for each instance
    if (useGating) {
        for (int instanceId = 0; instanceId < MAX_INSTANCE_ID; instanceId++) {
            ThreadToken token = new ThreadToken(role, instanceId);
            Thread thread = tokenRegistry.getThread(token);
            
            if (thread != null) {
                String uniqueKey = token.getUniqueId();
                // Start with 0 permits - threads must wait
                iterationGates.put(uniqueKey, new Semaphore(0));
            }
        }
    }
}

@Override
public void onLoopIteration(ThreadToken token) {
    if (token == null) return;
    
    String uniqueKey = token.getUniqueId();
    AtomicInteger counter = iterationCounters.get(uniqueKey);
    int currentIteration = counter != null ? counter.getAndIncrement() : -1;
    
    // CRITICAL: If gating is enabled, block here until released
    if (useGating) {
        Semaphore gate = iterationGates.get(uniqueKey);
        if (gate != null) {
            try {
                // Block indefinitely waiting for permission
                gate.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
    
    // After gate opens, apply delay (if any)
    long delay = getDelayForIteration(uniqueKey, currentIteration);
    if (delay > 0) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

**Release Methods**:
```java
// Release specific thread instance
public void releaseIteration(ThreadToken token) {
    if (useGating) {
        Semaphore gate = iterationGates.get(token.getUniqueId());
        if (gate != null) {
            System.out.println("Granting token to: " + token.getUniqueId());
            gate.release(); // Grant 1 permit
        }
    }
}

// Release multiple iterations for an instance
public void releaseIterations(ThreadToken token, int count) {
    if (useGating) {
        Semaphore gate = iterationGates.get(token.getUniqueId());
        if (gate != null) {
            gate.release(count); // Grant multiple permits
        }
    }
}

// Release all instances of a role (convenience method)
public void releaseIterations(ThreadToken.Role role, int count) {
    if (useGating) {
        java.util.List<String> keys = roleToKeys.get(role);
        if (keys != null) {
            for (String key : keys) {
                Semaphore gate = iterationGates.get(key);
                if (gate != null) {
                    gate.release(count);
                }
            }
        }
    }
}

// Release all gates when done controlling
public void releaseAllGates() {
    if (useGating) {
        System.out.println("Releasing all gates - threads will now run freely");
        for (Semaphore gate : iterationGates.values()) {
            gate.release(Integer.MAX_VALUE / 2); // Effectively unlimited permits
        }
    }
}
```

**Example Usage**:
```java
// Fuzzing harness initialization
ThreadTokenRegistry registry = new ThreadTokenRegistry();
sim.registerThreadTokens(registry);
TokenControllerProvider.setRegistry(registry);

// Enable gated mode (useGating = true)
FuzzingTokenController controller = new FuzzingTokenController(data, registry, true);
TokenControllerProvider.setController(controller);

// Start simulation - threads will block at first loop iteration
sim.startAll();

// Orchestrate specific interleaving
ThreadToken miner0 = new ThreadToken(ThreadToken.Role.MINER, 0);
ThreadToken miner1 = new ThreadToken(ThreadToken.Role.MINER, 1);
ThreadToken operator = new ThreadToken(ThreadToken.Role.OPERATOR, 0);

// Let miner 0 run 2 iterations
controller.releaseIterations(miner0, 2);
Thread.sleep(50); // Let iterations complete

// Let operator run 1 iteration
controller.releaseIterations(operator, 1);
Thread.sleep(50);

// Let miner 1 run 1 iteration
controller.releaseIterations(miner1, 1);
Thread.sleep(50);

// Continue orchestrating...

// When done, release all to let simulation complete
controller.releaseAllGates();
```

**Deterministic Replay**: By using a fixed sequence of releases, you can reproduce the exact same thread interleaving across multiple runs, making it possible to:
- Verify bug fixes
- Debug race conditions
- Create regression tests for specific scenarios

### Usage Patterns

#### Pattern 1: Basic Fuzzing Setup

```java
// Create simulation
MineSimulation sim = new MineSimulation();

// Register thread tokens
ThreadTokenRegistry registry = new ThreadTokenRegistry();
sim.registerThreadTokens(registry);
TokenControllerProvider.setRegistry(registry);

// Enable fuzzing controller (free-running mode)
FuzzingTokenController controller = new FuzzingTokenController(data, registry, false);
TokenControllerProvider.setController(controller);

// Start simulation
sim.startAll();

// Let it run
Thread.sleep(5000);

// Stop and check for issues
sim.stopAll();
```

#### Pattern 2: Gated Interleaving Exploration

```java
// Setup with gating enabled
FuzzingTokenController controller = new FuzzingTokenController(data, registry, true);
TokenControllerProvider.setController(controller);

sim.startAll(); // Threads start but block immediately

// Systematically explore state space
for (int step = 0; step < explorationSteps; step++) {
    // Choose which thread(s) to advance based on fuzz input
    int roleIndex = data.consumeInt(0, ThreadToken.Role.values().length - 1);
    ThreadToken.Role role = ThreadToken.Role.values()[roleIndex];
    
    int instanceId = data.consumeInt(0, 5); // Assuming max 5 instances per role
    ThreadToken token = new ThreadToken(role, instanceId);
    
    // Release one iteration for this specific thread
    if (registry.getThread(token) != null) {
        controller.releaseIterations(token, 1);
        Thread.sleep(10); // Let iteration execute
    }
}

// Release all and let simulation complete naturally
controller.releaseAllGates();
Thread.sleep(1000);
sim.stopAll();
```

#### Pattern 3: Targeted Race Condition Testing

```java
// Test specific scenario: "What if operator runs before any miners?"
FuzzingTokenController controller = new FuzzingTokenController(data, registry, true);
TokenControllerProvider.setController(controller);

sim.startAll();

// Force operator to run first
ThreadToken operator = new ThreadToken(ThreadToken.Role.OPERATOR, 0);
controller.releaseIterations(operator, 3); // Let operator do 3 iterations
Thread.sleep(100);

// Now let miners proceed
controller.releaseIterations(ThreadToken.Role.MINER, 5); // All miners get 5 iterations
Thread.sleep(100);

// Continue test...
controller.releaseAllGates();
```

### Benefits

1. **Fine-grained control**: Control individual loop iterations, not just thread starts
   - Traditional approaches can only control when threads start, not how they interleave
   - Token system controls each iteration independently

2. **Reproducible interleavings**: Deterministically explore specific thread execution orders
   - Same fuzz input + same release sequence = same interleaving
   - Critical for debugging and regression testing

3. **Deadlock detection**: Test controlled scenarios that trigger race conditions
   - Can force specific "dangerous" interleavings
   - Gated mode allows testing scenarios that would be rare/impossible naturally

4. **Role-based fuzzing**: Target specific thread types
   - Test "what if all miners are slow?" or "what if operator is fast?"
   - Asymmetric exploration of state space

5. **Zero overhead**: NoOpTokenController default ensures normal simulation is unaffected
   - Production code has no performance impact
   - Fuzzing overhead only when explicitly enabled

6. **Minimal code changes**: Single hook line per thread class
   - No structural modifications to thread classes
   - Easy to maintain and understand

7. **Instance-level control**: Each thread instance controlled independently
   - "MINER_0" can have different behavior than "MINER_1"
   - Enables exploring asymmetric scenarios

### Integration with Existing Fuzzing Infrastructure

The token extension **complements** rather than replaces existing fuzzing components:

#### PauseProvider
- **Purpose**: Controls timing within thread loops (e.g., how long miners sleep)
- **Interaction**: Independent from token system
- **Used in**: `Params.minerPause()`, `Params.enginePause()`, etc.
- **Token relationship**: Token delays happen *before* loop iteration; PauseProvider delays happen *during* iteration

#### MineSimulation
- **Extension**: Added `registerThreadTokens(ThreadTokenRegistry registry)` method
- **Responsibility**: Maps each thread instance to its token during setup
- **Token relationship**: Bridges thread instances and token system

#### DeadlockWatcher
- **Purpose**: Monitors for stuck threads and reports deadlocks
- **Compatibility**: Token delays are intentional; watcher distinguishes from real deadlocks
- **Token relationship**: Monitors work even when threads are gated

#### MineProgress
- **Purpose**: Tracks overall simulation progress (gems mined, ore moved, etc.)
- **Compatibility**: Continues working regardless of token control
- **Token relationship**: Validates that controlled threads still make progress

### Configuration and Cleanup

**No configuration flags needed**. The system automatically:

1. **Defaults to no-op**: `TokenControllerProvider` starts with `NoOpTokenController`
2. **Explicit activation**: Fuzzing harnesses explicitly inject their controllers
3. **Clean state**: `TokenControllerProvider.reset()` ensures no state leakage between tests

**Best Practice Cleanup Pattern**:
```java
@AfterEach
public void cleanup() {
    Params.resetPauseProvider();          // Reset pause provider
    TokenControllerProvider.reset();       // Reset token system
    MineProgress.reset();                  // Reset progress tracking
}
```

### Testing the Token Infrastructure

Unit tests verify correct behavior:

```bash
# Run all token-related tests
mvn -Dtest=ThreadTokenTest,ThreadTokenRegistryTest,TokenControllerTest test
```

**Test Coverage**:
- **ThreadTokenTest**: Token creation, equality, hashing
- **ThreadTokenRegistryTest**: Registration, bidirectional lookup, clear
- **TokenControllerTest**: NoOp and Fuzzing controller behaviors
- **TokenFuzzingIntegrationTest**: End-to-end integration with simulation

**Example Integration Test** (from `TokenFuzzingIntegrationTest.java`):
```java
@Test
public void testTokenRegistration() {
    MineSimulation sim = new MineSimulation();
    
    // Register tokens
    ThreadTokenRegistry registry = new ThreadTokenRegistry();
    sim.registerThreadTokens(registry);
    
    // Verify all threads are registered with correct roles
    assertTrue(registry.size() > 0);
    
    // Verify bidirectional lookup
    ThreadToken producerToken = new ThreadToken(ThreadToken.Role.PRODUCER, 0);
    Thread producer = registry.getThread(producerToken);
    assertNotNull(producer);
    
    ThreadToken lookupToken = registry.getToken(producer);
    assertEquals(producerToken, lookupToken);
}

@Test
public void testNoOpControllerHasNoImpact() throws InterruptedException {
    // Verify default controller doesn't affect simulation performance
    TokenController controller = TokenControllerProvider.getController();
    assertTrue(controller instanceof NoOpTokenController);
    
    MineSimulation sim = new MineSimulation();
    ThreadTokenRegistry registry = new ThreadTokenRegistry();
    sim.registerThreadTokens(registry);
    
    MineProgress.reset();
    sim.startAll();
    
    Thread.sleep(500);
    
    // Verify progress is being made normally
    long progress = MineProgress.snapshot();
    assertTrue(progress > 0, "Simulation should make progress with NoOpController");
    
    sim.stopAll();
}
```

### Future Extensions

The token framework enables future enhancements:

1. **Phase-based control**
   - Initialization phase (threads starting)
   - Steady-state phase (normal operation)
   - Shutdown phase (threads stopping)
   - Control behavior differently in each phase

2. **Fine-grained operation hooks**
   - `beforeOperation("lock_acquire")` - Control when locks are acquired
   - `beforeOperation("queue_operation")` - Control queue access ordering
   - `afterOperation("state_update")` - Verify invariants after state changes

3. **Custom token controllers**
   - **CoverageDrivenController**: Use code coverage to guide iteration releases
   - **StateSpaceController**: Systematically explore all reachable states
   - **AdversarialController**: Maximize chance of finding bugs

4. **Coverage-guided fuzzing integration**
   - Integrate with Jazzer's coverage feedback
   - Release iterations that maximize new coverage
   - Build corpus of interesting interleavings

5. **Happens-before graph construction**
   - Record which thread iterations happened before others
   - Visualize thread interleavings
   - Identify minimal reproducers for bugs

6. **Multi-mode fuzzing**
   - Start in gated mode to explore state space systematically
   - Switch to free-running mode once interesting state is reached
   - Hybrid exploration strategy

### Conclusion

The token-controlled thread fuzzing extension represents a novel approach to concurrent system testing that provides:

- **Systematic exploration** of thread interleavings
- **Reproducible testing** of race conditions
- **Zero overhead** when not fuzzing
- **Minimal code impact** (single line per thread)
- **Flexible control** (free-running or gated modes)
- **Instance-level precision** (control individual threads)

This makes it possible to find subtle concurrency bugs that would be extremely difficult to discover through traditional testing approaches, while maintaining the ability to reproduce and verify fixes once found.
