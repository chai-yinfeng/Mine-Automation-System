package mine.fuzzing;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class DeadlockWatcher {

    private final long maxRunMs;

    public DeadlockWatcher(long maxRunMs) {
        this.maxRunMs = maxRunMs;
    }

    public void watch() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long start = System.currentTimeMillis();
        long lastProgress = MineProgress.snapshot();
        long lastProgressTime = start;

        while (true) {
            long[] deadlocked = bean.findDeadlockedThreads();
            if (deadlocked != null) {
                throw new AssertionError("Monitor deadlock detected");
            }

            long now = System.currentTimeMillis();
            long curProgress = MineProgress.snapshot();
            if (curProgress != lastProgress) {
                lastProgress = curProgress;
                lastProgressTime = now;
            }

            // If no progress for a long time, treat as liveness failure
            long noProgressLimit = Math.max(maxRunMs / 2, 5000);
            if (now - lastProgressTime > noProgressLimit) {
                throw new AssertionError("No progress for too long (possible logical deadlock)");
            }

            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
