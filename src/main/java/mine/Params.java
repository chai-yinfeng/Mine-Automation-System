package mine;

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
	
	// the following functions assume uniformly distributed pause durations
	
	// the time between arrival of carts to the mine
	public static long arrivalPause() {
		Random random = new Random();
		return random.nextInt(MAX_ARRIVAL_PAUSE);
	}

	// the time between departure of carts from the mine
	public static long departurePause() {
		Random random = new Random();
		return random.nextInt(MAX_DEPARTURE_PAUSE);
	}

	public static long operatorPause() {
		Random random = new Random();
		return random.nextInt(MAX_ELEVATOR_PAUSE);
	}
	
	public static long minerPause() {
		Random random = new Random();
		return random.nextInt(MAX_MINER_PAUSE);
	}
}

