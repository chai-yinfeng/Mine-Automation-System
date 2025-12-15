package mine.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import mine.Params;

public class MineFuzzTarget {

    /**
     * Fuzz entry point.
     */
    private static final long MAX_RUN_MS = 150000;

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

        // 6. If using gated control, release iterations based on fuzz input
        if (useGating) {
            // Get all registered tokens for precise control
            java.util.List<ThreadToken> allTokens = new java.util.ArrayList<>(registry.getAllTokens());
            
            // Only proceed if there are registered tokens
            if (!allTokens.isEmpty()) {
                // Release a sequence of iterations to explore specific interleavings
                int releaseSteps = data.remainingBytes() > 4 ? data.consumeInt(5, 30) : 10;
                long releaseDelay = data.remainingBytes() > 4 ? data.consumeLong(5, 20) : 10;

                for (int i = 0; i < releaseSteps && data.remainingBytes() > 1; i++) {
                    // Pick a unique token to release (instance-specific control)
                    int tokenIdx = data.consumeInt(0, allTokens.size() - 1);
                    ThreadToken token = allTokens.get(tokenIdx);

                    // Release 1-3 iterations for this specific instance
                    int count = data.remainingBytes() > 1 ? data.consumeInt(1, 3) : 1;
                    controller.releaseIterations(token, count);

                    // Delay between releases to let threads execute (fuzz-controlled)
                    try {
                        Thread.sleep(releaseDelay);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }

        // 7. Watch for deadlock / stall
        DeadlockWatcher watcher = new DeadlockWatcher(MAX_RUN_MS);
        try {
            watcher.watch();
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
            try {
                sim.stopAll();
            } catch (InterruptedException ignored) {}
            Params.resetPauseProvider();
            TokenControllerProvider.reset();
        }
    }
}
