package mine;

import mine.fuzzing.MineProgress;

/**
 * JBMC stub logger: no I/O, no assertions.
 * Only used during bounded model checking.
 */
public final class MineLogger {

    private MineLogger() {}

    public static void log(String component, String message) {
        // no-op: ignore logging in formal verification

        // log for mine progresses, for deadlock monitor.
        MineProgress.report();
    }
}