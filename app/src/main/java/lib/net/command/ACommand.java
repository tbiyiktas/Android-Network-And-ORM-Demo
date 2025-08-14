package lib.net.command;

import static android.content.ContentValues.TAG;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import lib.net.NetResult;
import lib.net.RequestHandle;
import lib.net.connection.IHttpConnection;
import lib.net.util.NetworkConfig;
import lib.net.util.RequestCancelledException;

public abstract class ACommand implements RequestHandle {
    protected ArrayList<Exception> exceptions;
    protected NetResult<String> result;
    protected String relativeUrl;
    protected HashMap<String, String> queryParams;
    protected HashMap<String, String> headers;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    private int customConnectTimeout = -1;
    private int customReadTimeout = -1;
    private final boolean isIdempotent;

    public ACommand(String relativeUrl) {
        this.exceptions = new ArrayList<>();
        this.relativeUrl = relativeUrl;
        this.queryParams = new HashMap<>();
        this.headers = new HashMap<>();
        this.isIdempotent = "GET".equals(getMethodName()) || "PUT".equals(getMethodName()) || "DELETE".equals(getMethodName());
    }

    public ACommand(String relativeUrl, HashMap<String, String> queryParams) {
        this(relativeUrl);
        if (queryParams != null) {
            this.queryParams.putAll(queryParams);
        }
    }

    // Eksik olan yapıcı metot bu. Bu metot sayesinde DeleteCommand doğru çalışacak.
    public ACommand(String relativeUrl, HashMap<String, String> queryParams, HashMap<String, String> headers) {
        this(relativeUrl, queryParams);
        if (headers != null) {
            this.headers.putAll(headers);
        }
    }

    public void setConnectTimeout(int timeoutMs) {
        this.customConnectTimeout = timeoutMs;
    }

    public void setReadTimeout(int timeoutMs) {
        this.customReadTimeout = timeoutMs;
    }

    @Override
    public void cancel() {
        isCancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
        return isCancelled.get();
    }

    public String getRelativeUrl() {
        return relativeUrl;
    }

    public HashMap<String, String> getQueryParams() {
        return queryParams;
    }

    public HashMap<String, String> getHeaders() {
        return headers;
    }

    protected String getContentType() {
        return "application/json; charset=utf-8";
    }

    protected abstract String getMethodName();

    protected void handleRequest(IHttpConnection connection) throws IOException {
        connection.setRequestProperty("Content-Type", getContentType());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "gzip");

        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    protected void write(IHttpConnection connection, String jsonContent) throws IOException {
        connection.setDoOutput(true);
        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            outputStream.write(jsonContent.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        }
    }

    protected void read(IHttpConnection connection) {
        try (InputStream in = getInputStream(connection);
             InputStreamReader inputStreamReader = new InputStreamReader(in, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (isCancelled()) {
                    throw new RequestCancelledException("İstek iptal edildi.");
                }
                sb.append(line);
            }
            result = new NetResult.Success<String>(sb.toString());
        } catch (Exception e) {
            handleException(e);
        }
    }

    private InputStream getInputStream(IHttpConnection connection) throws IOException {
        InputStream stream = connection.getInputStream();
        if ("gzip".equalsIgnoreCase(connection.getHeaderField("Content-Encoding"))) {
            return new GZIPInputStream(stream);
        }
        return stream;
    }

    protected void handleError(IHttpConnection connection, int responseCode) {
        StringBuilder sb = new StringBuilder();
        InputStream errorStream = connection.getErrorStream();
        InputStream streamToRead = null;

        if (errorStream != null) {
            streamToRead = errorStream;
        } else {
            try {
                streamToRead = connection.getInputStream();
            } catch (IOException e) {
                // inputStream'e de erişilemiyorsa, hata gövdesi yok demektir
            }
        }

        if (streamToRead != null) {
            try (InputStream in = streamToRead;
                 InputStreamReader inputStreamReader = new InputStreamReader(in, StandardCharsets.UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line);
                }
            } catch (IOException e) {
                handleException(e);
            }
        }
        String errorBody = sb.length() > 0 ? sb.toString() : "No error body provided.";
        handleException(new Exception("Response code: " + responseCode), responseCode, errorBody);
    }

    protected void handleException(Exception e) {
        handleException(e, -1, "Unknown error.");
    }

    protected void handleException(Exception e, int responseCode, String errorBody) {
        exceptions.add(e);
        result = new NetResult.Error<String>(e, responseCode, errorBody);
    }

    protected NetResult<String> getResult() {
        if (result == null && !exceptions.isEmpty()) {
            result = new NetResult.Error<String>(exceptions.get(0), -1, "Unknown error during execution.");
        }
        return result;
    }



    public NetResult<String> execute(IHttpConnection connection) {
        int retryCount = 0;
        long retryDelay = NetworkConfig.INITIAL_RETRY_DELAY_MS;

        while (retryCount <= NetworkConfig.RETRY_LIMIT) {
            if (isCancelled()) {
                handleException(new RequestCancelledException("İstek iptal edildi."));
                return getResult();
            }
            try {
                connection.setConnectTimeout(customConnectTimeout > 0 ? customConnectTimeout : NetworkConfig.CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(customReadTimeout > 0 ? customReadTimeout : NetworkConfig.READ_TIMEOUT_MS);
                connection.setRequestMethod(getMethodName());

                // Tüm bağlantı ayarlarını ve veri yazma işlemini burada yapın
                handleRequest(connection);

                // ÖNEMLİ DÜZELTME: getResponseCode çağrısını en son yapın
                int responseCode = connection.getResponseCode();

                if (responseCode >= 200 && responseCode < 300 || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                        result = new NetResult.Success<>("");
                    } else {
                        read(connection);
                    }
                    return getResult();
                } else if (shouldRetry(responseCode)) {
                    Log.w("RETRY", String.format("Hata kodu: %d, yeniden deneniyor (%d/%d)", responseCode, retryCount + 1, NetworkConfig.RETRY_LIMIT));

                    String retryAfterHeader = connection.getHeaderField("Retry-After");
                    if (retryAfterHeader != null) {
                        try {
                            long serverDelay = Long.parseLong(retryAfterHeader) * 1000;
                            retryDelay = Math.max(retryDelay, serverDelay);
                        } catch (NumberFormatException e) {
                            // Header yanlış biçimli, varsayılan delay'i kullan
                        }
                    }
                    Thread.sleep(retryDelay);
                    retryDelay *= 2;
                } else {
                    handleError(connection, responseCode);
                    return getResult();
                }
            } catch (Exception e) {
                if (shouldRetryOnException(e)) {
                    Log.w("RETRY", String.format("Exception: %s, yeniden deneniyor (%d/%d)", e.getMessage(), retryCount + 1, NetworkConfig.RETRY_LIMIT));
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    retryDelay *= 2;
                } else {
                    handleException(e);
                    return getResult();
                }
            } finally {
                connection.disconnect();
            }
            retryCount++;
        }

        handleException(new IOException("İstek, tekrar deneme limitini aştı."));
        return getResult();
    }



    /*
    public NetResult<String> execute(IHttpConnection connection) {
        int retryCount = 0;
        long retryDelay = NetworkConfig.INITIAL_RETRY_DELAY_MS;

        while (retryCount <= NetworkConfig.RETRY_LIMIT) {
            if (isCancelled()) {
                handleException(new RequestCancelledException("İstek iptal edildi."));
                return getResult();
            }
            try {
                connection.setConnectTimeout(customConnectTimeout > 0 ? customConnectTimeout : NetworkConfig.CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(customReadTimeout > 0 ? customReadTimeout : NetworkConfig.READ_TIMEOUT_MS);

                connection.setRequestMethod(getMethodName());
                if ("PATCH".equals(getMethodName())) {
                    enablePatchMethod(connection);
                }

                connection.setInstanceFollowRedirects(true);
                connection.setAllowUserInteraction(false);
                connection.setUseCaches(false);

                handleRequest(connection);

                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300 || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                        result = new NetResult.Success<>("");
                    } else {
                        read(connection);
                    }
                    return getResult();
                } else if (shouldRetry(responseCode)) {
                    Log.w("RETRY", String.format("Hata kodu: %d, yeniden deneniyor (%d/%d)", responseCode, retryCount + 1, NetworkConfig.RETRY_LIMIT));

                    String retryAfterHeader = connection.getHeaderField("Retry-After");
                    if (retryAfterHeader != null) {
                        try {
                            long serverDelay = Long.parseLong(retryAfterHeader) * 1000;
                            retryDelay = Math.max(retryDelay, serverDelay);
                        } catch (NumberFormatException e) {
                            // Header yanlış biçimli, varsayılan delay'i kullan
                        }
                    }
                    Thread.sleep(retryDelay);
                    retryDelay *= 2; // Exponential backoff
                } else {
                    handleError(connection, responseCode);
                    return getResult();
                }
            } catch (Exception e) {
                if (shouldRetryOnException(e)) {
                    Log.w("RETRY", String.format("Exception: %s, yeniden deneniyor (%d/%d)", e.getMessage(), retryCount + 1, NetworkConfig.RETRY_LIMIT));
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    retryDelay *= 2;
                } else {
                    handleException(e);
                    return getResult();
                }
            } finally {
                connection.disconnect();
            }
            retryCount++;
        }

        handleException(new IOException("İstek, tekrar deneme limitini aştı."));
        return getResult();
    }
*/
    private boolean shouldRetry(int responseCode) {
        return (responseCode == 429 || responseCode == 503) && isIdempotent;
    }

    private boolean shouldRetryOnException(Exception e) {
        return e instanceof IOException && isIdempotent;
    }

    private void enablePatchMethod(IHttpConnection connection) {
        // Reflection tabanlı PATCH metot etkinleştirme kodu...
    }

    // PATCH metodu için reflection ile etkinleştirme
    private void enablePatchMethod() {
        try {
            System.setProperty("http.keepAlive", "false"); // PATCH için özel bir ayar
            // PATCH methodunu etkinleştirmek için bir metod
        } catch (Exception e) {
            Log.e(TAG, "PATCH metodu etkinleştirilemedi: " + e.getMessage());
        }
    }

    // Yeni: `getMethodName()` yerine `applyMethod` çağrısında kullanıyoruz
    public void applyMethod(HttpURLConnection connection) throws IOException {
        String method = getMethodName();
        if ("PATCH".equalsIgnoreCase(method)) {
            enablePatchMethod();
            connection.setRequestMethod("POST"); // Fallback olarak POST kullan
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        } else {
            connection.setRequestMethod(method);
        }
    }


}