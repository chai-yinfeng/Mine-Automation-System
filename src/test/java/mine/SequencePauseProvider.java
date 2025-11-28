package mine;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PauseProvider that is fully driven by precomputed sequences.
 */
public class SequencePauseProvider implements PauseProvider {

    private final long[] arrivalSeq;
    private final long[] departureSeq;
    private final long[] operatorSeq;
    private final long[] minerSeq;

    private final AtomicInteger arrivalIdx = new AtomicInteger();
    private final AtomicInteger departureIdx = new AtomicInteger();
    private final AtomicInteger operatorIdx = new AtomicInteger();
    private final AtomicInteger minerIdx = new AtomicInteger();

    public SequencePauseProvider(FuzzedDataProvider data) {
        // Limit sequence length to keep state small
        int len = data.consumeInt(1, 64);

        arrivalSeq = consumeArray(data, len, 0, Params.MAX_ARRIVAL_PAUSE);
        departureSeq = consumeArray(data, len, 0, Params.MAX_DEPARTURE_PAUSE);
        operatorSeq = consumeArray(data, len, 0, Params.MAX_ELEVATOR_PAUSE);
        minerSeq = consumeArray(data, len, 0, Params.MAX_MINER_PAUSE);
    }

    private long[] consumeArray(FuzzedDataProvider data, int len, int min, int max) {
        long[] res = new long[len];
        for (int i = 0; i < len; i++) {
            res[i] = data.consumeLong(min, max);
        }
        return res;
    }

    private long pick(long[] seq, AtomicInteger idx, long fallback) {
        int i = idx.getAndIncrement();
        if (i < seq.length) {
            return seq[i];
        }
        // If we run out, keep using the last value
        return fallback;
    }

    @Override
    public long arrivalPause() {
        return pick(arrivalSeq, arrivalIdx,
                arrivalSeq[arrivalSeq.length - 1]);
    }

    @Override
    public long departurePause() {
        return pick(departureSeq, departureIdx,
                departureSeq[departureSeq.length - 1]);
    }

    @Override
    public long operatorPause() {
        return pick(operatorSeq, operatorIdx,
                operatorSeq[operatorSeq.length - 1]);
    }

    @Override
    public long minerPause() {
        return pick(minerSeq, minerIdx,
                minerSeq[minerSeq.length - 1]);
    }
}
