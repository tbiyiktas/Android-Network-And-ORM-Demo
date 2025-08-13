package lib.bt.interfaces;

import java.util.function.Consumer;

// Bluetooth'un genel durumunu yöneten arayüz
public interface IBluetoothStatusManager {
    enum BluetoothStatus {
        ENABLED, DISABLED, ENABLING, DISABLING
    }

    boolean isEnabled();
    void onStatusChanged(Consumer<BluetoothStatus> callback);
    boolean isDevicePaired(IBluetoothDevice device);
}