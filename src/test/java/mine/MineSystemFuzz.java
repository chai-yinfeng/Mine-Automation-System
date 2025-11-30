package mine;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

public class MineSystemFuzz {

    @FuzzTest(maxDuration = "20s")
    public void fuzzMine(FuzzedDataProvider data) {
        MineFuzzTarget.fuzzerTestOneInput(data);
    }
}
