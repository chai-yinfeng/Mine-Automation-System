package mine.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import mine.Params;

public class MineFuzzTarget {

    /**
     * Fuzz entry point.
     */
    private static final long MAX_RUN_MS = 15000;

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

        if (data.remainingBytes() < 8) {
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
        
        // 4. Optionally use token-driven thread selection
        // This demonstrates how tokens enable role-based fuzzing
        boolean useTokenControl = data.remainingBytes() > 1 && data.consumeBoolean();
        
        if (useTokenControl && data.remainingBytes() > 4) {
            // Token-driven approach: select threads by role and instance
            int numStarts = data.consumeInt(1, sim.threadCount());
            for (int i = 0; i < numStarts && data.remainingBytes() > 1; i++) {
                // Pick a role
                int roleIdx = data.consumeInt(0, ThreadToken.Role.values().length - 1);
                ThreadToken.Role role = ThreadToken.Role.values()[roleIdx];
                
                // Pick an instance of that role
                int instanceId = data.consumeInt(0, 3); // max 4 instances of any role
                ThreadToken token = new ThreadToken(role, instanceId);
                Thread t = registry.getThread(token);
                if (t != null) {
                    // Find the thread index and start it
                    Thread[] allThreads = sim.getAllThreads();
                    for (int j = 0; j < allThreads.length; j++) {
                        if (allThreads[j] == t) {
                            sim.startThread(j);
                            break;
                        }
                    }
                }
            }
        } else {
            // Original index-based approach
            int threadCount = sim.threadCount();
            if (threadCount == 0) {
                return;
            }
            
            int steps = data.consumeInt(1, 4 * threadCount);
            for (int i = 0; i < steps && data.remainingBytes() > 0; i++) {
                int idx = data.consumeInt(0, threadCount - 1);
                sim.startThread(idx);
            }
        }

        // Start all remaining threads to get a "full" system after fuzzed prefix
        sim.startAllRemaining();

        // 5. Watch for deadlock / stall
        DeadlockWatcher watcher = new DeadlockWatcher(MAX_RUN_MS);
        try {
            watcher.watch();
        } catch (AssertionError e) {
            System.out.println("maxRunMs = " + MAX_RUN_MS);
            System.out.println(provider);
            throw e;
        } finally {
            try {
                sim.stopAll();
            } catch (InterruptedException ignored) {}
            Params.resetPauseProvider();
        }
    }
}
