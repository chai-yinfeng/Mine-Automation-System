package mine;

/**
 * The consumer class is responsible for disposing of carts once they 
 * have completed their visit to the mines.
 * 
 * You should not need to modify this file. If you do modify this file 
 * in your submission, please clearly comment where and why you do so.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Consumer extends Thread {

	// the elevator that carts are taken from
	private Elevator elevator;
	
	// create a new consumer
	public Consumer(Elevator elevator) {
		this.elevator = elevator;
	}

	// carts are removed from the elevator at random intervals
	public void run() {
		while(!this.isInterrupted()) {
			try {
				// remove a cart from the elevator
				Cart c = this.elevator.depart();
				System.out.println(c.toString() + " departs from mine");
				
				// pause before removing a further cart
				sleep(Params.departurePause());
			}
			catch (InterruptedException e) {
				this.interrupt();
			}
		}
	}
}
