package lib.net;

import lib.net.connection.IHttpConnectionFactory;

public class MyHttpClient extends AHttpClient {

    public MyHttpClient(String basePath, IHttpConnectionFactory connectionFactory) {
        super(basePath, connectionFactory);
    }

    public MyHttpClient(String basePath) {
        super(basePath);
    }
}