package lib.bt.interfaces;

/**
 * Platformdan bağımsız bir Bluetooth cihazını temsil eden arayüz.
 * Her platform (Android, iOS vb.) bu arayüzü uygulayan kendi sınıflarını sağlayacaktır.
 */
public interface IBluetoothDevice {

    enum Type { CLASSIC, BLE, DUAL, UNKNOWN }

    enum BondState { NONE, BONDING, BONDED, FAILED }

    String getAddress();
    String getName();

    /** Cihaz türü (CLASSIC / BLE / DUAL / UNKNOWN) */
    Type getType();

    /**
     * Eşleştirme durumu (NONE / BONDING / BONDED / FAILED)
     */
    BondState getBondState();

    /** Kolaylık: eşleştirilmiş mi? */
    default boolean isPaired() {
        return getBondState() == BondState.BONDED;
    }
}