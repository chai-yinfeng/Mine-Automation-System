package mine.formal;

import mine.Cart;
import mine.Elevator;
import org.cprover.CProver;

/**
 * JBMC harness for verifying the behaviour of the Elevator module.
 *
 * We model a sequential, well-behaved client that calls:
 *   - operate()
 *   - operateEmpty()
 *   - arrive(Cart)
 *   - depart()
 *   - deliver(Cart)
 *   - collect()
 *
 * Protocol assumptions:
 *   - operateEmpty() is only called when the elevator has no cart.
 *   - arrive(c) is only called when the elevator is at the top and empty.
 *   - depart() is only called when the elevator is at the top and has a cart.
 *   - deliver(c) is only called when the elevator is at the bottom and empty.
 *   - collect() is only called when the elevator is at the bottom and has a cart.
 *
 * Under these assumptions, we check that:
 *   - positions are always either "top" or "bottom";
 *   - depart() and collect() never return null and always empty the elevator;
 *   - a cart delivered/arrived on one side is the same cart returned on departure/collect.
 */
public class ElevatorJBMCCrash {

    public static void main(String[] args) throws Exception {
        Elevator e = new Elevator();

        // Carts that we may use from the top and bottom side.
        Cart topCart = null;
        Cart bottomCart = null;

        // JBMC chooses how many steps to execute.
        int steps = CProver.nondetInt();
        CProver.assume(0 <= steps && steps <= 4);

        for (int i = 0; i < steps; i++) {
            int op = CProver.nondetInt();
            // 0: operateEmpty, 1: operate, 2: arrive, 3: depart, 4: deliver, 5: collect
            CProver.assume(0 <= op && op <= 5);

            switch (op) {
                case 0 -> {
                    // operateEmpty: only when there is no cart
                    if (!e.hasCart()) {
                        e.operateEmpty();

                        // After operateEmpty, elevator is still empty.
                        assert !e.hasCart();
                    }
                }

                case 1 -> {
                    // operate: always safe, just toggles position.
                    boolean wasTop = e.isAtTop();
                    boolean wasBottom = e.isAtBottom();

                    e.operate();

                    // Position must flip between top and bottom.
                    if (wasTop) {
                        assert e.isAtBottom();
                    }
                    if (wasBottom) {
                        assert e.isAtTop();
                    }
                }

                case 2 -> {
                    // arrive(c): elevator must be at top and empty
                    if (e.isAtTop() && !e.hasCart()) {
                        if (topCart == null) {
                            topCart = Cart.getNewCart();
                        }
                        e.arrive(topCart);

                        // arrive() calls operate(), so elevator should now be at bottom with a cart.
                        assert e.isAtBottom();
                        assert e.hasCart();
                    }
                }

                case 3 -> {
                    // depart(): elevator must be at top and have a cart
                    if (e.isAtTop() && e.hasCart()) {
                        Cart out = e.depart();

                        // depart returns a non-null cart and empties the elevator.
                        assert out != null;
                        assert !e.hasCart();
                        assert e.isAtTop();

                        // If we used topCart before, we expect to get it back.
                        if (topCart != null) {
                            assert out == topCart;
                        }
                    }
                }

                case 4 -> {
                    // deliver(c): elevator must be at bottom and empty
                    if (e.isAtBottom() && !e.hasCart()) {
                        if (bottomCart == null) {
                            bottomCart = Cart.getNewCart();
                        }
                        e.deliver(bottomCart);

                        // deliver() calls operate(), so elevator should now be at top with a cart.
                        assert e.isAtTop();
                        assert e.hasCart();
                    }
                }

                case 5 -> {
                    // collect(): elevator must be at bottom and have a cart
                    if (e.isAtBottom() && e.hasCart()) {
                        Cart out = e.collect();

                        // collect returns a non-null cart and empties the elevator.
                        assert out != null;
                        assert !e.hasCart();
                        assert e.isAtBottom();

                        // If we used bottomCart before, we expect to get it back.
                        if (bottomCart != null) {
                            assert out == bottomCart;
                        }
                    }
                }

                default -> {
                    assert false : "Unreachable operation code";
                }
            }

            // Global invariant: position must always be either top or bottom.
            assert e.isAtTop() || e.isAtBottom();
        }
    }
}