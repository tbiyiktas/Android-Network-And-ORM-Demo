package lib.net.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IHttpConnection {
    void setConnectTimeout(int timeout);
    void setReadTimeout(int timeout);
    void setRequestMethod(String method) throws IOException;
    void setRequestProperty(String key, String value);
    void setDoOutput(boolean doOutput);
    void setDoInput(boolean doInput); // <-- Bu satır eklendi
    void setInstanceFollowRedirects(boolean followRedirects);
    void setAllowUserInteraction(boolean allowInteraction);
    void setUseCaches(boolean useCaches);
    int getResponseCode() throws IOException;
    InputStream getInputStream() throws IOException;
    InputStream getErrorStream();
    OutputStream getOutputStream() throws IOException;
    String getHeaderField(String key);
    void disconnect();
}