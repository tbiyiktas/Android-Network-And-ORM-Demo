package lib.bt.callbacks;

import lib.bt.interfaces.IBluetoothDevice;

public interface PairingCallback {
    void onPairingStarted(IBluetoothDevice device);
    void onPairingSuccess(IBluetoothDevice device);
    void onPairingFailed(IBluetoothDevice device, String errorMessage);
}
