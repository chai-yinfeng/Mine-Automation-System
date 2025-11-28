package mine;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

public class MineFuzzTarget {

    /**
     * Fuzz entry point.
     */
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {

        if (data.remainingBytes() < 8) {
            return;
        }

        // 1. Decode fuzz input into timing sequences + run time bound
        long maxRunMs = data.consumeLong(1000, 5000); // total simulation time
        SequencePauseProvider provider = new SequencePauseProvider(data);

        // 2. Install fuzz-driven pause provider
        Params.setPauseProvider(provider);

        // 3. Reset progress counter
        MineProgress.reset();

        // 4. Build and start the simulation
        MineSimulation sim = new MineSimulation();
        sim.startAll();

        // 5. Watch for deadlock / stall
        DeadlockWatcher watcher = new DeadlockWatcher(maxRunMs);
        try {
            watcher.watch();
        } catch (AssertionError e) {
            // Print the fuzzed parameters for debugging
            System.out.println("maxRunMs = " + maxRunMs);
            System.out.println(provider);
            throw e; // Continue throwing to let Jazzer record it as a finding.
        } finally {
            // 6. Always try to stop threads cleanly
            try {
                sim.stopAll();
            } catch (InterruptedException ignored) {}
            Params.resetPauseProvider();
        }
    }
}
