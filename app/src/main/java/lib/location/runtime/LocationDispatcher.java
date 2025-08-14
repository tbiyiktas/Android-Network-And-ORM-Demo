package lib.location.runtime;

import androidx.annotation.NonNull;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lib.location.BackpressurePolicy;
import lib.location.Cancellable;
import lib.location.ILocationCenter;
import lib.location.LocationConfigProvider;
import lib.location.commands.LocationCommand;

public class LocationDispatcher {

    private final ILocationCenter center;
    private final ThreadPoolExecutor executor;

    public LocationDispatcher(@NonNull ILocationCenter center,
                              @NonNull LocationConfigProvider config) {
        this.center = center;

        final int capacity = Math.max(1, config.getDispatcherQueueCapacity());
        final BackpressurePolicy policy = config.getDispatcherBackpressurePolicy();

        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity);
        RejectedExecutionHandler reh = handlerFor(policy);

        this.executor = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                queue,
                new ThreadFactory() {
                    @Override public Thread newThread(@NonNull Runnable r) {
                        Thread t = new Thread(r, "LocationQueue");
                        t.setDaemon(true);
                        return t;
                    }
                },
                reh
        );
        // Caller verilen handler DROP_OLDEST ise, default ThreadPoolExecutor dolu kuyruğa
        // otomatik davranış uygulamaz; biz handler içinde yönetiyoruz.
        this.executor.prestartAllCoreThreads();
    }

    /** Kuyruk doluysa seçilen politika uygulanır. */
    public Cancellable enqueue(@NonNull LocationCommand cmd) {
        if (cmd.isCanceled()) return cmd;
        Runnable task = () -> { if (!cmd.isCanceled()) cmd.execute(center); };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException rex) {
            // ABORT politikasında buraya düşer. Komutu iptal edelim.
            cmd.cancel();
            // İstersenizinize göre burada log atabilirsiniz.
        }
        return cmd;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    // --- Helpers ---

    private static RejectedExecutionHandler handlerFor(BackpressurePolicy policy) {
        switch (policy) {
            case DROP_OLDEST:
                // En eskiyi düşür, yeni işi sıraya al
                return (r, ex) -> {
                    BlockingQueue<Runnable> q = ex.getQueue();
                    // Düşürebilirsek en eskisini atıp tekrar dene
                    if (q.poll() != null) {
                        try { ex.execute(r); } catch (RejectedExecutionException ignore) {}
                    }
                    // Dolu ve tek thread kitliyse yine reddedilebilir; o durumda sessizce düşer.
                };
            case DROP_LATEST:
                // Yeni gelen işi sessizce görmezden gel
                return (r, ex) -> { /* no-op: drop r */ };
            case RUN_ON_CALLER:
                // Çağıran thread üzerinde çalıştır
                return (r, ex) -> r.run();
            case ABORT:
            default:
                // Varsayılan: standart policy (RejectedExecutionException)
                return new ThreadPoolExecutor.AbortPolicy();
        }
    }
}
