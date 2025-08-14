package lib.location;


public interface Cancellable {
    void cancel();
    boolean isCanceled();
}