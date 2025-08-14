package lib.bt;

public final class BluetoothConfig {
    // Bluetooth bağlantısı başarısız olduğunda yapılacak maksimum otomatik yeniden deneme sayısı.
    // 0 olarak ayarlanırsa, otomatik yeniden deneme yapılmaz.
    public static final int MAX_RECONNECT_ATTEMPTS = 5;
    public static final int RECONNECT_DELAY_MS = 1000;
    public static final int RECONNECT_MAX_DELAY_MS = 15000;
}