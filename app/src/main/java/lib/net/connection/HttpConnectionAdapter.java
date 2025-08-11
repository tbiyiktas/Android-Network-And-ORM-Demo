package lib.net.connection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

public class HttpConnectionAdapter implements IHttpConnection {

    private final HttpURLConnection connection;

    public HttpConnectionAdapter(HttpURLConnection connection) {
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
    public void setRequestMethod(String method) throws IOException {
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

    // HttpConnectionAdapter.java (readStream metodu güncellendi)
    private String readStream(InputStream stream, HttpURLConnection connection) throws IOException {
        if (stream == null) {
            return "";
        }

        // Content-Encoding başlığını kontrol et
        String contentEncoding = connection.getHeaderField("Content-Encoding");
        boolean isGzip = "gzip".equalsIgnoreCase(contentEncoding);

        try (InputStream wrappedStream = isGzip ? new GZIPInputStream(stream) : stream;
             BufferedReader reader = new BufferedReader(new InputStreamReader(wrappedStream, StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

}