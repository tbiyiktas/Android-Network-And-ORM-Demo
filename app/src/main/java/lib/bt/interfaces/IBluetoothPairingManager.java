package lib.bt.interfaces;

// Eşleştirme işlemleri ayrı yönetilir
public interface IBluetoothPairingManager {
    void pair(IBluetoothDevice device, lib.bt.callbacks.PairingCallback cb);
    void cancelPairing();
}