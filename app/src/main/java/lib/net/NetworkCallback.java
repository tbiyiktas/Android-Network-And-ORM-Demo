package lib.net;

public interface NetworkCallback<T> {
    void onResult(NetResult<T> result);
}