package lib.net.connection;

import okhttp3.OkHttpClient;

/**
 * OkHttp tabanlı IHttpConnectionFactory.
 * NetworkManager.create(baseUrl, factory) ile verildiğinde tüm HTTP çağrıları OkHttp üzerinden yapılır.
 */
public class OkHttpConnectionFactory implements IHttpConnectionFactory {

    private final OkHttpClient baseClient;

    public OkHttpConnectionFactory() {
        this(new OkHttpClient());
    }

    public OkHttpConnectionFactory(OkHttpClient client) {
        this.baseClient = (client != null) ? client : new OkHttpClient();
    }

    @Override
    public IHttpConnection createConnection(String url) {
        return new OkHttpConnectionAdapter(url, baseClient);
    }
}
