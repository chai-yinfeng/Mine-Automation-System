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
				mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(
					mine.fuzzing.TokenControllerProvider.getRegistry().getCurrentThreadToken());
				
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

}
