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
				mine.fuzzing.ThreadToken token = mine.fuzzing.TokenControllerProvider.getRegistry().getCurrentThreadToken();
				mine.fuzzing.TokenControllerProvider.getController().onLoopIteration(token);
				
				// [LOGGING] loop iteration start
				if (token != null) {
					MineLogger.log("OPERATOR", "iteration start [" + token.getUniqueId() + "]");
				}
				
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

	// --- [FUZZING] Methods to check if this operator can make progress ---

	/**
	 * Returns true if the operator can proceed (can operate empty elevator).
	 */
	public boolean canProceed() {
		return elevator.canOperateEmpty();
	}

	public Elevator getElevator() {
		return elevator;
	}
}
