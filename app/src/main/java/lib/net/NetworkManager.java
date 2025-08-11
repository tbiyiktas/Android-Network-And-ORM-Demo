package lib.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lib.net.command.ACommand;
import lib.net.command.DeleteCommand;
import lib.net.command.GetCommand;
import lib.net.command.MultipartCommand;
import lib.net.command.PatchCommand;
import lib.net.command.PostCommand;
import lib.net.command.PutCommand;
import lib.net.connection.HttpUrlConnectionFactory;
import lib.net.connection.IHttpConnection;
import lib.net.connection.IHttpConnectionFactory;
import lib.net.parser.GsonResponseParser;
import lib.net.parser.IResponseParser;
import lib.net.util.NetworkConfig;
import lib.net.util.RequestCancelledException;
import lib.net.util.ResponseHandler;

public class NetworkManager {

    private static final String TAG = "NetworkManager";

    private final ExecutorService requestExecutor = Executors.newFixedThreadPool(NetworkConfig.THREAD_POOL_SIZE);
    private final BlockingQueue<RequestTask> requestQueue = new LinkedBlockingQueue<>(NetworkConfig.QUEUE_CAPACITY);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ResponseHandler responseHandler;
    private final IHttpConnectionFactory connectionFactory;
    private final String basePath;
    private final ConcurrentHashMap<RequestHandle, RequestTask> activeRequests = new ConcurrentHashMap<>();
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);


    public NetworkManager(String basePath, IHttpConnectionFactory connectionFactory, ResponseHandler responseHandler) {
        this.basePath = basePath;
        this.connectionFactory = connectionFactory;
        this.responseHandler = responseHandler;
        startWorkers();
    }

    public static NetworkManager create(String basePath) {
        IResponseParser parser = new GsonResponseParser();
        ResponseHandler handler = new ResponseHandler(parser);
        IHttpConnectionFactory factory = new HttpUrlConnectionFactory();
        return new NetworkManager(basePath, factory, handler);
    }

    // NEW: only factory (parser = Gson)
    public static NetworkManager create(String basePath, IHttpConnectionFactory factory) {
        IResponseParser parser = new GsonResponseParser();
        ResponseHandler handler = new ResponseHandler(parser);
        return new NetworkManager(basePath, factory, handler);
    }

    // NEW: fully custom (factory + parser)
    public static NetworkManager create(String basePath, IHttpConnectionFactory factory, IResponseParser parser) {
        ResponseHandler handler = new ResponseHandler(parser);
        return new NetworkManager(basePath, factory, handler);
    }

    private void startWorkers() {
        for (int i = 0; i < NetworkConfig.THREAD_POOL_SIZE; i++) {
            requestExecutor.execute(new WorkerTask());
        }
    }

    public void shutdown() {
        isShuttingDown.set(true);

        while (!requestQueue.isEmpty()) {
            RequestTask task = requestQueue.poll();
            if (task != null) {
                task.command.cancel();
            }
        }

        requestExecutor.shutdown();
        try {
            if (!requestExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            requestExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // GÜNCELLENDİ: enqueueRequest metodu Class yerine Type alıyor.
    private <T> RequestHandle enqueueRequest(String basePath, ACommand command, Type responseType, NetworkCallback<T> callback) {
        RequestTask<T> task = new RequestTask<>(basePath, command, callback, responseType);
        if (!requestQueue.offer(task)) {
            Log.w(TAG, "Kuyruk dolu, istek reddedildi: " + command.getRelativeUrl());
            mainHandler.post(() -> callback.onResult(new NetResult.Error<>(
                    new IOException("Kuyruk dolu"), 429, "Too Many Requests - Kuyruk dolu."
            )));
            return new RequestHandle() { @Override public void cancel() {} @Override public boolean isCancelled() { return true; } };
        }
        activeRequests.put(command, task);
        return command;
    }

    // GÜNCELLENDİ: Artık tek bir ve daha esnek 'get' metodumuz var.
    public <T> RequestHandle get(String relativePath, HashMap<String, String> queryParams, HashMap<String, String> headers, Type responseType, NetworkCallback<T> callback) {
        ACommand command = new GetCommand(relativePath, queryParams, headers);
        return enqueueRequest(this.basePath, command, responseType, callback);
    }

    // YENİ METOT: Post isteği için
    public <T> RequestHandle post(String relativePath, String jsonContent, HashMap<String, String> headers, Type responseType, NetworkCallback<T> callback) {
        ACommand command = new PostCommand(relativePath, jsonContent, headers);
        return enqueueRequest(this.basePath, command, responseType, callback);
    }

    // YENİ METOT: Put isteği için
    public <T> RequestHandle put(String relativePath, String jsonContent, HashMap<String, String> headers, Type responseType, NetworkCallback<T> callback) {
        ACommand command = new PutCommand(relativePath, jsonContent, headers);
        return enqueueRequest(this.basePath, command, responseType, callback);
    }

    // YENİ METOT: Delete isteği için
    public <T> RequestHandle delete(String relativePath, HashMap<String, String> headers, Type responseType, NetworkCallback<T> callback) {
        ACommand command = new DeleteCommand(relativePath, headers);
        return enqueueRequest(this.basePath, command, responseType, callback);
    }

    // YENİ METOT: Dosya yükleme (Multipart) isteği için
    public <T> RequestHandle upload(String relativePath, HashMap<String, String> formFields, HashMap<String, File> files, HashMap<String, String> headers, Type responseType, NetworkCallback<T> callback) {
        ACommand command = new MultipartCommand(relativePath, headers, formFields, files);
        return enqueueRequest(this.basePath, command, responseType, callback);
    }

    private static class RequestTask<T> {
        String basePath;
        ACommand command;
        NetworkCallback<T> callback;
        Type responseType;

        RequestTask(String basePath, ACommand command, NetworkCallback<T> callback, Type responseType) {
            this.basePath = basePath;
            this.command = command;
            this.callback = callback;
            this.responseType = responseType;
        }
    }

    private class WorkerTask implements Runnable {
        @Override
        public void run() {
            try {
                while (!isShuttingDown.get()) {
                    // Task'ı jenerik olarak alıyoruz
                    RequestTask task = requestQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (task == null) continue;


                    if (task.command.isCancelled()) {
                        activeRequests.remove(task.command);
                        continue;
                    }

                    MyHttpClient client = new MyHttpClient(task.basePath, connectionFactory);
                    NetResult<?> finalResult;

                    try {
                        String fullUrl = client.buildUrlString(task.command);
                        IHttpConnection connection = client.createConnection(fullUrl);

                        NetResult<String> rawResult = task.command.execute(connection);

                        // Artık handle metodu doğru jenerik türü döndürüyor
                        finalResult = responseHandler.handle(rawResult, task.responseType);

                    } catch (RequestCancelledException e) {
                        Log.w(TAG, "İstek iptal edildi: " + e.getMessage());
                        finalResult = new NetResult.Error<>(e, -1, "İstek iptal edildi.");
                    } catch (Exception e) {
                        finalResult = new NetResult.Error<>(e, -1, "Bağlantı veya işlem hatası.");
                        Log.e(TAG, "Worker görevi sırasında hata: ", e);
                    }finally {
                        activeRequests.remove(task.command);
                    }

                    final NetResult finalResultForLambda = finalResult;

                    mainHandler.post(() -> {
                        if (task.callback != null) {
                            task.callback.onResult(finalResultForLambda);
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.d(TAG, "Worker iş parçacığı sonlandırıldı.");
            } catch (Exception e) {
                Log.e(TAG, "Worker görevi döngüsünde beklenmedik hata: ", e);
            }
        }
    }

    public static class Builder {
        private String basePath;
        private IHttpConnectionFactory factory = new HttpUrlConnectionFactory();
        private IResponseParser parser = new GsonResponseParser();

        public Builder basePath(String b){ this.basePath = b; return this; }
        public Builder factory(IHttpConnectionFactory f){ this.factory = f; return this; }
        public Builder parser(IResponseParser p){ this.parser = p; return this; }
        public NetworkManager build(){ return new NetworkManager(basePath, factory, new ResponseHandler(parser)); }
    }
}