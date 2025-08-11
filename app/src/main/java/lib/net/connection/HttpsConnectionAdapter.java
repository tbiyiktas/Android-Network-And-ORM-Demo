package lib.net.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;

import javax.net.ssl.HttpsURLConnection;

public class HttpsConnectionAdapter implements IHttpConnection {
    private final HttpsURLConnection connection;

    public HttpsConnectionAdapter(HttpsURLConnection connection) {
        this.connection = connection;
    }

    @Override
    public void setConnectTimeout(int timeout) {
        connection.setConnectTimeout(timeout);
    }

    @Override
    public void setReadTimeout(int timeout) {
        connection.setReadTimeout(timeout);
    }

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
        connection.setRequestMethod(method);
    }

    @Override
    public void setRequestProperty(String key, String value) {
        connection.setRequestProperty(key, value);
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        connection.setDoOutput(doOutput);
    }

    @Override
    public void setDoInput(boolean doInput) {
        connection.setDoInput(doInput);
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        connection.setInstanceFollowRedirects(followRedirects);
    }

    @Override
    public void setAllowUserInteraction(boolean allowInteraction) {
        connection.setAllowUserInteraction(allowInteraction);
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        connection.setUseCaches(useCaches);
    }

    @Override
    public int getResponseCode() throws IOException {
        return connection.getResponseCode();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return connection.getInputStream();
    }

    @Override
    public InputStream getErrorStream() {
        return connection.getErrorStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return connection.getOutputStream();
    }

    @Override
    public String getHeaderField(String key) {
        return connection.getHeaderField(key);
    }

    @Override
    public void disconnect() {
        connection.disconnect();
    }
}