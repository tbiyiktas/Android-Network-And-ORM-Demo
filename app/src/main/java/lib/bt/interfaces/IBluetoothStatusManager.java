package lib.bt.interfaces;

import java.util.function.Consumer;

public interface IBluetoothStatusManager {

    enum BluetoothStatus {
        ENABLED, DISABLED, ENABLING, DISABLING
    }

    boolean isEnabled();

    void onStatusChanged(Consumer<BluetoothStatus> callback);

    boolean isDevicePaired(IBluetoothDevice device);
}
