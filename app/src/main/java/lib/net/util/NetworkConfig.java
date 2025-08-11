package lib.net.util;

public class NetworkConfig {

    // Ağ istekleri için genel ayarlar
    public static final int CONNECT_TIMEOUT_MS = 15000;
    public static final int READ_TIMEOUT_MS = 15000;
    public static final int THREAD_POOL_SIZE = 4;

    // Kuyruk ayarları
    public static final int QUEUE_CAPACITY = 256;

    // Retry ayarları
    public static final int RETRY_LIMIT = 3;
    public static final long INITIAL_RETRY_DELAY_MS = 1000;
}