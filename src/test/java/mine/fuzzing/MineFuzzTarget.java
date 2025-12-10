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

        // 3. Use fuzz input to decide which threads to start, and in what order
        int threadCount = sim.threadCount();
        if (threadCount == 0) {
            return;
        }

        // Bound how many "start steps" we take from the input
        int steps = data.consumeInt(1, 4 * threadCount);
        for (int i = 0; i < steps && data.remainingBytes() > 0; i++) {
            int idx = data.consumeInt(0, threadCount - 1);
            sim.startThread(idx);
        }

        // Optionally: ensure at least producer/consumer are started
        // sim.startThread(0); // producer
        // sim.startThread(1); // consumer

        // Optionally: start all remaining threads to get a "full" system after fuzzed prefix
        sim.startAllRemaining();

        // 4. Watch for deadlock / stall
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
