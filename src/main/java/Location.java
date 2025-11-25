/**
 * The Location is an abstract base class (Station or Elevator) that
 * Carts can be delivered to and collected from.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public abstract class Location {
	
	public abstract Cart collect() 
			throws InterruptedException;
	
	public abstract void deliver(Cart cart) 
			throws InterruptedException;

}
