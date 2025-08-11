package lib.net;

public interface RequestHandle {
    void cancel();
    boolean isCancelled();
}