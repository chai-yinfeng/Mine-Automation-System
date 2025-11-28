package mine;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

public class StationFuzz {

    @FuzzTest(maxDuration = "20s")
    public void fuzzStation(FuzzedDataProvider data) {
//        Station s = new Station(0);
//        int steps = data.consumeInt(0, 50);
//
//        for (int i = 0; i < steps; i++) {
//            int op = data.consumeInt(0, 2);
//
//            try {
//                switch (op) {
//                    case 0 -> {
//                        if (!s.hasCart()) {
//                            Cart c = Cart.getNewCart();
//                            s.deliver(c);
//                        }
//                    }
//                    case 1 -> {
//                        if (!s.hasGem()) {
//                            s.depositGem();
//                        }
//                    }
//                    case 2 -> {
//                        if (s.hasCart() && s.hasGem()) {
//                            Cart cOut = s.collect();
//                            assert cOut.gems > 0;
//                        }
//                    }
//                }
//            } catch (InterruptedException e) {
//                return;
//            }
//        }
        MineFuzzTarget.fuzzerTestOneInput(data);
    }
}
