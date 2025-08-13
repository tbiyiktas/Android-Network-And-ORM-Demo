package lib.bt.callbacks;

import lib.bt.interfaces.IBluetoothDevice;
import java.util.List;

public interface ScanCallback {
    void onScanStarted();
    void onDeviceFound(IBluetoothDevice device);
    void onScanFinished(List<IBluetoothDevice> devices);
    void onScanFailed(String errorMessage);
}