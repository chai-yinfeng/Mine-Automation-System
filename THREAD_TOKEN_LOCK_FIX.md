# Thread Token Lock Bug Fix

## Problem Description

The system had a concurrency bug in the `FuzzingTokenController` where multiple instances of the same role (e.g., ENGINE_0, ENGINE_1, ENGINE_2, etc.) were sharing the same semaphore for iteration gating.

### Root Cause

The `FuzzingTokenController` was using `role.name()` (e.g., "ENGINE") as the key for:
- `iterationGates` - semaphores controlling thread execution
- `iterationCounters` - tracking iteration counts
- `delaySequences` - fuzz-driven delays

This meant that all ENGINE instances shared a single semaphore. When ENGINE_0 released its token, any other engine (ENGINE_1, ENGINE_2, etc.) could acquire it, breaking reproducibility.

## Solution

Changed the implementation to use `token.getUniqueId()` (e.g., "ENGINE_0", "ENGINE_1") as the key instead of just the role name. This ensures each thread instance has its own:
- Dedicated semaphore
- Independent iteration counter
- Separate delay sequence

### Changes Made

1. **Constructor**: Modified to iterate through all registered tokens and initialize maps with `uniqueId` keys
2. **onLoopIteration()**: Changed to use `uniqueKey` instead of `roleKey`
3. **Release methods**: Added new methods that accept `ThreadToken` for instance-specific control
4. **Backward compatibility**: Maintained role-based methods that release ALL instances of a role

### API

#### Instance-Specific Control (New)
```java
// Release one iteration for a specific instance
controller.releaseIteration(threadToken);

// Release multiple iterations for a specific instance
controller.releaseIterations(threadToken, count);

// Get iteration count for a specific instance
int count = controller.getIterationCount(threadToken);
```

#### Role-Based Control (Maintained for backward compatibility)
```java
// Release one iteration for ALL instances of a role
controller.releaseIteration(ThreadToken.Role.ENGINE);

// Release multiple iterations for ALL instances of a role
controller.releaseIterations(ThreadToken.Role.ENGINE, count);

// Get total iteration count across all instances of a role
int totalCount = controller.getIterationCount(ThreadToken.Role.ENGINE);
```

## Impact

- **Reproducibility**: Each instance now has dedicated control, preventing token competition
- **Backward Compatibility**: Existing fuzzing code continues to work
- **Flexibility**: New API allows fine-grained per-instance control when needed

## Testing

All existing tests pass without modification, confirming backward compatibility.
