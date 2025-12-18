package mine.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import java.nio.ByteBuffer;
import java.util.Random;

public class SimpleFuzzerTest {

    // 简单的Mock类
    static class SimpleMockProvider implements FuzzedDataProvider {
        private final byte[] data;
        private int pos = 0;

        public SimpleMockProvider(byte[] data) {
            this.data = data;
        }

        @Override
        public boolean consumeBoolean() {
            return pos < data.length && (data[pos++] & 1) == 1;
        }

        @Override
        public boolean[] consumeBooleans(int maxLength) {
            return new boolean[0];
        }

        @Override
        public byte consumeByte() {
            return 0;
        }

        @Override
        public int consumeInt(int min, int max) {
            if (pos + 4 > data.length) return min;
            int val = ByteBuffer.wrap(data, pos, 4).getInt();
            pos += 4;
            long range = (long) max - min + 1;
            return (int) (min + (Math.abs((long) val) % range));
        }

        @Override
        public long consumeLong(long min, long max) {
            if (pos + 8 > data.length) return min;
            long val = ByteBuffer.wrap(data, pos, 8).getLong();
            pos += 8;
            long range = max - min + 1;
            return min + (Math.abs(val) % range);
        }

        @Override
        public int remainingBytes() {
            return Math.max(0, data.length - pos);
        }

        // 其他方法使用默认实现
        @Override public byte consumeByte(byte min, byte max) { return min; }
        @Override public short consumeShort(short min, short max) { return min; }

        @Override
        public short[] consumeShorts(int maxLength) {
            return new short[0];
        }

        @Override public int consumeInt() { return consumeInt(Integer.MIN_VALUE, Integer.MAX_VALUE); }
        @Override public long consumeLong() { return consumeLong(Long.MIN_VALUE, Long.MAX_VALUE); }
        @Override public float consumeFloat() { return 0f; }
        @Override public double consumeDouble() { return 0d; }
        @Override public float consumeRegularFloat() { return 0f; }
        @Override public float consumeRegularFloat(float min, float max) { return min; }
        @Override public double consumeRegularDouble() { return 0d; }
        @Override public double consumeRegularDouble(double min, double max) { return min; }
        @Override public float consumeProbabilityFloat() { return 0f; }
        @Override public double consumeProbabilityDouble() { return 0d; }
        @Override public char consumeChar() { return 'a'; }
        @Override public char consumeChar(char min, char max) { return min; }
        @Override public char consumeCharNoSurrogates() { return 'a'; }
        @Override public String consumeAsciiString(int maxLength) { return ""; }
        @Override public String consumeString(int maxLength) { return ""; }
        @Override public String consumeRemainingAsAsciiString() { return ""; }
        @Override public String consumeRemainingAsString() { return ""; }
        @Override public byte[] consumeBytes(int length) {
            int len = Math.min(length, remainingBytes());
            byte[] result = new byte[len];
            System.arraycopy(data, pos, result, 0, len);
            pos += len;
            return result;
        }
        @Override public byte[] consumeRemainingAsBytes() { return consumeBytes(remainingBytes()); }

        @Override
        public short consumeShort() {
            return 0;
        }

        @Override public int[] consumeInts(int maxLength) { return new int[0]; }
        @Override public long[] consumeLongs(int maxLength) { return new long[0]; }
    }

    public static void main(String[] args) {
        System.out.println("=== Simple Fuzzer Parse Test ===\n");

        // 生成一个长数据序列（5000字节）
        byte[] testData = generateTestData(50000);

        System.out.println("Generated test data: " + testData.length + " bytes");
        System.out.println("\nCreating mock provider and testing parse...\n");

        try {
            SimpleMockProvider provider = new SimpleMockProvider(testData);

            // 只分析解析逻辑，不实际运行
//            analyzeParseLogic(provider);

            // 如果要实际运行fuzzer，取消下面的注释
             MineFuzzTarget.fuzzerTestOneInput(provider);

            System.out.println("\n✓ Parse test completed successfully");

        } catch (Exception e) {
            System.err.println("\n✗ Parse test failed:");
            e.printStackTrace();
        }
    }

    // 生成测试数据
    private static byte[] generateTestData(int size) {
        Random rand = new Random(42);
        byte[] data = new byte[size];
        rand.nextBytes(data);
        return data;
    }

    // 分析解析逻辑（不实际运行）
    private static void analyzeParseLogic(SimpleMockProvider provider) {
        System.out.println("Initial remaining bytes: " + provider.remainingBytes());

        if (provider.remainingBytes() < 200) {
            System.out.println("❌ Would exit early (< 200 bytes)");
            return;
        }
        System.out.println("✓ Pass minimum data check (>= 200 bytes)");

        System.out.println("\n--- Gating Control Parameters ---");
        int releaseSteps = provider.consumeInt(5, 30);
        long releaseDelay = provider.consumeLong(5, 20);
        System.out.println("Release steps: " + releaseSteps);
        System.out.println("Release delay: " + releaseDelay + "ms");

        System.out.println("\n--- First 5 Iteration Releases ---");
        for (int i = 0; i < Math.min(5, releaseSteps); i++) {
            int roleIdx = provider.consumeInt(0, 5);
            int count = provider.consumeInt(1, 3);
            System.out.println("Step " + i + ": Role=" + roleIdx + ", Count=" + count);
        }

        System.out.println("\nRemaining bytes after parse: " + provider.remainingBytes());
    }
}
