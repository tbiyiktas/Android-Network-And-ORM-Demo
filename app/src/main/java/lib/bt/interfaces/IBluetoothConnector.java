package lib.bt.interfaces;

import java.util.function.Consumer;

/**
 * Sadece bağlan/ayrıl ve bağlantı durumunu yayımlar.
 * Eşleştirme ve veri iletimi bu arayüzün dışındadır (SRP & ISP).
 */
public interface IBluetoothConnector {

    enum ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, FAILED
    }

    boolean connect(String deviceAddress);

    boolean disconnect();

    boolean isConnected();

    /** Bağlantı durum değişimlerini yayınlar. */
    void onConnectionStatusChanged(Consumer<ConnectionStatus> callback);

    /**
     * (Opsiyonel) Transport katmanının bağlanması için düşük seviye kanal.
     * Android tipi sızdırmamak için Object olarak bırakıldı.
     * Implementasyonlar uygun türü döndürebilir (örn. BluetoothSocket).
     */
    default Object getLowLevelChannel() { return null; }
}
