package mine;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

/**
 * Jazzer fuzz target for Station.
 *
 * The fuzzer generates a sequence of operations:
 *   - deliver() a new cart (when there is no cart)
 *   - depositGem() (when there is no gem)
 *   - collect() (when there is both a cart and a gem)
 *
 * Whenever collect() is actually performed, we assert that:
 *   - the returned cart carries at least one gem
 */
public class StationFuzz {

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        Station s = new Station(0);

        // 控制一下长度，避免太长
        int steps = data.consumeInt(0, 50);

        for (int i = 0; i < steps; i++) {
            int op = data.consumeInt(0, 2); // 0:deliver, 1:depositGem, 2:collect

            try {
                switch (op) {
                    case 0:
                        // deliver: only if no cart is present
                        if (!s.hasCart()) {
                            Cart c = Cart.getNewCart();
                            s.deliver(c);
                        }
                        break;

                    case 1:
                        // depositGem: only if no gem is present
                        if (!s.hasGem()) {
                            s.depositGem();
                        }
                        break;

                    case 2:
                        // collect: only if both cart and gem present
                        if (s.hasCart() && s.hasGem()) {
                            Cart cOut = s.collect();
                            // Property: cart returned from collect must have at least one gem
                            assert cOut.gems > 0;
                        }
                        break;

                    default:
                        // nothing
                        break;
                }
            } catch (InterruptedException e) {
                // Station methods can throw InterruptedException; in fuzzing we just stop this input
                return;
            }
        }
    }
}
