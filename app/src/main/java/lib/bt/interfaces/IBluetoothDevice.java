package lib.bt.interfaces;

/**
 * Platformdan bağımsız bir Bluetooth cihazını temsil eden arayüz.
 * Her platform (Android, iOS vb.) bu arayüzü uygulayan kendi sınıflarını sağlayacaktır.
 */
public interface IBluetoothDevice {
    String getAddress();
    String getName();
    // Gerekirse başka ortak özellikler de eklenebilir.
    // Örneğin, int getType(); // Klasik, LE, Dual gibi.

    int getBondState();
}