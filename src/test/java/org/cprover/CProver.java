package org.cprover;

/**
 * Stub implementation of the JBMC API for compilation.
 * JBMC provides its own semantics for these methods when running verification.
 */
public final class CProver {

    private CProver() {}

    public static int nondetInt() {
        // Arbitrary value, ignored by JBMC's symbolic execution.
        return 0;
    }

    public static void assume(boolean condition) {
        // No-op for compilation; JBMC gives it special meaning.
    }

    public static boolean nondetBoolean() {
        return false;
    }
}

