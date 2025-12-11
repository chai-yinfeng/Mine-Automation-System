

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

#### Token-Based Thread Selection in Fuzzing

The `MineFuzzTarget` demonstrates token-based fuzzing:

```java
// Initialize token registry
ThreadTokenRegistry registry = new ThreadTokenRegistry();
sim.registerThreadTokens(registry);

// Select threads by role (fuzz-driven)
ThreadToken.Role role = /* choose from fuzz input */;
int instanceId = /* choose from fuzz input */;
ThreadToken token = new ThreadToken(role, instanceId);
Thread t = registry.getThread(token);
// Start thread when desired
```

#### Benefits

1. **Role-based fuzzing**: Target specific thread types (e.g., all miners, specific engine)
2. **Reproducible testing**: Token-based selection enables deterministic thread scheduling
3. **Zero overhead**: No-op default ensures normal simulation is unaffected
4. **Flexible control**: Easy to add new fuzzing strategies without modifying thread code
5. **Compatible**: Works with existing DeadlockWatcher and MineProgress monitors

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