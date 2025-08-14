package lib.bt.callbacks;

import java.util.List;

import lib.bt.interfaces.IBluetoothDevice;

public interface ScanCallback {
    void onScanStarted();
    void onDeviceFound(IBluetoothDevice device);
    void onScanFinished(List<IBluetoothDevice> devices);
    void onScanFailed(String errorMessage);
}