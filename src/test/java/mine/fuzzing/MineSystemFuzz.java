package mine.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

public class MineSystemFuzz {

    @FuzzTest(maxDuration = "10m")
    public void fuzzMine(FuzzedDataProvider data) {
        MineFuzzTarget.fuzzerTestOneInput(data);
    }
}
