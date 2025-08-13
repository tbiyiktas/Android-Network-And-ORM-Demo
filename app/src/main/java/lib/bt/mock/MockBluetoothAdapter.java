package lib.bt.mock;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothConnector_old;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.interfaces.IBluetoothDeviceScanner;
import lib.bt.interfaces.IBluetoothStatusManager;

public class MockBluetoothAdapter implements IBluetoothStatusManager, IBluetoothDeviceScanner, IBluetoothConnector_old {

    private static final String TAG = "MockBluetoothAdapter";
    private Consumer<IBluetoothDeviceScanner.PairingStatus> pairingStatusCallback;
    private List<IBluetoothDevice> mockDevices = new ArrayList<>();

    public MockBluetoothAdapter() {
        mockDevices.add(new MockBluetoothDevice("Cihaz A", "00:11:22:33:44:55"));
        mockDevices.add(new MockBluetoothDevice("Cihaz B", "66:77:88:99:AA:BB"));
    }

    // IBluetoothStatusManager
    @Override
    public boolean isEnabled() {
        return true; // Her zaman açık
    }

    @Override
    public void onStatusChanged(Consumer<BluetoothStatus> callback) {
        // Mock için boş bırakıldı
    }

    @Override
    public boolean isDevicePaired(IBluetoothDevice device) {
        // Mock için her zaman eşleşmiş dönsün
        return true;
    }

    // IBluetoothDeviceScanner
    @Override
    public void startScan() {
        Log.d(TAG, "Mock tarama başlatıldı.");
    }

    @Override
    public void stopScan() {
        Log.d(TAG, "Mock tarama durduruldu.");
    }

    @Override
    public List<IBluetoothDevice> getPairedDevices() {
        return mockDevices;
    }

    @Override
    public List<IBluetoothDevice> getDevices() {
        return mockDevices;
    }

    @Override
    public void onDeviceFound(Consumer<IBluetoothDevice> callback) {
        // Mock için boş bırakıldı
    }

    @Override
    public void onDiscoveryFinished(Runnable callback) {
        // Mock için boş bırakıldı
    }

    @Override
    public void onPairingStatusChanged(Consumer<PairingStatus> callback) {

    }

    //@Override
    public void startListeningForPairingStatus(Consumer<IBluetoothDeviceScanner.PairingStatus> callback) {
        Log.d(TAG, "Mock eşleştirme dinlemesi başlatıldı.");
        this.pairingStatusCallback = callback;
    }

    // EKSİK METOT: Eşleştirme dinlemesini durdurur
    @Override
    public void stopListeningForPairingStatus() {
        Log.d(TAG, "Mock eşleştirme dinlemesi durduruldu.");
        this.pairingStatusCallback = null;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    // IBluetoothConnector
    @Override
    public boolean connect(String deviceAddress) {
        Log.d(TAG, "Mock bağlantı kuruldu: " + deviceAddress);
        return true;
    }

    @Override
    public void pairDevice(IBluetoothDevice device) {
        Log.d(TAG, "Mock eşleştirme isteği gönderildi: " + device.getName());
        // Eşleşme callback'ini simüle edelim
        if (pairingStatusCallback != null) {
            pairingStatusCallback.accept(new PairingStatus(device, true));
        }
    }

    @Override
    public void cancelPairing() {

    }

    @Override
    public IBluetoothDevice getDeviceFromAddress(String deviceAddress) {
        // Mock'ta adresle cihaz bulma
        for (IBluetoothDevice device : mockDevices) {
            if (device.getAddress().equals(deviceAddress)) {
                return device;
            }
        }
        return null;
    }

    @Override
    public void onConnectionStatusChanged(Consumer<ConnectionStatus> callback) {

    }

    @Override
    public boolean disconnect() {
        Log.d(TAG, "Mock bağlantı kesildi.");
        return true;
    }

    @Override
    public boolean sendData(byte[] data) {
        Log.d(TAG, "Mock veri gönderildi.");
        return true;
    }

    @Override
    public void onDataReceived(Consumer<byte[]> callback) {
        // Mock için boş bırakıldı
    }
}