package mine;

/**
 * JBMC stub logger: no I/O, no assertions.
 * Only used during bounded model checking.
 */
public final class MineLogger {

    private MineLogger() {}

    public static void log(String component, String message) {
        // no-op: ignore logging in formal verification
    }
}