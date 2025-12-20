package mine.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import mine.Params;

public class MineFuzzTarget {

    /**
     * Fuzz entry point.
     */
    private static final long MAX_RUN_MS = 15000;
    
    /**
     * Control whether to release all gates when fuzzer data is exhausted.
     * If false (default), fuzzing terminates gracefully with threads remaining gated.
     * If true, threads are released for free execution after data runs out.
     */
    private static boolean releaseGatesOnDataExhaustion = false;

//    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
//
//        if (data.remainingBytes() < 8) {
//            return;
//        }
//
//        // 1. Decode fuzz input into timing sequences + run time bound
    ////        long maxRunMs = data.consumeLong(1000, 5000); // total simulation time
//        SequencePauseProvider provider = new SequencePauseProvider(data);
//
//        // 2. Install fuzz-driven pause provider
//        Params.setPauseProvider(provider);
//
//        // 3. Reset progress counter
//        MineProgress.reset();
//
//        // 4. Build and start the simulation
//        MineSimulation sim = new MineSimulation();
//        sim.startAll();
//
//        // 5. Watch for deadlock / stall
//        DeadlockWatcher watcher = new DeadlockWatcher(MAX_RUN_MS);
//        try {
//            watcher.watch();
//        } catch (AssertionError e) {
//            // Print the fuzzed parameters for debugging
//            System.out.println("maxRunMs = " + MAX_RUN_MS);
//            System.out.println(provider);
//            throw e; // Continue throwing to let Jazzer record it as a finding.
//        } finally {
//            // 6. Always try to stop threads cleanly
//            try {
//                sim.stopAll();
//            } catch (InterruptedException ignored) {}
//            Params.resetPauseProvider();
//        }
//    }

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {

        int initialBytes = data.remainingBytes();
        System.err.println("Input size: " + initialBytes + " bytes");

        if (data.remainingBytes() < 200) {
            return;
        }

        // 1. Decode fuzz input into timing sequences
        SequencePauseProvider provider = new SequencePauseProvider(data);
        Params.setPauseProvider(provider);

        MineProgress.reset();

        // 2. Build the simulation (threads are constructed but not started)
        MineSimulation sim = new MineSimulation();

        // 3. Initialize token-based fuzzing infrastructure
        ThreadTokenRegistry registry = new ThreadTokenRegistry();
        sim.registerThreadTokens(registry);
        TokenControllerProvider.setRegistry(registry);

        // 4. Decide fuzzing mode: gated iteration control or free-running with delays
        boolean useGating = true;
        FuzzingTokenController controller = new FuzzingTokenController(data, registry, useGating);
        TokenControllerProvider.setController(controller);

        // 5. Start threads - all or subset based on fuzz input
        boolean startAll = true;

        // 6. Watch for deadlock / stall
        AsyncDeadlockWatcher watcher = new AsyncDeadlockWatcher(MAX_RUN_MS, Thread.currentThread());
        watcher.start();
        try {
            if (startAll) {
                sim.startAll();
            } else {
                // Start subset of threads based on token selection
                int numStarts = data.consumeInt(1, sim.threadCount());
                for (int i = 0; i < numStarts && data.remainingBytes() > 1; i++) {
                    int roleIdx = data.consumeInt(0, ThreadToken.Role.values().length - 1);
                    ThreadToken.Role role = ThreadToken.Role.values()[roleIdx];
                    int instanceId = data.consumeInt(0, 3);
                    ThreadToken token = new ThreadToken(role, instanceId);
                    Thread t = registry.getThread(token);
                    if (t != null) {
                        Thread[] allThreads = sim.getAllThreads();
                        for (int j = 0; j < allThreads.length; j++) {
                            if (allThreads[j] == t) {
                                sim.startThread(j);
                                break;
                            }
                        }
                    }
                }
                sim.startAllRemaining();
            }

            int[] trace_token = new int[]{
                0, 10, 10, 3, 7, 7, 2, 8, 8, 5, 9, 9, 4, 11, 6,
                0, 10, 10, 3, 7, 7, 2, 8, 8, 5, 9, 9, 4, 6,
                0, 10, 10, 3, 7, 7, 2, 8, 8, 5, 9, 9, 6,
                0, 10, 10, 3, 7, 7, 2, 8, 8, 5, 6,
                0, 10, 10, 3, 7, 7, 2, 8, 6,
                0, 10, 10, 3, 7, 7, 2, 6,
                0, 10, 10, 3, 7, 6,
                0, 10, 10, 3, 6,
                0, 10, 6,
                0, 10,
                0
            };
//            int[] trace_token = new int[]{};

            int index = 0;

            // 7. If using gated control, release iterations based on fuzz input
            if (useGating) {
                // Get all registered tokens for precise control
                java.util.List<ThreadToken> allTokens = new java.util.ArrayList<>(registry.getAllTokens());

                // Only proceed if there are registered tokens
                if (!allTokens.isEmpty()) {
                    int consecutiveBlocked = 0;
                    final int MAX_CONSECUTIVE_BLOCKED = 100; // Try 100 tokens before forcing one

                    boolean firstsetup = true;
                    int iter = 9;
                    while (data.remainingBytes() > 1) {
                        // Pick a unique token to release (instance-specific control)
                        int tokenIdx = 0;
                        if (index < trace_token.length) {
                            tokenIdx = trace_token[index];
                            index ++;
                        }else{
                            if (firstsetup) {
                                System.out.println("===============Finished Setup===============");
                                firstsetup = false;
                                return;
                            }
                            tokenIdx = data.consumeInt(0, allTokens.size() - 1);
                        }
                        ThreadToken token = allTokens.get(tokenIdx);

                        // Check if this thread can actually make progress (not blocked on a wait condition)
                        boolean canProceed = sim.canThreadProceed(token);
                        boolean tokenGranted = false;


                        if (canProceed) {
                            // Release exactly 1 iteration for serialized execution
                            // Only one thread works at a time, completing its task before the next token is granted
                            System.out.println("Token granted to: " + token);
                            controller.releaseIteration(token);
                            consecutiveBlocked = 0; // Reset counter on successful grant
                            tokenGranted = true;
                        } else {
                            consecutiveBlocked++;

                            // If too many consecutive tokens are blocked, force grant one to prevent livelock
                            // This handles cases where conditional logic is too conservative
                            if (consecutiveBlocked >= MAX_CONSECUTIVE_BLOCKED) {
                                System.out.println("WARNING: " + MAX_CONSECUTIVE_BLOCKED + " consecutive tokens blocked. Force-granting token to: " + token.getUniqueId());
                                controller.releaseIteration(token);
                                consecutiveBlocked = 0;
                                tokenGranted = true;
                            } else {
                                // Thread is blocked, don't grant token
                                // Consume some bytes to avoid infinite loop
                                final int DUMMY_CONSUME_LIMIT = 100;
                                if (data.remainingBytes() > 1) {
                                    data.consumeInt(0, DUMMY_CONSUME_LIMIT);
                                }
                            }
                        }

                        System.out.println("Remaining bytes: " + data.remainingBytes());
                        printThreadStatusTable(sim, registry);

                        // Only sleep if we actually granted a token
                        // Long delay to ensure the thread completes its work before next token grant
                        // This provides serialized execution - one thread works at a time
                        // Delay must be long enough for:
                        // - Thread to acquire the permit
                        // - Execute its business logic (collect/deliver/etc with blocking waits)
                        // - Complete and loop back to waiting state
                        //
                        // Note: This is a pragmatic timeout-based approach. A more sophisticated solution
                        // would use completion callbacks, but that would require modifying all thread classes.
                        // For fuzzing purposes, this 5-second delay is acceptable and provides reliable
                        // serialization. Adjust if thread operations take longer than expected.
                        if (tokenGranted) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }

                    // After fuzzer data is exhausted, determine behavior based on configuration
                    // Default behavior (releaseGatesOnDataExhaustion=false): terminate fuzzing gracefully
                    // Optional behavior (releaseGatesOnDataExhaustion=true): release all gates for free execution
                    if (releaseGatesOnDataExhaustion) {
                        System.out.println("Fuzzer data exhausted. Releasing all gates for free execution...");
                        controller.releaseAllGates();
                    } else {
                        System.out.println("Fuzzer data exhausted. Fuzzing test completed successfully.");
                        printThreadStatusTable(sim, registry);
                        // Gracefully terminate - threads remain gated, fuzzing test is complete
                        // DeadlockWatcher will not detect this as a deadlock since we're intentionally stopping
                        return;
                    }
                }
            }
        } catch (AssertionError e) {
            System.out.println("maxRunMs = " + MAX_RUN_MS);
            System.out.println(provider);
            System.out.println("Gating enabled: " + useGating);
            if (useGating) {
                System.out.println("Iteration counts:");
                for (ThreadToken.Role role : ThreadToken.Role.values()) {
                    System.out.println("  " + role + ": " + controller.getIterationCount(role));
                }
            }
            throw e;
        } finally {
            watcher.stop();
            watcher.throwIfDetected();
            try {
                sim.stopAll();
            } catch (InterruptedException ignored) {}
            Params.resetPauseProvider();
            TokenControllerProvider.reset();
        }
    }
    
    /**
     * Print a thread status table showing which threads can proceed.
     * This method does not produce any output from the system itself, only displays
     * the current state of all threads based on their canProceed() checks.
     * 
     * @param sim The simulation containing all threads
     * @param registry The token registry mapping threads to tokens
     */
    private static void printThreadStatusTable(MineSimulation sim, ThreadTokenRegistry registry) {
        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║         Thread Status Table (Current Tick)         ║");
        System.out.println("╠════════════════════════════════╦═══════════════════╣");
        System.out.println("║ Thread                         ║ Can Proceed?      ║");
        System.out.println("╠════════════════════════════════╬═══════════════════╣");
        
        // Get all registered tokens and sort them for consistent output
        java.util.List<ThreadToken> allTokens = new java.util.ArrayList<>(registry.getAllTokens());
        allTokens.sort((t1, t2) -> {
            int roleCompare = t1.getRole().compareTo(t2.getRole());
            if (roleCompare != 0) return roleCompare;
            return Integer.compare(t1.getInstanceId(), t2.getInstanceId());
        });
        
        // Print status for each thread
        for (ThreadToken token : allTokens) {
            Thread thread = registry.getThread(token);
            if (thread != null && thread.isAlive()) {
                boolean canProceed = sim.canThreadProceed(token);
                String status = canProceed ? "✅ Yes" : "❌ No";
                String threadName = String.format("%-30s", token.getUniqueId());
                System.out.printf("║ %s ║ %-16s ║%n", threadName, status);
            }
        }
        
        System.out.println("╚════════════════════════════════╩═══════════════════╝");
        System.out.println();
    }
    
    /**
     * Set whether to release all gates when fuzzer data is exhausted.
     * This is a public setter to allow tests to configure the behavior.
     * 
     * @param release If true, threads are released for free execution after data exhaustion.
     *                If false (default), fuzzing terminates gracefully with completion message.
     */
    public static void setReleaseGatesOnDataExhaustion(boolean release) {
        releaseGatesOnDataExhaustion = release;
    }
}
