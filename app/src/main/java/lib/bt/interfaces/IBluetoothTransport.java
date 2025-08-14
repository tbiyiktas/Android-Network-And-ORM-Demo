package lib.bt.interfaces;

import java.util.function.Consumer;

/**
 * Veri iletimi: gönder/al ve yaşam döngüsü (attach/detach).
 * Bağlantı kurma bu katmanın işi değildir.
 */
public interface IBluetoothTransport {

    boolean send(byte[] data);

    void onDataReceived(Consumer<byte[]> callback);

    /** Connector tarafından sağlanan düşük seviye kanala bağlanır. */
    void attach(Object lowLevelChannel);

    /** Kanalı bırakır ve arka plan okuma işlemlerini durdurur. */
    void detach();
    void onClosed(Runnable callback);
}
