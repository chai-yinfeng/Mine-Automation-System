package mine.fuzzing;

import mine.*;

public class MineSimulation {

    private final Producer producer;
    private final Consumer consumer;
    private final Operator operator;
    private final Miner[] miners;
    private final Engine[] engines;
    private final Engine firstEngine;
    private final Engine lastEngine;

    // flat view for fuzzing / scheduling
    private final Thread[] threads;
    private final boolean[] started;

//    public MineSimulation() {
//        int n = Params.STATIONS;
//
//        Elevator elevator = new Elevator();
//        Station[] station = new Station[n];
//        for (int i = 0; i < n; i++) {
//            station[i] = new Station(i);
//        }
//
//        producer = new Producer(elevator);
//        consumer = new Consumer(elevator);
//        operator = new Operator(elevator);
//
//        miners = new Miner[n];
//        for (int i = 0; i < n; i++) {
//            miners[i] = new Miner(station[i]);
//        }
//
//        engines = new Engine[n - 1];
//        for (int i = 0; i < n - 1; i++) {
//            engines[i] = new Engine(station[i], station[i + 1]);
//        }
//
//        firstEngine = new Engine(elevator, station[0]);
//        lastEngine = new Engine(station[n - 1], elevator);
//    }

    public MineSimulation() {
        int n = Params.STATIONS;

        Elevator elevator = new Elevator();
        Station[] station = new Station[n];
        for (int i = 0; i < n; i++) {
            station[i] = new Station(i);
        }

        producer = new Producer(elevator);
        consumer = new Consumer(elevator);
        operator = new Operator(elevator);

        miners = new Miner[n];
        for (int i = 0; i < n; i++) {
            miners[i] = new Miner(station[i]);
        }

        engines = new Engine[n - 1];
        for (int i = 0; i < n - 1; i++) {
            engines[i] = new Engine(station[i], station[i + 1]);
        }

        firstEngine = new Engine(elevator, station[0]);
        lastEngine = new Engine(station[n - 1], elevator);

        // Flatten all worker threads into a single array
        int total = 3 /* producer, consumer, operator */
                + miners.length
                + engines.length
                + 2; /* firstEngine, lastEngine */

        threads = new Thread[total];
        int idx = 0;
        threads[idx++] = producer;
        threads[idx++] = consumer;
        threads[idx++] = operator;
        for (Miner m : miners) threads[idx++] = m;
        for (Engine e : engines) threads[idx++] = e;
        threads[idx++] = firstEngine;
        threads[idx++] = lastEngine;

        started = new boolean[total];
    }

    public int threadCount() {
        return threads.length;
    }

    /**
     * Start a single thread if it has not been started yet.
     */
    public void startThread(int idx) {
        if (idx < 0 || idx >= threads.length) {
            return;
        }
        if (!started[idx]) {
            threads[idx].start();
            started[idx] = true;
        }
    }

    /**
     * Optional helper: start all remaining threads.
     */
    public void startAllRemaining() {
        for (int i = 0; i < threads.length; i++) {
            if (!started[i]) {
                threads[i].start();
                started[i] = true;
            }
        }
    }

    public void startAll() {
        for (Miner m : miners) m.start();
        for (Engine e : engines) e.start();
        firstEngine.start();
        lastEngine.start();
        producer.start();
        consumer.start();
        operator.start();
    }

    public void stopAll() throws InterruptedException {
        // Interrupt all threads
        producer.interrupt();
        consumer.interrupt();
        operator.interrupt();
        firstEngine.interrupt();
        lastEngine.interrupt();
        for (Engine e : engines) e.interrupt();
        for (Miner m : miners) m.interrupt();

        // Join with timeout to avoid hanging the fuzzer
        joinQuiet(producer);
        joinQuiet(consumer);
        joinQuiet(operator);
        joinQuiet(firstEngine);
        joinQuiet(lastEngine);
        for (Engine e : engines) joinQuiet(e);
        for (Miner m : miners) joinQuiet(m);
    }

    private void joinQuiet(Thread t) throws InterruptedException {
        t.join(2000);
    }
}
