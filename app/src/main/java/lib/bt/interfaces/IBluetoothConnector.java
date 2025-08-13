package lib.bt.interfaces;

// Bağlantı sadece bağlantı kurma/koparma ve bağlantı durumu ile ilgilenir
public interface IBluetoothConnector {
    boolean connect(String deviceAddress);
    boolean disconnect();
    boolean isConnected();
    void onConnectionStatusChanged(java.util.function.Consumer<ConnectionStatus> cb);

    enum ConnectionStatus { CONNECTING, CONNECTED, DISCONNECTED, FAILED }
}