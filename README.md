# Mine Automation System

A concurrent mine automation simulation with formal verification (JBMC) and fuzz testing (Jazzer) infrastructure.

## Quick Start

```bash
# 1. Build the project
mvn clean package -DskipTests

# 2. Run simulation
mvn exec:java

# 3. Run tests
mvn test

# 4. Run fuzzing (requires Jazzer CLI)
export JAZZER_FUZZ=1
mvn -Dtest=mine.fuzzing.MineSystemFuzz test
```

## Project Overview

This project implements a multi-threaded mine automation system with:
- **Producer/Consumer pipeline** for ore processing
- **Thread-safe synchronization** across carts, elevators, and stations
- **Formal verification** using JBMC to prove correctness properties
- **Fuzz testing** with Jazzer to discover race conditions and deadlocks
- **Compile-time override design** for clean separation of production and testing code

The architecture features a novel approach: **production code** (`src/main/`) remains clean and focused, while **testing infrastructure** (`src/test/`) provides verification and fuzzing capabilities through compile-time classpath override. This enables different behavior in testing vs. production without modifying core simulation logic.

For detailed architecture, compile-time override mechanism, and design principles, see **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

## Project Structure

```
Mine-Automation-System/
├── src/
│   ├── main/java/mine/          # Core simulation code
│   │   ├── Cart.java            # Transport cart logic
│   │   ├── Elevator.java        # Elevator controller
│   │   ├── Station.java         # Loading/unloading station
│   │   ├── Producer.java        # Miner + Engine threads
│   │   ├── Consumer.java        # Operator thread
│   │   └── fuzzing/             # Token-based thread control
│   └── test/java/mine/          # Test harnesses
│       ├── formal/              # JBMC verification tests
│       └── fuzzing/             # Jazzer fuzz targets
├── docs/
│   ├── ARCHITECTURE.md          # System design and components
│   ├── BUGFIX_VERIFICATION.md   # Bug fixes and verification results
│   ├── THREAD_TOKEN_LOCK_FIX.md # Thread token synchronization fix
│   ├── FUZZING_GUIDE.md         # Fuzzing instructions and results
│   └── VERIFICATION_GUIDE.md    # JBMC verification guide
├── test-artifacts/              # Fuzzing results
│   ├── crashes/                 # Discovered crash inputs
│   ├── timeouts/                # Timeout cases
│   └── corpus/                  # Seed corpus
└── pom.xml                      # Maven configuration
```

## Documentation

- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** - System design, compile-time override mechanism, thread roles, synchronization patterns, and separation of production/testing code
- **[FUZZING_GUIDE.md](docs/FUZZING_GUIDE.md)** - Comprehensive fuzzing guide including token-controlled thread fuzzing extension
- **[BUGFIX_VERIFICATION.md](docs/BUGFIX_VERIFICATION.md)** - Known bugs, fixes, and verification results
- **[THREAD_TOKEN_LOCK_FIX.md](docs/THREAD_TOKEN_LOCK_FIX.md)** - Thread token deadlock fix details

## Building and Testing

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- (Optional) JBMC for formal verification
- (Optional) Jazzer CLI for advanced fuzzing

### Compile

```bash
mvn clean compile
```

### Run Simulation

```bash
# Default configuration (5 miners, random pauses)
mvn exec:java

# Custom configuration via system properties
mvn exec:java -Dmine.numMiners=10 -Dmine.seed=42
```

### Run All Tests

```bash
# Includes unit tests, verification harnesses, and regression fuzz tests
mvn test
```

## Formal Verification with JBMC

Verify thread-safety properties using bounded model checking:

```bash
# Install JBMC (if not already installed)
# See: https://github.com/diffblue/cbmc
export PATH=~/cbmc-git/jbmc/src/jbmc:$PATH

# Compile test classes
mvn -q -DskipTests test-compile

# Set classpath
CP="target/test-classes:target/classes"

# Verify Station synchronization
jbmc mine.formal.StationJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace

# Verify Cart thread-safety
jbmc mine.formal.CartJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace

# Verify Elevator logic
jbmc mine.formal.ElevatorJBMCVerification \
  --classpath "$CP" \
  --unwind 4 \
  --no-unwinding-assertions \
  --trace
```

For detailed verification results and property explanations, see [docs/VERIFICATION_GUIDE.md](docs/VERIFICATION_GUIDE.md).

## Fuzz Testing with Jazzer

### Option 1: Maven Plugin (Recommended)

```bash
# ================= Run modern JUnit fuzz target =================
# Run all tests（including @Test and @FuzzTest）
mvn test

# --- Regression mode ---
unset JAZZER_FUZZ
mvn -Dtest=mine.fuzzing.MineSystemFuzz test

# --- Fuzzing mode ---
export JAZZER_FUZZ=1
mvn -Dtest=mine.fuzzing.MineSystemFuzz test
```

### Option 2: Jazzer CLI (Advanced)

Requires [Jazzer CLI](https://github.com/CodeIntelligenceTesting/jazzer) installed:

```bash
# Compile project
mvn -q -DskipTests test-compile

# Build classpath
CP="target/test-classes:target/classes:$(mvn -q -DincludeScope=test dependency:build-classpath -Dmdep.path)"

# ================= Run legacy fuzz target =================
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

**Configuration Notes**:
- **`-max_len=4096`**: Token-based fuzzing consumes more data per run (4-50KB typical) due to per-iteration thread control. 4KB provides balanced coverage without excessive mutation overhead.
- **`-timeout=50`**: Allows time for gated thread scheduling (each iteration released sequentially) plus 15s deadlock detection window.
- **`test-artifacts/corpus`**: Pre-seeded corpus with large (5-50KB) random inputs. Token fuzzing requires larger initial seeds than typical fuzzers.

**Debugging**: Use `SimpleFuzzerTest.java` for quick iteration without Jazzer overhead:
```bash
java -cp "target/test-classes:target/classes:$(mvn -q dependency:build-classpath)" \
  mine.fuzzing.SimpleFuzzerTest
```

For detailed corpus management, data consumption analysis, and debugging strategies, see [docs/FUZZING_GUIDE.md](docs/FUZZING_GUIDE.md).

## Token-Controlled Thread Fuzzing

This project includes a novel **token-based thread control** system for deterministic interleaving exploration:

- **Fine-grained control**: Manage individual thread loop iterations at runtime
- **Role-based targeting**: Control specific thread types (miners, operators, etc.) or individual instances
- **Two operation modes**: Free-running (fuzz-driven delays) or gated (explicit iteration control)
- **Zero overhead**: Disabled by default in normal simulation
- **Reproducible testing**: Deterministically replay specific thread interleaving
- **Deadlock testing**: Force specific race conditions and verify fixes

**For detailed architecture, implementation principles, and usage examples, see [docs/FUZZING_GUIDE.md](docs/FUZZING_GUIDE.md).**

## Known Issues and Fixes

The project has undergone multiple verification and fuzzing cycles. Major issues discovered and fixed:

1. **Thread Token Deadlock** - Fixed lock ordering in `ThreadTokenRegistry`
   See [docs/THREAD_TOKEN_LOCK_FIX.md](docs/THREAD_TOKEN_LOCK_FIX.md)

2. **Station Race Conditions** - Verified with JBMC and fixed synchronization
   See [docs/BUGFIX_VERIFICATION.md](docs/BUGFIX_VERIFICATION.md)

3. **Elevator State Machine** - Corrected empty queue handling
   See verification results in [docs/VERIFICATION_GUIDE.md](docs/VERIFICATION_GUIDE.md)

## Contributing

When modifying the codebase:

1. Run full test suite: `mvn test`
2. Verify with JBMC (if modifying synchronization logic)
3. Run fuzzing for at least 60 seconds
4. Update relevant documentation in `docs/`



