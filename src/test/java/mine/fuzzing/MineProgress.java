package mine.fuzzing;

import java.util.concurrent.atomic.AtomicLong;

public class MineProgress {
    private static final AtomicLong counter = new AtomicLong();

    public static void report() {
        counter.incrementAndGet();
    }

    public static long snapshot() {
        return counter.get();
    }

    public static void reset() {
        counter.set(0);
    }
}
