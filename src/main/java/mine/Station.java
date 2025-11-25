package mine;

/**
 * The src.main.java.mine.Station class is a type of src.main.java.mine.Location at which mined gems are stored
 * until they are loaded onto Carts. Carts are delivered to and collected
 * from a station by Enginers.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Station extends Location {

	// the numerical id of the station
	private int id;
	
	// the cart currently at the station (if any)
	private Cart cart;
	
	// gem currently at the station 
	private boolean gem;
	
	// create a new station with specified id
	public Station(int i) {
		this.id = i;
		this.gem = false;
	}

	// Allows an Engine to collect Cart from the Station once loaded with a gem.
	@Override
	public synchronized Cart collect() throws InterruptedException {
		
		// wait while there is no cart at this station
		while(this.cart == null || !this.gem) {
			wait();
		}
		
		Cart c = this.cart;
		
		if (this.gem) {
			c.gems += 1;
			// [LOGGING] cart loaded with a gem at this station
			MineLogger.log("STATION-" + id, c + " loaded with a gem");
			this.gem = false;
		}
		
		this.cart = null;
		// [LOGGING] cart collected from this station
		MineLogger.log("STATION-" + id, c + " collected from " + this);
		notifyAll();
		
		return c;
	}

	// Allows an Engine to deliver a Cart to the Station once there is no other cart.
	@Override
	public synchronized void deliver(Cart cart) throws InterruptedException {

		// wait while there is already a cart at this station
		while(this.cart != null) {
			wait();
		}
		
		this.cart = cart;
		// [LOGGING] cart delivered to this station
		MineLogger.log("STATION-" + id, cart + " delivered to " + this);
		notifyAll();		
	}

	// Allows a miner to deposit a gem at the Station once the previous gem has been taken.
	public synchronized void depositGem() throws InterruptedException {
		
		// wait while the station is full
		while(this.gem) {
			wait();
		}
		
		this.gem = true;
		// Optional: [LOGGING] miner deposits a gen
		// MineLogger.log("STATION-" + id, "gem deposited");
		notifyAll();
	}
	
	public String toString() {
		return "station " + this.id;
	}

}
