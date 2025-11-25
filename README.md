

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
jbmc -classpath target/classes:target/test-classes mine.StationJBMCVerification

# Run fuzzer
jazzer --cp="target/classes:target/test-classes" --target_class=mine.StationFuzz

```