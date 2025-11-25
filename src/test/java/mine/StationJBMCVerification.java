package mine;

/**
 * JBMC harness for verifying basic Station + Cart properties.
 *
 * Scenario:
 *   1. depositGem()
 *   2. deliver(cart)
 *   3. collect()
 *
 * Properties:
 *   - collect() returns the same cart that was delivered
 *   - exactly one gem is added to the cart
 *   - after collect(), station has no cart and no gem
 *
 * [MODIFIED - FORMAL VERIFICATION HARNESS]
 */
public class StationJBMCVerification {

    public static void main(String[] args) throws Exception {
        Station s = new Station(0);

        // initial state: no cart, no gem
        assert !s.hasCart();
        assert !s.hasGem();

        // step 1: deposit a gem
        s.depositGem();
        assert !s.hasCart();
        assert s.hasGem();

        // step 2: deliver a new empty cart
        Cart c0 = Cart.getNewCart();
        s.deliver(c0);
        assert s.hasCart();
        assert s.hasGem();

        // step 3: collect the cart with gem
        Cart c1 = s.collect();

        // property 1: same cart instance
        assert c1 == c0;

        // property 2: exactly one gem added (new carts start with 0 gems)
        assert c1.gems == 1;

        // property 3: station is empty again
        assert !s.hasCart();
        assert !s.hasGem();
    }
}
