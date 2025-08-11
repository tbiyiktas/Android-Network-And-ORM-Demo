package lib.net.connection;

import lib.net.connection.IHttpConnection;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OkHttp tabanlı IHttpConnection implementasyonu.
 * - Request konfigürasyonu bu nesne üzerinde yapılır.
 * - İlk kez getResponseCode()/getInputStream()/getErrorStream() çağrıldığında HTTP isteği gönderilir.
 * - getOutputStream(), body verisini buffer'da toplar; execute sırasında body olarak gönderilir.
 *
 * Notlar:
 * - OkHttp, gzip'i varsayılan olarak şeffaf şekilde açar (decompress). Content-Encoding kontrolü yapmana çoğu zaman gerek kalmaz.
 * - setDoInput / setUseCaches / setAllowUserInteraction OkHttp’de doğrudan karşılığı olmayan no-op alanlardır.
 */
public class OkHttpConnectionAdapter implements IHttpConnection {

    private final String url;
    private final OkHttpClient baseClient;

    // Request config
    private String method = "GET";
    private final Map<String, String> headers = new LinkedHashMap<>();
    private boolean doOutput = false;
    private boolean doInput = true;
    private boolean followRedirects = true;
    private boolean allowUserInteraction = false; // no-op
    private boolean useCaches = false;            // no-op
    private int connectTimeoutMs = -1;
    private int readTimeoutMs = -1;

    // Body buffer
    private final ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();

    // Execution state
    private volatile boolean executed = false;
    private Response response;

    public OkHttpConnectionAdapter(String url, OkHttpClient baseClient) {
        this.url = url;
        this.baseClient = baseClient;
    }

    // -------------------- IHttpConnection --------------------

    @Override
    public void setConnectTimeout(int timeout) {
        this.connectTimeoutMs = timeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
        this.readTimeoutMs = timeout;
    }

    @Override
    public void setRequestMethod(String method) throws IOException {
        this.method = method != null ? method.toUpperCase() : "GET";
    }

    @Override
    public void setRequestProperty(String key, String value) {
        if (key == null) return;
        headers.put(key, value != null ? value : "");
    }

    @Override
    public void setDoOutput(boolean doOutput) {
        this.doOutput = doOutput;
    }

    @Override
    public void setDoInput(boolean doInput) {
        this.doInput = doInput; // OkHttp'de doğrudan karşılığı yok; sadece semantik
    }

    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        this.followRedirects = followRedirects;
    }

    @Override
    public void setAllowUserInteraction(boolean allowInteraction) {
        this.allowUserInteraction = allowInteraction; // no-op
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        this.useCaches = useCaches; // no-op
    }

    @Override
    public int getResponseCode() throws IOException {
        ensureExecuted();
        return response.code();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        ensureExecuted();
        if (isErrorStatus(response.code())) {
            // Hata durumunda normal akışın InputStream’i yerine boş stream döndür.
            return new ByteArrayInputStream(new byte[0]);
        }
        if (response.body() == null) return new ByteArrayInputStream(new byte[0]);
        // OkHttp gzip'i transparan açtığından body doğrudan okunabilir.
        return response.body().byteStream();
    }

    @Override
    public InputStream getErrorStream() {
        try {
            ensureExecuted();
        } catch (IOException e) {
            // execute edilemediyse, en azından boş dön
            return new ByteArrayInputStream(new byte[0]);
        }
        if (!isErrorStatus(response.code())) return null;
        if (response.body() == null) return new ByteArrayInputStream(new byte[0]);
        return response.body().byteStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        // Çağıran yazıyı buraya basar; execute sırasında body olarak gönderilir.
        return bodyBuffer;
    }

    @Override
    public String getHeaderField(String key) {
        if (response == null) return null;
        return response.header(key);
    }

    @Override
    public void disconnect() {
        try {
            if (response != null) response.close();
        } catch (Exception ignore) {
        } finally {
            response = null;
            executed = false;
            bodyBuffer.reset();
        }
    }

    // -------------------- Helpers --------------------

    private synchronized void ensureExecuted() throws IOException {
        if (executed) return;

        OkHttpClient client = configureClient(baseClient, connectTimeoutMs, readTimeoutMs, followRedirects);

        Request.Builder rb = new Request.Builder().url(url);

        // Headers
        for (Map.Entry<String, String> h : headers.entrySet()) {
            rb.addHeader(h.getKey(), h.getValue());
        }

        // Request body
        byte[] bodyBytes = bodyBuffer.toByteArray();
        boolean hasBody = doOutput || methodAllowsRequestBody(method);
        RequestBody requestBody = null;
        if (hasBody) {
            String contentType = headers.getOrDefault("Content-Type", "application/json; charset=utf-8");
            MediaType mt = MediaType.parse(contentType);
            if (bodyBytes.length == 0 && requiresNonNullBody(method)) {
                // Bazı sunucular boş body de kabul eder; null yerine empty byte[]
                bodyBytes = new byte[0];
            }
            if (methodAcceptsBody(method)) {
                requestBody = RequestBody.create(bodyBytes, mt);
            } // GET/HEAD için body null olmalı
        }

        // Method mapping
        rb.method(method, methodAcceptsBody(method) ? requestBody : null);

        Call call = client.newCall(rb.build());
        response = call.execute(); // blocking; zaten worker thread’de çalışıyor
        executed = true;
    }

    private static boolean isErrorStatus(int code) {
        return code >= 400;
    }

    private static OkHttpClient configureClient(OkHttpClient base,
                                                int connectTimeoutMs,
                                                int readTimeoutMs,
                                                boolean followRedirects) {
        OkHttpClient.Builder b = base.newBuilder()
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects);
        if (connectTimeoutMs > 0) b.connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS);
        if (readTimeoutMs > 0) {
            b.readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS);
            b.writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS); // simetrik tutmak için
        }
        return b.build();
    }

    private static boolean methodAcceptsBody(String m) {
        // OkHttp kuralı: GET/HEAD body kabul etmez (null olmalı)
        return !"GET".equals(m) && !"HEAD".equals(m);
    }

    private static boolean methodAllowsRequestBody(String m) {
        // DELETE de body alabilir (sunucu tarafına göre değişebilir)
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
    }

    private static boolean requiresNonNullBody(String m) {
        return "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m);
    }
}
