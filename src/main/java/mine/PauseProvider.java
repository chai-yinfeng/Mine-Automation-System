package mine;

public interface PauseProvider {
    long arrivalPause();
    long departurePause();
    long operatorPause();
    long minerPause();
}
