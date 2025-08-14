package lib.bt.interfaces;

import java.util.List;
import java.util.function.Consumer;

/**
 * Keşif (scan) ve eşleştirme (pairing) akışını yöneten arayüz.
 *
 * SÖZLEŞME:
 * - Callback kayıtları idempotenttir: aynı metoda tekrar çağrı yapılırsa önceki kayıtın üstüne yazar (tek callback saklanır).
 * - startScan() zaten çalışıyorsa no-op kabul edilir (ikinci çağrı yeni bir tarama başlatmaz).
 * - stopScan() çalışmıyorsa no-op kabul edilir.
 */
public interface IBluetoothDeviceScanner {

    // --- Tarama kontrolü ---
    void startScan();   // idempotent: zaten scan aktifse no-op
    void stopScan();    // idempotent: scan aktif değilse no-op

    // --- Cihaz listeleri ---
    List<IBluetoothDevice> getPairedDevices();
    List<IBluetoothDevice> getDevices();

    // --- Tarama olayları (tek callback saklanır; son verilen geçerlidir) ---
    void onDeviceFound(Consumer<IBluetoothDevice> callback);
    void onDiscoveryFinished(Runnable callback);

    // --- Eşleştirme olayları (tek callback saklanır; son verilen geçerlidir) ---
    void onPairingStatusChanged(Consumer<PairingStatus> callback);

    // --- Eşleştirme işlemleri ---
    void pairDevice(IBluetoothDevice device);
    void cancelPairing();

    // Pairing broadcast/receiver dinleyicilerini bırakmak için
    void stopListeningForPairingStatus();

    // --- Model ---
    final class PairingStatus {
        private final IBluetoothDevice device;
        private final IBluetoothDevice.BondState state;


        public PairingStatus(IBluetoothDevice device, IBluetoothDevice.BondState state) {
            this.device = device;
            this.state = state;
        }

        public IBluetoothDevice getDevice() { return device; }
        public IBluetoothDevice.BondState getState() { return state; }

        public boolean isPaired() { return state == IBluetoothDevice.BondState.BONDED; }

        public boolean isFinal() {
            return state == IBluetoothDevice.BondState.BONDED
                    || state == IBluetoothDevice.BondState.NONE
                    || state == IBluetoothDevice.BondState.FAILED;
        }
    }
}
