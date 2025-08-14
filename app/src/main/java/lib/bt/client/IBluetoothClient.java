package lib.bt.client;

import java.util.List;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothConnector;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner;
import lib.bt.interfaces.IBluetoothStatusManager;
import lib.bt.model.BtResult;

public interface IBluetoothClient {
    // Scan
    void startScan();
    void stopScan();
    List<IBluetoothDevice> getDevices();
    List<IBluetoothDevice> getPairedDevices();
    void onDeviceFound(Consumer<IBluetoothDevice> cb);
    void onDiscoveryFinished(Runnable cb);

    // Pairing
    void pairDevice(IBluetoothDevice device);
    void cancelPairing();
    void stopListeningForPairingStatus();
    void onPairingStatusChanged(Consumer<IBluetoothDeviceScanner.PairingStatus> cb);

    // Connection
    boolean connect(String address);
    boolean disconnect();
    boolean isConnected();
    void onConnectionStatusChanged(Consumer<IBluetoothConnector.ConnectionStatus> cb);

    // Transport
    boolean send(byte[] data);
    void onDataReceived(Consumer<byte[]> cb);

    // Status
    boolean isBluetoothEnabled();
    void onBluetoothStatusChanged(Consumer<IBluetoothStatusManager.BluetoothStatus> cb);

    // Cleanup
    void shutdown();

    // Async convenience (BtResult)
    void scanOnce(long timeoutMs, Consumer<BtResult<List<IBluetoothDevice>>> cb);
    void pairAsync(IBluetoothDevice device, Consumer<BtResult<IBluetoothDevice>> cb);
    void connectAsync(String address, Consumer<BtResult<Void>> cb);
}
