package mine;

/**
 * The Operator periodically raises and lowers the Elevator.
 * 
 * @author ngeard@unimelb.edu.au
 * @date 6 March 2025
 */

public class Operator extends Thread {

	// the elevator managed by the operator
	private Elevator elevator;

	// create a new operator
	public Operator(Elevator elevator) {
		this.elevator = elevator;
	}
	
	public void run() {
		while (!isInterrupted()) {
			try {
				// [FUZZING-HOOK] Allow token-based control of loop iteration
				mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(
					mine.fuzzing.TokenControllerProvider.getRegistry().getCurrentThreadToken());
				
				sleep(Params.ELEVATOR_TIME);
				
				// update the status of the elevator
				this.elevator.operateEmpty();

				// wait before operating the elevator again
				sleep(Params.operatorPause());
			}
			catch (InterruptedException e) {
				this.interrupt();
			}
		}
	}

}
