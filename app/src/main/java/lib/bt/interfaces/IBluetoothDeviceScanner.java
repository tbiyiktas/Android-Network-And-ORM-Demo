package lib.bt.interfaces;

import java.util.List;
import java.util.function.Consumer;

public interface IBluetoothDeviceScanner {

    // Arayüzdeki var olan metotlar...
    void startScan();
    void stopScan();
    List<IBluetoothDevice> getPairedDevices();
    List<IBluetoothDevice> getDevices();
    void onDeviceFound(Consumer<IBluetoothDevice> callback);
    void onDiscoveryFinished(Runnable callback);

    // Eşleştirme durum değişiklikleri için callback
    void onPairingStatusChanged(Consumer<PairingStatus> callback);

    // Eşleştirme işlemini başlatma
    void pairDevice(IBluetoothDevice device);

    // Eşleştirme işlemini iptal etme
    void cancelPairing();

    // Düzeltme: Yeni eklenen soyut metot
    void stopListeningForPairingStatus();

    class PairingStatus {
        private final IBluetoothDevice device;
        private final boolean isPaired;

        public PairingStatus(IBluetoothDevice device, boolean isPaired) {
            this.device = device;
            this.isPaired = isPaired;
        }

        public IBluetoothDevice getDevice() {
            return device;
        }

        public boolean isPaired() {
            return isPaired;
        }
    }
}