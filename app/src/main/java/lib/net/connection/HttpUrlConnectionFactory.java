package lib.net.connection;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpUrlConnectionFactory implements IHttpConnectionFactory {
    @Override
    public IHttpConnection createConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        return new HttpConnectionAdapter(connection);
    }
}