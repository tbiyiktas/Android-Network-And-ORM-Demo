package lib.bt.interfaces;

import java.util.function.Consumer;

// Cihaz bağlantısı ve veri alışverişini yöneten arayüz
public interface IBluetoothConnector_old {
    boolean isConnected();
    boolean connect(String deviceAddress);
    boolean disconnect();
    boolean sendData(byte[] data);
    void onDataReceived(Consumer<byte[]> callback);

    void pairDevice(IBluetoothDevice device);
    void cancelPairing();
    IBluetoothDevice getDeviceFromAddress(String deviceAddress);

    void onConnectionStatusChanged(Consumer<ConnectionStatus> callback);

    class ConnectionStatus {
        private final IBluetoothDevice device;
        private final boolean isConnected;
        private final String errorMessage;

        public ConnectionStatus(IBluetoothDevice device, boolean isConnected, String errorMessage) {
            this.device = device;
            this.isConnected = isConnected;
            this.errorMessage = errorMessage;
        }

        public IBluetoothDevice getDevice() {
            return device;
        }

        public boolean isConnected() {
            return isConnected;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}