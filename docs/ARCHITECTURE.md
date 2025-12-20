# Mine Automation System - Architecture

## Overview

The Mine Automation System is a **concurrent producer-consumer simulation** that models a multi-station mining operation with automated cart transport. The system demonstrates thread-safe synchronization patterns, formal verification capabilities, and advanced fuzzing infrastructure.

**Key Characteristics:**
- **Multi-threaded**: 10+ concurrent threads coordinating resource access
- **Thread-safe**: Synchronized access to shared resources (carts, elevators, stations)
- **Verifiable**: Formal verification with JBMC to prove correctness properties
- **Testable**: Fuzz testing with Jazzer to discover race conditions
- **Modular**: Clean separation between production code and testing infrastructure

## System Architecture

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                        Mine Automation System                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────┐      ┌──────────┐      ┌─────────┐                 │
│  │ Producer│─────▶│ Elevator │◀─────│Consumer │                 │
│  └─────────┘      └──────────┘      └─────────┘                 │
│       │                ▲                 │                      │
│       │                │                 │                      │
│       │          ┌──────────┐            │                      │
│       │          │ Operator │            │                      │
│       │          └──────────┘            │                      │
│       │                                  │                      │
│       ▼                                  ▼                      │
│  ┌─────────────────────────────────────────────────┐            │
│  │          Cart Transport System                  │            │
│  │  ┌────────┐    ┌────────┐    ┌────────┐         │            │
│  │  │Station0│◀──▶│Station1│◀──▶│Station2│◀─ ...   │            │
│  │  └────────┘    └────────┘    └────────┘         │            │
│  │      ▲             ▲             ▲              │            │
│  │      │             │             │              │            │
│  │  ┌───────┐     ┌───────┐     ┌───────┐          │            │
│  │  │Miner 0│     │Miner 1│     │Miner 2│          │            │
│  │  └───────┘     └───────┘     └───────┘          │            │
│  │                                                 │            │
│  │  ┌────────┐  ┌────────┐  ┌────────┐             │            │
│  │  │Engine 0│  │Engine 1│  │Engine 2│  ...        │            │
│  │  └────────┘  └────────┘  └────────┘             │            │
│  └─────────────────────────────────────────────────┘            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Thread Roles

The system consists of several types of threads, each with specific responsibilities:

#### 1. **Producer Thread** (1 instance)
- **Role**: Creates new carts and sends them into the system
- **Behavior**: Generates carts at random intervals, delivers to elevator
- **Synchronization**: Waits for elevator capacity before delivering
- **Location**: `src/main/java/mine/Producer.java`

#### 2. **Consumer Thread** (1 instance)
- **Role**: Removes carts from the system after they've been processed
- **Behavior**: Collects full carts from elevator, counts gems
- **Synchronization**: Waits for full carts to be available at elevator
- **Location**: `src/main/java/mine/Consumer.java`

#### 3. **Operator Thread** (1 instance)
- **Role**: Moves the elevator between floors
- **Behavior**: Alternates between moving up and down at regular intervals
- **Synchronization**: Coordinates with producer/consumer for cart access
- **Location**: `src/main/java/mine/Operator.java`

#### 4. **Miner Threads** (N instances, N = number of stations)
- **Role**: Mine gems and deposit them at their assigned station
- **Behavior**: Continuously mine gems, wait for cart availability, load gems
- **Synchronization**: Wait for cart to be present and previous gem to be loaded
- **Location**: `src/main/java/mine/Miner.java`

#### 5. **Engine Threads** (N+1 instances)
- **Role**: Transport carts between adjacent locations (stations and elevator)
- **Behavior**: Collect cart from source, transport, deliver to destination
- **Synchronization**: Wait for carts at source, wait for space at destination
- **Location**: `src/main/java/mine/Engine.java`
- **Instances**:
  - N-1 engines between adjacent stations
  - 1 engine from elevator to first station
  - 1 engine from last station to elevator

### Core Components

#### **Cart** (`mine.Cart`)
- **Purpose**: Represents a transport cart that carries gems
- **State**: Unique ID, gem count
- **Thread-safety**: Immutable after creation, safe to pass between threads
- **Design pattern**: Flyweight (reuses cart IDs)

#### **Station** (`mine.Station` extends `mine.Location`)
- **Purpose**: Location where miners deposit gems and carts are loaded
- **State**: 
  - Cart reference (currently at station)
  - Gem flag (whether a gem is waiting)
  - Station ID
- **Thread-safety**: All methods synchronized
- **Key operations**:
  - `depositGem()` - Miner deposits gem (blocks if station full)
  - `collect()` - Engine collects loaded cart (blocks until cart + gem ready)
  - `deliver(Cart)` - Engine delivers empty cart (blocks if station occupied)

#### **Elevator** (`mine.Elevator` extends `mine.Location`)
- **Purpose**: Central hub connecting bottom of mine to top (consumer)
- **State**:
  - Queue of carts at bottom
  - Queue of carts at top
  - Current floor (top/bottom)
- **Thread-safety**: All methods synchronized
- **Key operations**:
  - `arrive(Cart)` - Producer delivers new cart
  - `depart()` - Consumer collects full cart
  - `moveUp()` / `moveDown()` - Operator moves elevator

#### **Location** (`mine.Location`)
- **Purpose**: Abstract base class for Station and Elevator
- **Contract**: Defines `collect()` and `deliver(Cart)` interface
- **Design pattern**: Template method (subclasses implement specific behavior)

## Project Structure and Build System

### Directory Layout

```
Mine-Automation-System/
├── src/
│   ├── main/java/mine/           # Production code (simulation core)
│   │   ├── Cart.java
│   │   ├── Consumer.java
│   │   ├── Elevator.java
│   │   ├── Engine.java
│   │   ├── Location.java
│   │   ├── Main.java             # Entry point for normal simulation
│   │   ├── Mine.java
│   │   ├── MineLogger.java       # Production logger (console output)
│   │   ├── Miner.java
│   │   ├── Operator.java
│   │   ├── Params.java           # Configuration with PauseProvider
│   │   ├── PauseProvider.java    # Interface for timing control
│   │   ├── Producer.java
│   │   ├── Station.java
│   │   └── fuzzing/              # Token control infrastructure
│   │       ├── ThreadToken.java
│   │       ├── ThreadTokenRegistry.java
│   │       ├── TokenController.java
│   │       ├── TokenControllerProvider.java
│   │       └── NoOpTokenController.java
│   │
│   └── test/java/mine/           # Testing infrastructure (separate!)
│       ├── MineLogger.java       # Test logger (overrides production version)
│       ├── formal/               # JBMC verification harnesses
│       │   ├── CartJBMCVerification.java
│       │   ├── StationJBMCVerification.java
│       │   └── ElevatorJBMCVerification.java
│       └── fuzzing/              # Jazzer fuzz testing
│           ├── MineSimulation.java        # Test harness
│           ├── MineProgress.java          # Progress tracking
│           ├── DeadlockWatcher.java       # Deadlock detection
│           ├── FuzzingTokenController.java
│           ├── MineFuzzTarget.java        # Legacy fuzz target
│           ├── MineSystemFuzz.java        # JUnit fuzz target
│           └── ...                        # Additional test utilities
│
├── target/
│   ├── classes/                  # Compiled production code
│   │   └── mine/
│   │       ├── Cart.class
│   │       ├── MineLogger.class  # ← Production version
│   │       └── ...
│   │
│   └── test-classes/             # Compiled test code
│       └── mine/
│           ├── MineLogger.class  # ← Test version (OVERRIDES!)
│           ├── formal/
│           └── fuzzing/
│
├── pom.xml                       # Maven build configuration
└── docs/
    ├── ARCHITECTURE.md           # This file
    ├── FUZZING_GUIDE.md
    ├── BUGFIX_VERIFICATION.md
    └── ...
```

### Compile-Time Override Design

One of the most important architectural decisions in this project is the **compile-time override mechanism** for `MineLogger`. This design enables different behavior in production vs. testing without modifying production code.

#### The Problem

During testing (especially fuzzing and verification), we need additional capabilities:
- **Progress tracking**: Monitor if threads are making progress (deadlock detection)
- **State inspection**: Track which operations are happening
- **Minimal I/O overhead**: Reduce console output during high-speed fuzzing

However, we **cannot** modify production code with testing logic because:
- Production code should remain clean and focused
- Testing infrastructure should be isolated
- JBMC verification should analyze actual production behavior

#### The Solution: Classpath Override

Java's classloader searches for classes in a specific order:
1. **test-classes** directory (compiled test code)
2. **classes** directory (compiled production code)

By placing a **different implementation** of `MineLogger` in `src/test/java/mine/`, we achieve compile-time override:

**Production Logger** (`src/main/java/mine/MineLogger.java`):
```java
package mine;

public final class MineLogger {
    private MineLogger() {}
    
    public static void log(String component, String message) {
        // Simple console output
        System.out.printf("[%s][%s][%s] %s%n", time, thread, component, message);
    }
}
```

**Test Logger** (`src/test/java/mine/MineLogger.java`):
```java
package mine;

import mine.fuzzing.MineProgress;  // ← Test infrastructure

public final class MineLogger {
    private MineLogger() {}
    
    public static void log(String component, String message) {
        // Same console output as production
        System.out.printf("[%s][%s][%s] %s%n", time, thread, component, message);
        
        // ADDITIONAL: Track progress for deadlock detection
        MineProgress.report();  // ← Only in test version!
    }
}
```

#### How It Works

**During production run** (`mvn exec:java`):
```
Classpath: target/classes/
           ↓
Uses: mine.MineLogger from src/main/java/mine/MineLogger.java
Result: Pure console logging, no test infrastructure
```

**During testing** (`mvn test`):
```
Classpath: target/test-classes/:target/classes/
           ↓
Uses: mine.MineLogger from src/test/java/mine/MineLogger.java
Result: Console logging + progress tracking for deadlock detection
```

**Key Benefits:**
1. **Zero production code modification**: Thread classes call `MineLogger.log()` normally
2. **Automatic switching**: Maven classpath ordering handles the override
3. **Type-safe**: Both versions have identical signatures
4. **Maintainable**: Changes to logging interface require updating both files
5. **Testable**: Test version adds instrumentation without affecting production logic

#### Maven Build Configuration

The `pom.xml` configures the build to support this pattern:

```xml
<build>
    <plugins>
        <!-- Production code compilation -->
        <plugin>
            <artifactId>maven-compiler-plugin</artifactId>
            <!-- Compiles src/main/java/ → target/classes/ -->
        </plugin>
        
        <!-- Test code compilation -->
        <plugin>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <!-- Classpath order: test-classes BEFORE classes -->
                <useModulePath>false</useModulePath>
            </configuration>
        </plugin>
        
        <!-- Execution plugin -->
        <plugin>
            <artifactId>exec-maven-plugin</artifactId>
            <configuration>
                <mainClass>mine.Main</mainClass>
                <!-- Uses ONLY target/classes/, no override -->
            </configuration>
        </plugin>
    </plugins>
</build>
```

#### Alternative Design Considerations

**Why not use dependency injection?**
- Would require modifying thread constructors
- Adds complexity to production code
- Breaks encapsulation of thread classes

**Why not use interfaces?**
- Static methods are simpler for utility logging
- No need for logger instances
- Less overhead in production

**Why not use conditional compilation?**
- Java doesn't have preprocessor macros
- Build flags would complicate Maven setup
- Classpath override is idiomatic Java pattern

### Configuration and Timing Control

The system uses a similar override pattern for timing control via `Params` class:

**Production Behavior** (`src/main/java/mine/Params.java`):
```java
public class Params {
    private static PauseProvider provider = new RandomPauseProvider();
    
    public static void setPauseProvider(PauseProvider p) {
        provider = p;
    }
    
    public static long minerPause() {
        return provider.minerPause();  // Random by default
    }
}
```

**Test Behavior** (via injection, not override):
```java
// In test harness
Params.setPauseProvider(new SequencePauseProvider(data));
// Now all timing is deterministic from fuzz input
```

This achieves:
- **Production**: Random timing (realistic simulation)
- **Testing**: Deterministic timing (reproducible fuzzing)
- **No code duplication**: Single `Params` class with injectable provider

## Synchronization Design

### Synchronization Primitives

All shared resources use Java's **monitor-based synchronization**:

```java
public synchronized void depositGem() throws InterruptedException {
    while(this.gem) {  // ← Wait pattern (not if!)
        wait();
    }
    this.gem = true;
    notifyAll();       // ← Wake all waiting threads
}
```

**Key patterns:**
1. **`synchronized` methods**: Ensures mutual exclusion
2. **`wait()` in loops**: Handles spurious wakeups and race conditions
3. **`notifyAll()`**: Wakes all waiting threads (safer than `notify()`)

### Critical Sections

**Station synchronization:**
- `depositGem()`: Miner waits until previous gem loaded
- `collect()`: Engine waits until cart + gem ready
- `deliver()`: Engine waits until station empty

**Elevator synchronization:**
- `arrive()`: Producer waits until bottom has capacity
- `depart()`: Consumer waits until top has carts
- `moveUp()`/`moveDown()`: Operator moves floor, transfers carts

### Deadlock Prevention

The system avoids deadlocks through:
1. **No circular waits**: Thread roles form a DAG
2. **Bounded waits**: All waits have wakeup conditions
3. **Progress guarantees**: Each operation eventually completes if system is healthy

**Verification**: JBMC proves absence of deadlocks in bounded scenarios (see `docs/BUGFIX_VERIFICATION.md`)

## Testing and Verification Architecture

### Separation of Concerns

The architecture maintains **strict separation** between production and testing code:

| Aspect | Production (`src/main/`) | Testing (`src/test/`) |
|--------|-------------------------|----------------------|
| **Purpose** | Core simulation logic | Testing infrastructure |
| **Dependencies** | Minimal (JDK only) | Jazzer, JUnit, custom harnesses |
| **Entry point** | `Main.main()` | Test methods, fuzz targets |
| **Logging** | Console output only | Console + progress tracking |
| **Timing** | Random pauses | Controllable via injection |
| **Thread control** | No-op token controller | Fuzzing token controller |

### Testing Infrastructure

#### 1. **Formal Verification** (`src/test/java/mine/formal/`)

**Purpose**: Prove correctness properties using JBMC bounded model checking

**Harnesses:**
- `StationJBMCVerification`: Verifies station synchronization properties
- `CartJBMCVerification`: Verifies cart immutability and uniqueness
- `ElevatorJBMCVerification`: Verifies elevator state machine correctness

**Properties verified:**
- No race conditions on shared state
- No deadlocks within bounded execution
- Invariants maintained (e.g., cart uniqueness, gem accounting)

**Example:**
```java
public class StationJBMCVerification {
    public static void verifyStationSynchronization() {
        Station station = new Station(0);
        // JBMC explores all thread interleavings
        Thread miner = new Thread(() -> {
            station.depositGem();
        });
        Thread engine = new Thread(() -> {
            Cart cart = station.collect();
        });
        // Verify: no data races, no deadlocks
    }
}
```

#### 2. **Fuzz Testing** (`src/test/java/mine/fuzzing/`)

**Purpose**: Discover race conditions, deadlocks, and assertion failures through randomized testing

**Key components:**
- **`MineSimulation`**: Test harness that creates and controls simulation
- **`MineProgress`**: Tracks thread progress for deadlock detection
- **`DeadlockWatcher`**: Monitors threads and reports stuck conditions
- **`FuzzingTokenController`**: Controls thread scheduling from fuzz input
- **`MineSystemFuzz`**: JUnit-integrated fuzz target

**Workflow:**
1. Fuzzer generates random input bytes
2. Input controls thread timing and scheduling
3. Simulation runs for fixed duration
4. Watcher detects deadlocks or progress stalls
5. Crashes are saved for reproduction

#### 3. **Token-Controlled Thread Fuzzing**

**Novel contribution**: Fine-grained control of thread scheduling for deterministic testing

See [FUZZING_GUIDE.md](FUZZING_GUIDE.md) for comprehensive details.

**Key insight**: By injecting hooks at loop boundaries and controlling iteration scheduling, we can:
- Reproduce specific thread interleavings deterministically
- Test race conditions that would be rare in normal execution
- Verify fixes for known concurrency bugs

## Design Principles

### 1. **Separation of Concerns**
- Production code focuses on simulation logic
- Testing code focuses on verification and fuzzing
- Clear boundaries enforced by directory structure

### 2. **Compile-Time Polymorphism**
- Classpath override for `MineLogger` provides different behavior without code modification
- Maintains type safety and IDE support

### 3. **Dependency Injection**
- `PauseProvider` interface allows timing control without hardcoding
- `TokenController` interface allows thread control for fuzzing

### 4. **Immutability Where Possible**
- `Cart` is effectively immutable after creation
- `ThreadToken` is fully immutable
- Reduces race condition surface area

### 5. **Monitor-Based Synchronization**
- Uses Java's built-in `synchronized` / `wait()` / `notifyAll()`
- Well-understood semantics
- Compatible with JBMC verification

### 6. **Progressive Enhancement**
- Production code has zero overhead (no-op controllers)
- Testing infrastructure adds capabilities without modifying core logic
- Clean separation enables independent evolution

## Extension Points

The architecture provides several extension points for future enhancements:

### 1. **Additional Thread Types**
Add new roles by:
1. Creating thread class in `src/main/java/mine/`
2. Adding role to `ThreadToken.Role` enum
3. Registering in `MineSimulation.registerThreadTokens()`

### 2. **Custom Verification Properties**
Add JBMC harnesses in `src/test/java/mine/formal/`:
```java
public class CustomVerification {
    public static void verifyProperty() {
        // Setup scenario
        // Assert invariants
        // JBMC explores all interleavings
    }
}
```

### 3. **Alternative Synchronization Mechanisms**
The `Location` interface allows experimenting with:
- Lock-free algorithms
- Explicit `Lock` objects
- Transactional memory (STM)

### 4. **Performance Monitoring**
Extend test `MineLogger` to collect metrics:
```java
public static void log(String component, String message) {
    // Console output
    System.out.printf(...);
    // Progress tracking
    MineProgress.report();
    // NEW: Performance metrics
    MetricsCollector.recordEvent(component, message);
}
```

## Summary

The Mine Automation System demonstrates sophisticated architectural patterns for concurrent systems:

1. **Clean separation**: Production and testing code are completely independent
2. **Compile-time override**: Classpath ordering provides behavior switching
3. **Dependency injection**: Timing and thread control are pluggable
4. **Verification-friendly**: Monitor-based sync compatible with JBMC
5. **Testing-first**: Rich infrastructure for fuzzing and formal verification

This architecture enables:
- ✅ Proving correctness through formal verification
- ✅ Finding bugs through extensive fuzz testing
- ✅ Reproducing and debugging concurrency issues
- ✅ Maintaining clean, focused production code
- ✅ Evolving testing infrastructure independently

For detailed information on specific aspects:
- **Fuzzing**: See [FUZZING_GUIDE.md](FUZZING_GUIDE.md)
- **Verification**: See [VERIFICATION_GUIDE.md](VERIFICATION_GUIDE.md)
- **Bug fixes**: See [BUGFIX_VERIFICATION.md](BUGFIX_VERIFICATION.md)

