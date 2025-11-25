package mine;

/**
 * Miners mine for gems, which the deposit at Stations.
 *
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Miner extends Thread {

    protected Station station;

    public Miner(Station station) {
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

