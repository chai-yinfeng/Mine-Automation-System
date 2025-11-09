public class Mine extends Thread {

    protected Station station;

    public Mine(Station station) {
        this.station = station;
    }

    public void run() {
        while (!this.isInterrupted()) {
            try {
                sleep(Params.MINING_TIME);

                // deposit mined gem at station
                this.station.depositGem();

                // pause while next gem is mined
                sleep(Params.minerPause());
            }
            catch (InterruptedException e) {
                this.interrupt();
            }
        }
    }

}
