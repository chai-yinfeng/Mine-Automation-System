package mine.formal;

import mine.Cart;
import org.cprover.CProver;

/**
 * JBMC harness for verifying basic properties of Cart.getNewCart().
 *
 * Properties checked:
 *  - getNewCart() never returns null.
 *  - every new cart starts with 0 gems.
 *  - cart IDs are positive.
 *  - within a single execution, IDs returned by getNewCart() are strictly increasing.
 *
 * Note: We do not model any gem updates here; those are handled in the
 * Station verification harness where collect() increments the gem count.
 */
public class CartJBMCVerification {

    public static void main(String[] args) {
        // JBMC chooses how many carts to create in this scenario.
        int n = CProver.nondetInt();
        CProver.assume(0 <= n && n <= 3); // small bound for unwinding

        int lastId = 0;

        for (int i = 0; i < n; i++) {
            Cart c = Cart.getNewCart();

            // 1) getNewCart() must never return null.
            assert c != null;

            // 2) Every new cart starts with zero gems.
            assert c.getGems() == 0;

            // 3) ID must be positive and strictly increasing within this run.
            int id = c.getId();
            assert id > 0;
            assert id > lastId;

            lastId = id;
        }
    }
}

