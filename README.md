

## Project Structure

```
Mine-Automation-System/
│
├─ src/
│   ├─ main/java/mine/        # Application source code
│   └─ test/java/mine/        # Verification harnesses (JBMC + Jazzer)
│
├─ pom.xml                    # Maven build configuration
└─ README.md                  # This document
```
The source tree follows a standard Maven Java layout, allowing tools like JBMC, Jazzer, and JUnit to operate correctly.

## How to Build and Run

```bash
# Compile
mvn -q -DskipTests package

# Run simulation
mvn exec:java

# Run verification
export PATH=~/cbmc-git/jbmc/src/jbmc:$PATH

mvn -q -DskipTests test-compile

CP="target/test-classes:target/classes"

jbmc mine.formal.StationJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace

jbmc mine.formal.CartJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace

jbmc mine.formal.ElevatorJBMCVerification \
  --classpath "$CP" \
  --unwind 4 \
  --no-unwinding-assertions \
  --trace


# Run fuzzer (bin)
JAZZER=~/jazzer-bin/jazzer

$JAZZER --cp="target/classes:target/test-classes" \
        --target_class=mine.fuzzing.MineSystemFuzz \
        --uses_fuzzed_data_provider=1

# Run fuzzer (JUnit)
# --- Regression mode ---
# Run all tests（including @Test and @FuzzTest）
mvn test

# Only run fuzz class
#unset JAZZER_FUZZ

mvn -Dtest=mine.fuzzing.MineSystemFuzz test

# --- Fuzzing mode ---
export JAZZER_FUZZ=1

# Run JUnit as usual
mvn -Dtest=mine.fuzzing.MineSystemFuzz test

```
---

command to run jazzer for the `MineFuzzTarget.java`

need `jazzer cli` installed explicitly!

```bash
# compile the whole project
mvn -q -DskipTests test-compile

# let maven to generate the classpath.
# `test-classes` must in prior of `classes`, incase for implecit override. 
CP="target/test-classes:target/classes:$(mvn -q -DincludeScope=test dependency:build-classpath -Dmdep.path)"

# Fuzz the mine.fuzzing.MineFuzzTarget by Fuzzer CLI
jazzer \
  --cp="$CP" \
  --target_class=mine.fuzzing.MineFuzzTarget \
  --instrumentation_includes='mine.**' \
  --reproducer_path=target/jazzer-repros \
  -max_total_time=120
```

---

## Token-Controlled Thread Fuzzing Extension

### Overview

The token-controlled fuzzing extension enables fuzz-driven exploration of thread scheduling and interleaving without modifying the normal simulation behavior. This extension assigns immutable tokens to thread instances at construction time, allowing fuzzing harnesses to control thread behavior based on roles and instances.

### Architecture

#### Core Components

1. **ThreadToken** (`mine.fuzzing.ThreadToken`)
   - Immutable metadata assigned to each thread instance
   - Identifies thread role (PRODUCER, CONSUMER, OPERATOR, MINER, ENGINE, CART)
   - Contains instance ID for distinguishing multiple threads of the same role
   - Example: `ThreadToken(Role.MINER, 2)` represents the third miner thread

2. **ThreadTokenRegistry** (`mine.fuzzing.ThreadTokenRegistry`)
   - Thread-safe registry managing token-to-thread mappings
   - Bidirectional lookup: thread → token, token → thread
   - Initialized during simulation setup
   - Cleared between test runs for clean state

3. **TokenController** Interface (`mine.fuzzing.TokenController`)
   - Defines hook points for fuzz-driven control
   - `onLoopIteration()` - called at start of thread loop
   - `beforeOperation()` / `afterOperation()` - called around critical operations
   - Implementations:
     - **NoOpTokenController** - Default (zero overhead, normal simulation)
     - **FuzzingTokenController** - Injects fuzz-driven delays and behaviors

4. **TokenControllerProvider** (`mine.fuzzing.TokenControllerProvider`)
   - Global accessor for registry and controller
   - Defaults to no-op behavior when fuzzing is disabled
   - Allows fuzzing infrastructure to inject custom controllers

### Usage

#### Fine-Grained Loop Iteration Control

The token fuzzing extension provides two modes of operation:

**1. Free-Running Mode (default)**
```java
// Initialize registry and controller
ThreadTokenRegistry registry = new ThreadTokenRegistry();
sim.registerThreadTokens(registry);
TokenControllerProvider.setRegistry(registry);

// Controller injects fuzz-driven delays at each loop iteration
FuzzingTokenController controller = new FuzzingTokenController(data, registry, false);
TokenControllerProvider.setController(controller);

sim.startAll(); // Threads run with token-controlled delays
```

**2. Gated Iteration Mode (for controlled interleaving)**
```java
// Enable gating - threads wait for explicit permission per iteration
FuzzingTokenController controller = new FuzzingTokenController(data, registry, true);
TokenControllerProvider.setController(controller);

sim.startAll(); // Threads start but wait at loop entry

// Release specific thread iterations from fuzz input
for (int i = 0; i < releaseSteps; i++) {
    ThreadToken.Role role = /* select from fuzz input */;
    controller.releaseIterations(role, 1); // Allow one iteration
    Thread.sleep(10); // Let thread execute
}
```

**Loop Hooks in Thread Classes:**
Each thread class now has a hook at the start of its while loop:
```java
while (!this.isInterrupted()) {
    // [FUZZING-HOOK] Token-based control
    TokenControllerProvider.getController().onLoopIteration(
        TokenControllerProvider.getRegistry().getCurrentThreadToken());
    
    // ... thread work ...
}
```

#### Benefits

1. **Fine-grained control**: Control individual loop iterations, not just thread starts
2. **Reproducible interleavings**: Deterministically explore specific thread execution orders
3. **Deadlock detection**: Test controlled scenarios that trigger race conditions
4. **Role-based fuzzing**: Target specific thread types (e.g., all miners, specific engine)
5. **Zero overhead**: NoOpTokenController default ensures normal simulation is unaffected
6. **Minimal changes**: Single hook line per thread class, no structural modifications

### Integration with Existing Fuzzing

The token extension complements existing fuzzing infrastructure:
- **PauseProvider**: Controls timing within thread loops (unchanged)
- **MineSimulation**: Now supports token registration for role-based control
- **DeadlockWatcher**: Monitors remain compatible; token delays don't trigger false positives
- **MineProgress**: Continues tracking progress across all threads

### Configuration

No configuration flags are needed. The extension automatically:
- Uses NoOpTokenController by default (zero impact on normal simulation)
- Activates only when fuzzing harnesses explicitly initialize token infrastructure
- Cleans up state between test runs via `TokenControllerProvider.reset()`

### Testing

Unit tests verify token infrastructure:
```bash
# Run token tests
mvn -Dtest=ThreadTokenTest,ThreadTokenRegistryTest,TokenControllerTest test
```

### Future Extensions

The token framework enables future enhancements:
- Phase-based control (initialization, steady-state, shutdown)
- Fine-grained operation hooks (lock acquisition, queue operations)
- Custom token controllers for specific testing scenarios
- Integration with coverage-guided fuzzing for targeted exploration