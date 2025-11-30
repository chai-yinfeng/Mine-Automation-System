

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