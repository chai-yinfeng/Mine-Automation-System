package mine.fuzzing;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Run DeadlockWatcher in a background thread and propagate AssertionError
 * back to the main thread by interrupting it and storing the error for later rethrow.
 *
 * Usage (example):
 *   Thread main = Thread.currentThread();
 *   AsyncDeadlockWatcher watcher = new AsyncDeadlockWatcher(MAX_RUN_MS, main);
 *   watcher.start();
 *   try {
 *       // run simulation / fuzzing
 *   } finally {
 *       watcher.stop();
 *       watcher.throwIfDetected(); // rethrow on main thread if deadlock detected
 *   }
 */
public class AsyncDeadlockWatcher {
    private final DeadlockWatcher delegate;
    private final Thread watcherThread;
    private final Thread mainThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile AssertionError detectedError = null;

    public AsyncDeadlockWatcher(long maxRunMs, Thread mainThread) {
        this.delegate = new DeadlockWatcher(maxRunMs);
        this.mainThread = mainThread;
        this.watcherThread = new Thread(this::runWatch, "AsyncDeadlockWatcher");
        this.watcherThread.setDaemon(true);
    }

    private void runWatch() {
        try {
            delegate.watch(); // will throw AssertionError on deadlock detection
        } catch (AssertionError e) {
            // store the error and interrupt the main thread so it can handle/observe it
            detectedError = e;
            if (mainThread != null) {
                mainThread.interrupt();
            }
        } finally {
            running.set(false);
        }
    }

    /**
     * Start background watcher. No-op if already started.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            watcherThread.start();
        }
    }

    /**
     * Stop the background watcher. Attempts to interrupt and join the watcher thread.
     */
    public void stop() {
        if (!running.get()) return;
        watcherThread.interrupt();
        try {
            watcherThread.join(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
        }
    }

    /**
     * If the background watcher detected a deadlock (AssertionError), rethrow it on caller thread.
     */
    public void throwIfDetected() {
        if (detectedError != null) {
            throw detectedError;
        }
    }

    /**
     * Optional: return the captured AssertionError (null if none).
     */
    public AssertionError getDetectedError() {
        return detectedError;
    }
}
