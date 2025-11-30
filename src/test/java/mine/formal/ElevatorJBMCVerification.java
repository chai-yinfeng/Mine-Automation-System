package mine.formal;

import mine.Elevator;
import org.cprover.CProver;

/**
 * Lightweight JBMC harness for the Elevator module.
 *
 * Scope:
 *   We *only* verify the behaviour of operate(), i.e. the movement of
 *   the elevator between "top" and "bottom".
 *
 * Rationale:
 *   Methods arrive/depart/deliver/collect/operateEmpty contain blocking
 *   loops with wait()/notifyAll(). They are better exercised by fuzzing.
 *   Here we focus on the core safety property:
 *
 *   - The elevator position is always either "top" or "bottom".
 *   - Each call to operate() toggles the position.
 *   - operate() does not introduce a cart into the elevator
 *     (starting from an empty elevator and never touching cart).
 */
public class ElevatorJBMCVerification {

    public static void main(String[] args) {
        Elevator e = new Elevator();

        // Initial state should be at top and empty (matches implementation).
        assert e.isAtTop();
        assert !e.hasCart();
        // exactly one of them is true
        assert e.isAtTop() ^ e.isAtBottom();

        // JBMC chooses how many times we call operate().
        int steps = CProver.nondetInt();
        CProver.assume(0 <= steps && steps <= 4);

        for (int i = 0; i < steps; i++) {
            boolean wasTop = e.isAtTop();
            boolean wasBottom = e.isAtBottom();

            // Sanity: before operate(), position is consistent.
            assert wasTop ^ wasBottom;

            e.operate();

            // After operate(), position must still be either top or bottom.
            assert e.isAtTop() || e.isAtBottom();

            // And it must flip.
            if (wasTop) {
                assert e.isAtBottom();
            }
            if (wasBottom) {
                assert e.isAtTop();
            }

            // We never touch the cart in this harness; operate() itself
            // must not magically create or remove a cart.
            assert !e.hasCart();
        }
    }
}
