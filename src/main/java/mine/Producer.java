package mine;

/**
 * The producer class is responsible for creating carts that will visit to the mines.
 * 
 * You should not need to modify this file. If you do modify this file 
 * in your submission, please clearly comment where and why you do so.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Producer extends Thread {

	// the elevator for new carts
	private Elevator elevator;
	
	// create a new producer
	public Producer(Elevator elevator) {
		this.elevator = elevator;
	}

	// carts are sent to the elevator at random intervals
	public void run() {
		while(!this.isInterrupted()) {
			try {
				// [FUZZING-HOOK] Allow token-based control of loop iteration
				mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(
					mine.fuzzing.TokenControllerProvider.getRegistry().getCurrentThreadToken());
				
				// create a new cart and send to elevator
				Cart cart = Cart.getNewCart();
				// [LOGGING] new cart arrives at the mine
				MineLogger.log("PRODUCER", cart + " arrives at the mine");
				this.elevator.arrive(cart);
				
				// pause before sending another cart
				sleep(Params.arrivalPause());
			}
			catch (InterruptedException e) {
				this.interrupt();
			}
		}
	}
}
