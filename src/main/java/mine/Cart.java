package mine;

/**
 * The carts that collect gems from the mines.
 * 
 * You should not need to modify this file. If you do modify this file 
 * in your submission, please clearly comment where and why you do so.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Cart {

	// a unique identifier for this cart
	protected int id;
	
	// the identifier for the next created cart
	private static int nextId = 1;
	
	// the number of gems carried by a cart
	protected int gems;
	
	private Cart(int id) {
		this.id = id;
		this.gems = 0;
	}
	
	// create a new cart with a unique identifier
	public static Cart getNewCart() {
		return new Cart(nextId++);
	}
	
	public String toString() {
		return "cart [" + id + ": " + gems + "]";
	}

}
