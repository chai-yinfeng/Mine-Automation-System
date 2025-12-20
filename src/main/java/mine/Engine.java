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

    private volatile boolean inMid = false;
	
	public Engine(Location origin, Location destination) {
		this.origin = origin;
		this.destination = destination;
        this.inMid = false;
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

                this.inMid = true;

                mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(token);
				
				// wait for the duration of the journey
				sleep(Params.ENGINE_TIME);
				
				// deliver a cart to the destination
				this.destination.deliver(cart);

                this.inMid = false;
			}
			catch (InterruptedException e) {
                System.out.println(e);
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
        if (!inMid) {
            return canCollectFrom(origin);
        } else {
            return canDeliverTo(destination);
        }
    }
    private boolean canCollectFrom(Location loc) {
        if (loc instanceof Elevator elev) {
            return elev.canCollectFromBottom();
        } else if (loc instanceof Station station) {
            return station.canCollect();
        } else {
            throw new IllegalStateException("Unknown origin location type: " + loc.getClass());
        }
    }
    private boolean canDeliverTo(Location loc) {
        if (loc instanceof Elevator elev) {
            return elev.canDeliverToBottom();
        } else if (loc instanceof Station station) {
            return station.canDeliver();
        } else {
            throw new IllegalStateException("Unknown destination location type: " + loc.getClass());
        }
    }

	public Location getOrigin() {
		return origin;
	}

	public Location getDestination() {
		return destination;
	}
}
