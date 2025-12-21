# Formal Verification Guide - JBMC

## Overview

This guide explains how to use **JBMC (Java Bounded Model Checker)** to formally verify correctness properties of the Mine Automation System. JBMC provides mathematical guarantees about program behavior within bounded execution scenarios, complementing fuzzing which explores runtime behavior stochastically.

**What is JBMC?**
- **Bounded Model Checker**: Exhaustively explores all possible execution paths up to a specified depth
- **Formal Verification**: Proves properties mathematically rather than testing samples
- **Concurrency-aware**: Handles multi-threaded code with synchronization primitives
- **Assertion-based**: Checks developer-written assertions are never violated

**Why Use JBMC?**
- ✅ **Exhaustive within bounds**: Explores ALL interleavings up to unwinding depth
- ✅ **Mathematical proof**: Verification provides stronger guarantees than testing
- ✅ **Early bug detection**: Catches issues before runtime testing
- ✅ **Specification validation**: Confirms design invariants hold
- ❌ **Bounded analysis**: Only verifies executions within unwinding limits
- ❌ **State explosion**: Deep unwinding or large state spaces can be slow

## Installation

### Prerequisites

- **Java 17+**: JBMC analyzes Java bytecode
- **GCC/Clang**: Required for building JBMC from source
- **Git**: To clone CBMC repository

### Building JBMC from Source

```bash
# Clone the CBMC repository (includes JBMC)
git clone https://github.com/diffblue/cbmc.git cbmc-git
cd cbmc-git

# Build JBMC
make -C jbmc/src minisat2-download
make -C jbmc/src

# Add to PATH
export PATH="$(pwd)/jbmc/src/jbmc:$PATH"

# Verify installation
jbmc --version
```

**Expected output**:
```
JBMC version X.Y cbmc-X.Y-<hash>
64-bit version
```

### Alternative: Pre-built Binaries

Some platforms offer pre-built CBMC/JBMC packages. Check the [CBMC releases page](https://github.com/diffblue/cbmc/releases).

## Quick Start

### 1. Compile the Project

```bash
# Compile both main and test classes
mvn clean test-compile
```

### 2. Set Classpath

```bash
# Set classpath for JBMC to find compiled classes
export CP="target/test-classes:target/classes"
```

### 3. Run a Simple Verification

```bash
# Verify Cart properties
jbmc mine.formal.CartJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace
```

**Expected output** (success):
```
** Results:
[main.assertion.1] line X: SUCCESS
[main.assertion.2] line Y: SUCCESS
...
** 0 of N failed (2 iterations)
VERIFICATION SUCCESSFUL
```

### 4. Verify All Harnesses

```bash
# Cart verification
jbmc mine.formal.CartJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace

# Station verification
jbmc mine.formal.StationJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace

# Elevator verification
jbmc mine.formal.ElevatorJBMCVerification \
  --classpath "$CP" \
  --unwind 4 \
  --no-unwinding-assertions \
  --trace
```

## Verification Harnesses

The project includes three verification harnesses in `src/test/java/mine/formal/`:

### 1. CartJBMCVerification

**Purpose**: Verify basic properties of `Cart.getNewCart()` and cart identity.

**Location**: `mine.formal.CartJBMCVerification`

**Properties Verified**:
1. `getNewCart()` never returns `null`
2. Every new cart starts with 0 gems
3. Cart IDs are positive integers
4. Within a single execution, IDs are strictly increasing

**Harness Structure**:
```java
public class CartJBMCVerification {
    public static void main(String[] args) {
        // JBMC chooses number of carts (bounded)
        int n = CProver.nondetInt();
        CProver.assume(0 <= n && n <= 3);
        
        int lastId = 0;
        for (int i = 0; i < n; i++) {
            Cart c = Cart.getNewCart();
            
            assert c != null;                    // Property 1
            assert c.getGems() == 0;             // Property 2
            assert c.getId() > 0;                // Property 3
            assert c.getId() > lastId;           // Property 4
            
            lastId = c.getId();
        }
    }
}
```

**Execution**:
```bash
jbmc mine.formal.CartJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace
```

**Unwinding Explanation**:
- `--unwind 3`: Explores up to 3 loop iterations (creates up to 3 carts)
- JBMC checks all possible values of `n` (0, 1, 2, 3)
- Each iteration verifies all 4 properties

**Why This Matters**:
- Cart identity is critical for tracking gems through the system
- ID uniqueness ensures carts can be distinguished
- Initial state verification confirms constructor correctness

### 2. StationJBMCVerification

**Purpose**: Verify synchronization protocol and state transitions of `Station`.

**Location**: `mine.formal.StationJBMCVerification`

**Properties Verified**:
1. `collect()` always returns the same cart instance that was delivered
2. Exactly one gem is added during a deposit-collect cycle
3. After `collect()`, the station is empty (no cart, no gem)
4. Gem counts never become negative

**Protocol Assumptions** (client contract):
- `depositGem()` only called when station has no gem
- `deliver(cart)` only called when station has no cart
- `collect()` only called when station has both cart and gem

**Harness Structure**:
```java
public class StationJBMCVerification {
    public static void main(String[] args) throws Exception {
        Station station = new Station(0);
        Cart trackedCart = null;
        int trackedInitialGems = 0;
        
        // JBMC chooses number of operations
        int steps = CProver.nondetInt();
        CProver.assume(0 <= steps && steps <= 3);
        
        for (int i = 0; i < steps; i++) {
            // JBMC chooses operation: 0=deposit, 1=deliver, 2=collect
            int op = CProver.nondetInt();
            CProver.assume(0 <= op && op <= 2);
            
            switch (op) {
                case 0: // depositGem
                    if (!station.hasGem()) {
                        station.depositGem();
                    }
                    break;
                    
                case 1: // deliver
                    if (!station.hasCart()) {
                        trackedCart = Cart.getNewCart();
                        trackedInitialGems = trackedCart.getGems();
                        station.deliver(trackedCart);
                    }
                    break;
                    
                case 2: // collect
                    if (station.hasCart() && station.hasGem()) {
                        Cart out = station.collect();
                        
                        assert out == trackedCart;                      // Property 1
                        assert out.getGems() == trackedInitialGems + 1; // Property 2
                        assert !station.hasCart();                      // Property 3
                        assert !station.hasGem();                       // Property 3
                    }
                    break;
            }
            
            // Safety invariant
            if (trackedCart != null) {
                assert trackedCart.getGems() >= 0;  // Property 4
            }
        }
    }
}
```

**Execution**:
```bash
jbmc mine.formal.StationJBMCVerification \
  --classpath "$CP" \
  --unwind 3 \
  --no-unwinding-assertions \
  --trace
```

**What JBMC Explores**:
- All possible operation sequences up to 3 steps
- Example sequences:
  - `deposit, deliver, collect`
  - `deliver, deposit, collect`
  - `deposit, deposit, collect` (second deposit skipped)
  - `collect` (skipped due to precondition)
- All possible cart allocation decisions (reuse vs. new)

**Why This Matters**:
- Station is the critical synchronization point between miners and engines
- Gem accounting correctness ensures no gems are lost or duplicated
- State machine verification confirms protocol adherence

### 3. ElevatorJBMCVerification

**Purpose**: Verify elevator position state machine and basic invariants.

**Location**: `mine.formal.ElevatorJBMCVerification`

**Scope**: This harness focuses ONLY on `operate()` (position changes). Methods with blocking loops (`arrive`, `depart`, `deliver`, `collect`) are better tested through fuzzing.

**Properties Verified**:
1. Elevator position is always either "top" or "bottom" (exclusive)
2. Each `operate()` call toggles the position
3. `operate()` does not create or destroy carts
4. Initial state is correct (at top, empty)

**Harness Structure**:
```java
public class ElevatorJBMCVerification {
    public static void main(String[] args) {
        Elevator e = new Elevator();
        
        // Verify initial state
        assert e.isAtTop();
        assert !e.hasCart();
        assert e.isAtTop() ^ e.isAtBottom();  // XOR: exactly one true
        
        // JBMC chooses number of operate() calls
        int steps = CProver.nondetInt();
        CProver.assume(0 <= steps && steps <= 4);
        
        for (int i = 0; i < steps; i++) {
            boolean wasTop = e.isAtTop();
            boolean wasBottom = e.isAtBottom();
            
            assert wasTop ^ wasBottom;  // Position consistent before
            
            e.operate();
            
            assert e.isAtTop() || e.isAtBottom();  // Position valid after
            
            // Position must flip
            if (wasTop) {
                assert e.isAtBottom();
            }
            if (wasBottom) {
                assert e.isAtTop();
            }
            
            // Cart state unchanged
            assert !e.hasCart();
        }
    }
}
```

**Execution**:
```bash
jbmc mine.formal.ElevatorJBMCVerification \
  --classpath "$CP" \
  --unwind 4 \
  --no-unwinding-assertions \
  --trace
```

**What JBMC Explores**:
- All sequences of 0-4 `operate()` calls
- Verifies state machine transitions:
  ```
  top → operate() → bottom → operate() → top → ...
  ```

**Why This Matters**:
- Elevator position determines cart routing
- State machine correctness is critical for system safety
- Position invariant ensures no undefined states

## JBMC Command-Line Options

### Essential Options

#### `--classpath <path>`
Specifies where to find compiled `.class` files.

**Example**:
```bash
--classpath "target/test-classes:target/classes"
```

**Why**: JBMC analyzes bytecode, needs both production and test classes.

#### `--unwind <N>`
Sets loop unrolling depth (how many iterations to explore).

**Example**:
```bash
--unwind 3   # Explores up to 3 loop iterations
```

**Tradeoff**:
- **Too low**: May miss bugs in deeper executions
- **Too high**: Verification time explodes exponentially

**Guidelines**:
- Start with 2-3 for initial verification
- Increase to 5-10 if verification succeeds and you want deeper coverage
- Monitor verification time (should complete in minutes, not hours)

#### `--no-unwinding-assertions`
Disables warnings about potentially incomplete loop exploration.

**Why Use It**:
- Bounded model checking inherently explores finite depth
- Unwinding assertions add noise when you know the bound is reasonable
- Focus on actual property violations, not theoretical incompleteness

**When to Remove It**:
- If you suspect bugs beyond the unwinding depth
- When you want JBMC to warn about incomplete exploration

#### `--trace`
Generates counterexample trace when verification fails.

**Example Output** (on failure):
```
Counterexample:
  step 1: n = 2
  step 2: op = 1 (deliver)
  step 3: trackedCart = Cart@123
  step 4: op = 2 (collect)
  step 5: ASSERTION FAILED: out.getGems() == trackedInitialGems + 1
```

**Why**: Invaluable for debugging—shows exact input and execution that violates property.

### Advanced Options

#### `--max-nondet-array-length <N>`
Limits size of non-deterministic arrays.

**Default**: 5

**When to Adjust**: If harness creates arrays based on `CProver.nondetInt()`.

#### `--stop-on-fail`
Stops after finding first property violation.

**Use Case**: Quick feedback during development.

#### `--json-ui`
Outputs results in JSON format for automated processing.

**Use Case**: CI/CD pipelines, automated reporting.

#### `--no-simplify`
Disables simplification of verification conditions.

**Use Case**: Debugging JBMC itself, or when simplification causes issues.

## Understanding Verification Results

### Success

```
** Results:
[main.assertion.1] line 34: SUCCESS
[main.assertion.2] line 38: SUCCESS
[main.assertion.3] line 43: SUCCESS
[main.assertion.4] line 44: SUCCESS

** 0 of 4 failed (2 iterations)
VERIFICATION SUCCESSFUL
```

**Interpretation**:
- All assertions passed for all explored paths
- "2 iterations" means JBMC explored 2 loop unrollings
- **Guarantee**: Within the bounded depth, NO execution violates properties

### Failure

```
** Results:
[main.assertion.1] line 34: SUCCESS
[main.assertion.2] line 38: FAILURE

Counterexample:
  State 0:
    n = 1
  State 1:
    c = Cart@42
    c.gems = 5  // BUG: Should be 0!
  State 2:
    ASSERTION VIOLATED: c.getGems() == 0

** 1 of 2 failed (1 iteration)
VERIFICATION FAILED
```

**Interpretation**:
- Second assertion failed in at least one explored path
- Counterexample shows exact values leading to failure
- **Action Required**: Fix the bug and re-verify

### Timeout/Out of Memory

```
JBMC ran for 10 minutes and used 32GB of memory...
```

**Causes**:
- Unwinding depth too high
- Complex synchronization with many threads
- Large state space

**Solutions**:
1. Reduce `--unwind` value
2. Simplify harness (verify subset of operations)
3. Add constraints with `CProver.assume()`
4. Run on machine with more resources

## Writing Custom Verification Harnesses

### Basic Template

```java
package mine.formal;

import org.cprover.CProver;

public class MyVerification {
    public static void main(String[] args) throws Exception {
        // 1. Create objects under verification
        MyClass obj = new MyClass();
        
        // 2. Let JBMC choose execution parameters
        int steps = CProver.nondetInt();
        CProver.assume(0 <= steps && steps <= 5);  // Bound
        
        for (int i = 0; i < steps; i++) {
            // 3. Let JBMC choose operation
            int op = CProver.nondetInt();
            CProver.assume(0 <= op && op <= 2);
            
            // 4. Execute operation
            switch (op) {
                case 0:
                    obj.methodA();
                    break;
                case 1:
                    obj.methodB();
                    break;
                case 2:
                    obj.methodC();
                    break;
            }
            
            // 5. Assert invariants
            assert obj.invariant();
        }
    }
}
```

### Best Practices

#### 1. Use `CProver.nondetInt()` for Non-Determinism

```java
// GOOD: Let JBMC choose values
int x = CProver.nondetInt();
CProver.assume(0 <= x && x <= 10);

// BAD: Hard-coded values don't explore possibilities
int x = 5;
```

#### 2. Add Reasonable Bounds with `CProver.assume()`

```java
// GOOD: Bounded exploration
int n = CProver.nondetInt();
CProver.assume(0 <= n && n <= 3);  // Small bound

// BAD: Unbounded can cause verification timeout
int n = CProver.nondetInt();
// No assume() → JBMC may explore huge values
```

#### 3. Respect Preconditions

```java
// GOOD: Only call method when precondition holds
if (obj.canPerformOperation()) {
    obj.performOperation();
}

// BAD: Calling without precondition may trigger spurious failures
obj.performOperation();  // May violate internal assumptions
```

#### 4. Assert Postconditions

```java
// GOOD: Clear property verification
int before = obj.getCount();
obj.increment();
assert obj.getCount() == before + 1;

// BAD: Vague assertion
assert obj.getCount() > 0;  // What does this prove?
```

#### 5. Start Simple, Add Complexity Gradually

```java
// Phase 1: Verify single operation
obj.methodA();
assert obj.invariant();

// Phase 2: Verify sequence of operations
obj.methodA();
obj.methodB();
assert obj.invariant();

// Phase 3: Verify non-deterministic sequences
for (int i = 0; i < steps; i++) {
    int op = CProver.nondetInt();
    CProver.assume(0 <= op && op <= 2);
    switch (op) { ... }
    assert obj.invariant();
}
```

## Limitations and When to Use Fuzzing Instead

### JBMC Strengths

✅ **Exhaustive within bounds**: Explores ALL paths up to unwinding depth  
✅ **Mathematical proof**: Verification is conclusive within bounds  
✅ **Deterministic**: Same harness always produces same result  
✅ **Early detection**: Catches bugs before runtime testing  

### JBMC Limitations

❌ **Bounded depth**: Only verifies finite executions  
❌ **State explosion**: Deep unwinding can be infeasible  
❌ **Synchronization complexity**: Multi-threaded verification is expensive  
❌ **No runtime behavior**: Can't detect performance issues, deadlocks in unbounded execution  

### When to Use JBMC

- ✅ Verify **state machine correctness** (e.g., elevator position)
- ✅ Verify **protocol adherence** (e.g., station operation sequence)
- ✅ Verify **data structure invariants** (e.g., cart ID uniqueness)
- ✅ Verify **short operation sequences** (2-5 steps)

### When to Use Fuzzing (Jazzer)

- ✅ Discover **deadlocks** in long-running multi-threaded execution
- ✅ Find **race conditions** that manifest under specific timing
- ✅ Test **integration** of multiple components
- ✅ Explore **deep execution paths** (hundreds of iterations)
- ✅ Test **realistic workloads** with actual thread scheduling

### Complementary Approach

**Best Practice**: Use BOTH verification methods:

1. **JBMC first**: Verify basic invariants and protocols
   - Fast feedback (minutes)
   - Proves correctness within bounds
   - Catches logic errors early

2. **Fuzzing second**: Explore runtime behavior
   - Longer runs (hours)
   - Discovers integration issues
   - Tests under realistic conditions

**Example Workflow**:
```bash
# Step 1: Verify core components with JBMC
jbmc mine.formal.CartJBMCVerification --classpath "$CP" --unwind 3
jbmc mine.formal.StationJBMCVerification --classpath "$CP" --unwind 3
jbmc mine.formal.ElevatorJBMCVerification --classpath "$CP" --unwind 4

# Step 2: If JBMC verification passes, run fuzzing
export JAZZER_FUZZ=1
mvn -Dtest=mine.fuzzing.MineSystemFuzz test

# Step 3: If fuzzing finds a bug, write a JBMC harness to verify the fix
# (Create focused harness that reproduces the bug scenario)
```

## Troubleshooting

### Problem: "Class not found"

```
Error: Could not find class mine.formal.CartJBMCVerification
```

**Solution**:
```bash
# Ensure test classes are compiled
mvn test-compile

# Verify classpath includes test-classes
export CP="target/test-classes:target/classes"
jbmc mine.formal.CartJBMCVerification --classpath "$CP" ...
```

### Problem: "Unwinding assertion failed"

```
Unwinding assertion failed at loop in main
```

**Solution**: Increase `--unwind` value or add `--no-unwinding-assertions`:
```bash
# Option 1: Increase unwinding
jbmc ... --unwind 5

# Option 2: Suppress warning (if bound is reasonable)
jbmc ... --unwind 3 --no-unwinding-assertions
```

### Problem: Verification takes forever

```
JBMC running for 30+ minutes...
```

**Solutions**:
1. Reduce unwinding depth:
   ```bash
   jbmc ... --unwind 2  # Instead of --unwind 5
   ```

2. Add tighter bounds in harness:
   ```java
   // Before: Large bound
   CProver.assume(0 <= n && n <= 10);
   
   // After: Smaller bound
   CProver.assume(0 <= n && n <= 3);
   ```

3. Simplify harness (verify fewer operations):
   ```java
   // Before: 3 operations
   CProver.assume(0 <= op && op <= 2);
   
   // After: 2 operations
   CProver.assume(0 <= op && op <= 1);
   ```

### Problem: False positive (spurious failure)

```
ASSERTION FAILED: obj.getState() == VALID
```

But you believe the assertion should hold.

**Debugging Steps**:

1. Check the counterexample trace:
   ```bash
   jbmc ... --trace
   ```
   
2. Verify preconditions are respected:
   ```java
   // Add explicit precondition checks
   if (obj.canPerformOperation()) {
       obj.performOperation();
   } else {
       // Operation skipped
   }
   ```

3. Add intermediate assertions to narrow down the issue:
   ```java
   obj.methodA();
   assert obj.intermediateState();  // Should this hold?
   obj.methodB();
   assert obj.finalState();
   ```

## Verified Properties Summary

| Component | Property | Verification | Status |
|-----------|----------|--------------|--------|
| **Cart** | `getNewCart()` never null | `CartJBMCVerification` | ✅ Verified |
| **Cart** | New carts start with 0 gems | `CartJBMCVerification` | ✅ Verified |
| **Cart** | Cart IDs are positive | `CartJBMCVerification` | ✅ Verified |
| **Cart** | Cart IDs strictly increasing | `CartJBMCVerification` | ✅ Verified |
| **Station** | `collect()` returns delivered cart | `StationJBMCVerification` | ✅ Verified |
| **Station** | Exactly 1 gem added per cycle | `StationJBMCVerification` | ✅ Verified |
| **Station** | Station empty after collect | `StationJBMCVerification` | ✅ Verified |
| **Station** | Gem counts never negative | `StationJBMCVerification` | ✅ Verified |
| **Elevator** | Position always top XOR bottom | `ElevatorJBMCVerification` | ✅ Verified |
| **Elevator** | `operate()` toggles position | `ElevatorJBMCVerification` | ✅ Verified |
| **Elevator** | `operate()` preserves cart state | `ElevatorJBMCVerification` | ✅ Verified |
| **Elevator** | Initial state correct | `ElevatorJBMCVerification` | ✅ Verified |

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: JBMC Verification

on: [push, pull_request]

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Set up Java
        uses: actions/setup-java@v2
        with:
          java-version: '17'
          
      - name: Install JBMC
        run: |
          git clone https://github.com/diffblue/cbmc.git
          cd cbmc
          make -C jbmc/src minisat2-download
          make -C jbmc/src
          echo "$PWD/jbmc/src/jbmc" >> $GITHUB_PATH
          
      - name: Compile Project
        run: mvn test-compile
        
      - name: Run JBMC Verification
        run: |
          export CP="target/test-classes:target/classes"
          jbmc mine.formal.CartJBMCVerification --classpath "$CP" --unwind 3 --no-unwinding-assertions
          jbmc mine.formal.StationJBMCVerification --classpath "$CP" --unwind 3 --no-unwinding-assertions
          jbmc mine.formal.ElevatorJBMCVerification --classpath "$CP" --unwind 4 --no-unwinding-assertions
```

## Further Reading

- **JBMC Documentation**: https://github.com/diffblue/cbmc/tree/develop/jbmc
- **CBMC User Guide**: https://www.cprover.org/cbmc/
- **Model Checking**: https://en.wikipedia.org/wiki/Model_checking
- **Formal Methods**: https://en.wikipedia.org/wiki/Formal_methods

## Summary

JBMC provides **mathematical proof** of correctness properties within bounded execution:

- ✅ **CartJBMCVerification**: Proves cart identity and initialization
- ✅ **StationJBMCVerification**: Proves synchronization protocol correctness
- ✅ **ElevatorJBMCVerification**: Proves state machine transitions

Combined with **Jazzer fuzzing**, this dual approach provides:
- **Early verification** (JBMC) + **Runtime testing** (Fuzzer)
- **Bounded proof** (JBMC) + **Unbounded exploration** (Fuzzer)
- **Logic validation** (JBMC) + **Integration testing** (Fuzzer)

For fuzzing details, see [FUZZING_GUIDE.md](FUZZING_GUIDE.md).  
For architecture, see [ARCHITECTURE.md](ARCHITECTURE.md).

