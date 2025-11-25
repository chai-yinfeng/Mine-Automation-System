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
		
		if (this.current == "top") {
			this.current = "bottom";
			if (this.cart != null) {
				System.out.println("elevator descends with " + this.cart.toString());				
			}
			else {
				System.out.println("elevator descends (empty)");
			}
		}
		else {
			this.current = "top";
			if (this.cart != null) {
				System.out.println("elevator ascends with " + this.cart.toString());				
			}
			else {
				System.out.println("elevator ascends (empty)");
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
			
		while (this.cart != null || this.current == "bottom") {
			wait();
		}
		
		this.cart = cart;
		this.operate();
		notifyAll();
		
	}

	// Allows the Producert to collect a Cart from the top of the shaft, once
	// elevator present and not empty.
	public synchronized Cart depart() throws InterruptedException {
		
		while (this.cart == null || this.current == "bottom") {
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
		
		while (this.cart == null || this.current == "top") {
			wait();
		}
		
		Cart c = this.cart;
		this.cart = null;
		System.out.println(c.toString() + " collected from elevator");
		notifyAll();
		
		return c;
	}

	// Allows an Engine to deliver a Cart to the bottom of the Elevator, once 
	// present and empty.
	@Override
	public synchronized void deliver(Cart cart) throws InterruptedException {
		
		while (this.cart != null || this.current == "top") {
			wait();
		}
			
		this.cart = cart;
		System.out.println(this.cart.toString() + " delivered to elevator");
		this.operate();
		notifyAll();
				
	}

}
