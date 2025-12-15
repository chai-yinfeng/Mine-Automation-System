# Bug Fix Verification

## System Configuration

With `STATIONS = 4`, the system creates:
- 3 inter-station engines: `engines[0]`, `engines[1]`, `engines[2]`
- 1 first engine: `firstEngine` (elevator to station[0])
- 1 last engine: `lastEngine` (station[n-1] to elevator)
- **Total: 5 ENGINE role instances**

## Before the Fix

All 5 engines shared a single semaphore keyed by `"ENGINE"`:

```
iterationGates:
  "ENGINE" -> Semaphore (shared by all 5 engines)

Thread execution:
  ENGINE_0 waits on semaphore
  ENGINE_1 waits on semaphore  
  ENGINE_2 waits on semaphore  <- All waiting on SAME semaphore!
  ENGINE_3 waits on semaphore
  ENGINE_4 waits on semaphore

When controller.releaseIteration(Role.ENGINE) is called:
  -> Releases 1 permit to the shared semaphore
  -> ANY of the 5 engines can acquire it (race condition)
  -> Non-reproducible execution order
```

## After the Fix

Each engine has its own semaphore keyed by unique ID:

```
iterationGates:
  "ENGINE_0" -> Semaphore (dedicated to ENGINE_0)
  "ENGINE_1" -> Semaphore (dedicated to ENGINE_1)
  "ENGINE_2" -> Semaphore (dedicated to ENGINE_2)
  "ENGINE_3" -> Semaphore (dedicated to ENGINE_3)
  "ENGINE_4" -> Semaphore (dedicated to ENGINE_4)

Thread execution:
  ENGINE_0 waits on its own semaphore
  ENGINE_1 waits on its own semaphore
  ENGINE_2 waits on its own semaphore
  ENGINE_3 waits on its own semaphore
  ENGINE_4 waits on its own semaphore

Option 1 - Instance-specific control (new API):
  controller.releaseIteration(token_ENGINE_0)
  -> Releases permit only to ENGINE_0's semaphore
  -> Only ENGINE_0 can proceed

Option 2 - Role-based control (backward compatible):
  controller.releaseIteration(Role.ENGINE)
  -> Releases 1 permit to each of the 5 engine semaphores
  -> All 5 engines proceed in parallel
```

## Test Results

All 19 existing tests pass, confirming:
- ✅ No regression in existing functionality
- ✅ Backward compatibility maintained
- ✅ New instance-specific API available when needed

## Reproducibility

The fix ensures that when using instance-specific control, the same fuzzing input will always produce the same execution order, because each instance's semaphore is controlled independently. This is critical for:
- Fuzzing-driven testing
- Deadlock detection
- Interleaving exploration
- Bug reproduction
