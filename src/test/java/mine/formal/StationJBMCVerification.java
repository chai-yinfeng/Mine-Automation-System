package mine.formal;

import mine.Cart;
import mine.Station;
import org.cprover.CProver;

/**
 * JBMC harness for verifying the behaviour of a single Station.
 *
 * The harness explores many *valid* finite sequences of operations:
 *   - depositGem()
 *   - deliver(Cart)
 *   - collect()
 *
 * Protocol assumptions (client-side):
 *   - depositGem() is only called when the station currently has no gem.
 *   - deliver(cart) is only called when there is no cart at the station.
 *   - collect() is only called when there is both a cart and a gem.
 *
 * Under these assumptions, JBMC checks that:
 *   - collect() always returns the same cart instance that was delivered.
 *   - exactly one gem is added to that cart during a full cycle.
 *   - after collect(), the station is empty (no cart, no gem).
 *   - gem counts on carts never become negative.
 *
 * Note: We do not start any threads here. All methods are called
 * sequentially, so the while+wait loops in Station are never taken,
 * because we only call operations when their preconditions hold.
 */
public class StationJBMCVerification {

    public static void main(String[] args) throws Exception {
        // Single station under verification
        Station station = new Station(0);

        // The cart that we deliver to the station (if any)
        Cart trackedCart = null;
        int trackedInitialGems = 0;

        // JBMC chooses how many operations to execute in this scenario
        int steps = CProver.nondetInt();
        CProver.assume(0 <= steps && steps <= 3);

        for (int i = 0; i < steps; i++) {
            // JBMC chooses which operation to attempt at each step
            int op = CProver.nondetInt();
            CProver.assume(0 <= op && op <= 2); // 0: deposit, 1: deliver, 2: collect

            switch (op) {
                case 0 -> {
                    // depositGem: only when the station currently has no gem
                    if (!station.hasGem()) {
                        station.depositGem();
                    }
                    // If station already has a gem, this operation is skipped
                }

                case 1 -> {
                    // deliver: only when there is no cart at the station
                    if (!station.hasCart()) {
                        // Decide non-deterministically whether to reuse an existing tracked cart
                        // or to allocate a fresh one.
                        boolean reuse = CProver.nondetBoolean();
                        if (trackedCart == null || !reuse) {
                            trackedCart = Cart.getNewCart();
                            trackedInitialGems = trackedCart.getGems();
                        }

                        station.deliver(trackedCart);
                    }
                    // If a cart is already present, this operation is skipped
                }

                case 2 -> {
                    // collect: only when there is a cart AND a gem at the station
                    if (station.hasCart() && station.hasGem()) {
                        Cart out = station.collect();

                        // 1) The same cart instance must be returned.
                        assert out == trackedCart;

                        // 2) Exactly one gem must have been added during this cycle.
                        assert out.getGems() == trackedInitialGems + 1;

                        // 3) After collect(), the station must be empty again.
                        assert !station.hasCart();
                        assert !station.hasGem();
                    }
                    // Otherwise, client would not call collect() and we skip the operation.
                }

                default -> {
                    // This should be unreachable because of the assume above.
                    assert false : "Unreachable operation code";
                }
            }

            // Basic safety invariant: gem count on any tracked cart must never be negative.
            if (trackedCart != null) {
                assert trackedCart.getGems() >= 0;
            }
        }
    }
}
