package lib.bt.interfaces;

// Veri iletimi (RFCOMM/GATT fark etmeksizin) ayrı bir katman
public interface IBluetoothTransport {
    boolean send(byte[] data);
    void onDataReceived(java.util.function.Consumer<byte[]> cb);
    void attachTo(java.util.Optional<Object> lowLevelChannel);
    void detach();
}