package mine;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;

public class StationEngineFuzz {

    // Jazzer 固定入口签名：public static void fuzzerTestOneInput(FuzzedDataProvider data)
    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        int nStations = data.consumeInt(1, 5);          // 随机几个站
        int nSteps    = data.consumeInt(0, 50);         // 执行多少步

        // 创建 n 个 station（根据你自己的构造函数改）
        Station[] stations = new Station[nStations];
        for (int i = 0; i < nStations; i++) {
            stations[i] = new Station(i);
        }

        // 创建一个 elevator 和几条 engine（你可以根据自己的 API 改）
        Elevator elevator = new Elevator();
        Engine[] engines = new Engine[nStations - 1];
        for (int i = 0; i < nStations - 1; i++) {
            engines[i] = new Engine(stations[i], stations[i + 1]);
        }

        // 根据 fuzz 输入随机执行不同操作
        for (int step = 0; step < nSteps; step++) {
            int op = data.consumeInt(0, 3);

            switch (op) {
                case 0:
                    // 在随机 station 创建一个 cart
                    int sid = data.consumeInt(0, nStations - 1);
                    Cart c = new Cart();  // 如果你的 Cart 需要 id，就从 data 取一个
                    stations[sid].addCart(c); // 换成你真正的“放车”方法
                    break;

                case 1:
                    // 随机选一条 engine 触发一次传输
                    if (engines.length > 0) {
                        int eid = data.consumeInt(0, engines.length - 1);
                        engines[eid].transfer();  // 换成真实的方法名，例如 operate()/runStep()
                    }
                    break;

                case 2:
                    // 随机让 elevator 做一次操作
                    elevator.operate();  // 换成你的电梯操作方法
                    break;

                default:
                    break;
            }

            // 这里可以插入一些简单的断言，帮助 Jazzer 优先探索坏情况
            // 例子：所有 station 队列大小不能为负
            for (int i = 0; i < nStations; i++) {
                int size = stations[i].queueSize();  // 用你的 getter 改写
                assert size >= 0;
            }
        }
    }
}
