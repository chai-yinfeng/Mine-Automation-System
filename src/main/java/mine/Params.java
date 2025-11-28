package mine;

import java.util.Random;

/**
 * A class holding several important parameters governing the behaviour
 * of the mine simulator.
 *
 * You should experiment with different scenarios by varying the values of
 * these parameters, particularly those governing the timing of various events.
 *
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

import java.util.Random;

public class Params {

	// the number of stations in the mine
	public static final int STATIONS = 4;
	
	// the amount of time taken to mine a gem
	public static final long MINING_TIME = 300;

	// the amount of time required to operate the elevator
	public static final long ELEVATOR_TIME = 50;
	
	// the amount of time taken for an engine to transport carts between two locations
	public static final long ENGINE_TIME = 200;
	
	// the maximum amount of time between arrivals
	public static final int MAX_ARRIVAL_PAUSE = 200;

	// the maximum amount of time between departures
	public static final int MAX_DEPARTURE_PAUSE = 800;
	
	// the maximum amount of time the elevator pauses while empty
	public static final int MAX_ELEVATOR_PAUSE = 200;
	
	// the maximum amount of time the miner pauses before producing next gem
	public static final int MAX_MINER_PAUSE = 200;

    private static PauseProvider provider = new RandomPauseProvider();

    public static void setPauseProvider(PauseProvider p) {
        provider = p;
    }
    public static void resetPauseProvider() {
        provider = new RandomPauseProvider();
    }

    // delegate the methods to the provider
    public static long arrivalPause() {
        return provider.arrivalPause();
    }
    public static long departurePause() {
        return provider.departurePause();
    }
    public static long operatorPause() {
        return provider.operatorPause();
    }
    public static long minerPause() {
        return provider.minerPause();
    }

    // random implementation
    private static class RandomPauseProvider implements PauseProvider {
        private final Random r = new Random();
        @Override
        public long arrivalPause() {
            return r.nextInt(MAX_ARRIVAL_PAUSE);
        }
        @Override
        public long departurePause() {
            return r.nextInt(MAX_DEPARTURE_PAUSE);
        }
        @Override
        public long operatorPause() {
            return r.nextInt(MAX_ELEVATOR_PAUSE);
        }
        @Override
        public long minerPause() {
            return r.nextInt(MAX_MINER_PAUSE);
        }
    }
}

