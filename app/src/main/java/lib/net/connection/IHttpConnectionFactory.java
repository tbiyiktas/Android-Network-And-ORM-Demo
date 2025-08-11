package lib.net.connection;

import java.io.IOException;

public interface IHttpConnectionFactory {
    IHttpConnection createConnection(String urlString) throws IOException;
}