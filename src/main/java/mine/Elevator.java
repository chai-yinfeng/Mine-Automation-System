package mine;

/**
 * The Elevator transports Carts from above ground to below ground.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Elevator extends Location {

	// the current location of the elevator car
	protected String current = "top";
	
	// the cart currently in the elevator (if any)
	private Cart cart = null;
	
	// Operates the elevator, moving it from the top to the bottom of the shaft.
	public synchronized void operate() {
		
		if ("top".equals(this.current)) {
			this.current = "bottom";
			if (this.cart != null) {
				// [LOGGING] elevator descends with cart
				MineLogger.log("ELEVATOR", "descends with " + this.cart);
			}
			else {
				MineLogger.log("ELEVATOR", "descends (empty)");
			}
		}
		else {
			this.current = "top";
			if (this.cart != null) {
				// [LOGGING] elevator ascends with cart
				MineLogger.log("ELEVATOR", "ascends with " + this.cart);
			}
			else {
				MineLogger.log("ELEVATOR", "ascends (empty)");
			}
		}
		
		notifyAll();
	}

	// Operates the elevator once it is empty.
	public synchronized void operateEmpty() throws InterruptedException {
		
		while (this.cart != null) {
			wait();
		}
		
		this.operate();
		notifyAll();
	}
	
	// Allows the Producer to deliver a Cart to the top of the shaft, once
	// elevator present and empty.
	public synchronized void arrive(Cart cart) throws InterruptedException {
			
		while (this.cart != null || "bottom".equals(this.current)) {
			wait();
		}
		
		this.cart = cart;
		this.operate();
		notifyAll();
		
	}

	// Allows the Producer to collect a Cart from the top of the shaft, once
	// elevator present and not empty.
	public synchronized Cart depart() throws InterruptedException {
		
		while (this.cart == null || "bottom".equals(this.current)) {
			wait();
		}
		
		Cart c = this.cart;
		this.cart = null;
		notifyAll();
			
		return c;		
	}

	// Allows an Engine to collect Cart from the bottom of the Elevator, once
	// present and not empty.
	@Override
	public synchronized Cart collect() throws InterruptedException {
		
		while (this.cart == null || "top".equals(this.current)) {
			wait();
		}
		
		Cart c = this.cart;
		this.cart = null;
		// [LOGGING] cart collected from elevator
		MineLogger.log("ELEVATOR", c + " collected from elevator");
		notifyAll();
		
		return c;
	}

	// Allows an Engine to deliver a Cart to the bottom of the Elevator, once 
	// present and empty.
	@Override
	public synchronized void deliver(Cart cart) throws InterruptedException {
		
		while (this.cart != null || "top".equals(this.current)) {
			wait();
		}
			
		this.cart = cart;
		// [LOGGING] cart delivered to elevator
		MineLogger.log("ELEVATOR", this.cart + " delivered to elevator");
		this.operate();
		notifyAll();
				
	}

}
