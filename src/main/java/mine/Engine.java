package mine;

/**
 * Engines transport Carts between Locations, including Stations and the bottom
 * of the Elevator.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Engine extends Thread {

	// the engine's origin location (elevator or station)
	protected Location origin;
	
	// the engine's destination location (elevator or station)
	protected Location destination;
	
	public Engine(Location origin, Location destination) {
		this.origin = origin;
		this.destination = destination;
	}
	
	public void run() {
		while (!this.isInterrupted()) {
			try {
				// [FUZZING-HOOK] Allow token-based control of loop iteration
				mine.fuzzing.ThreadToken token = mine.fuzzing.TokenControllerProvider.getRegistry().getCurrentThreadToken();
				mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(token);
				
				// [LOGGING] loop iteration start
				if (token != null) {
					MineLogger.log("ENGINE", "iteration start [" + token.getUniqueId() + "]");
				}
				
				// collect a cart from the origin
				Cart cart = this.origin.collect();
				
				// wait for the duration of the journey
				sleep(Params.ENGINE_TIME);
				
				// deliver a cart to the destination
				this.destination.deliver(cart);
			}
			catch (InterruptedException e) {
				this.interrupt();
			}
		}
	}

	// --- [FUZZING] Methods to check if this engine can make progress ---

	/**
	 * Returns true if this engine can make progress (collect from origin and deliver to destination).
	 * An engine can proceed if it can collect from its origin AND deliver to its destination.
	 */
	public boolean canProceed() {
		if (origin instanceof Elevator) {
			Elevator elev = (Elevator) origin;
			// Collecting from bottom of elevator
			if (!elev.canCollectFromBottom()) {
				return false;
			}
		} else if (origin instanceof Station) {
			Station station = (Station) origin;
			if (!station.canCollect()) {
				return false;
			}
		}

		if (destination instanceof Elevator) {
			Elevator elev = (Elevator) destination;
			// Delivering to bottom of elevator
			if (!elev.canDeliverToBottom()) {
				return false;
			}
		} else if (destination instanceof Station) {
			Station station = (Station) destination;
			if (!station.canDeliver()) {
				return false;
			}
		}

		return true;
	}

	public Location getOrigin() {
		return origin;
	}

	public Location getDestination() {
		return destination;
	}
}
