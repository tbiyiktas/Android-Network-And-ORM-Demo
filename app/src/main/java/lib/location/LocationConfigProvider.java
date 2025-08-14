package lib.location;

import androidx.annotation.NonNull;

public interface LocationConfigProvider {
    int getHistorySize();
    boolean startOnAppStart();
    boolean enableForegroundService();

    /** Accuracy filtresi aktifken accuracy==null değerleri kabul edilsin mi? */
    boolean acceptNullAccuracyIfFiltered();

    /** App arka plana geçince LocationCenter.stop() çağrılsın mı? */
    boolean stopOnBackground();

    /** Dispatcher kuyruğunun maks. eleman sayısı (bounded queue). */
    int getDispatcherQueueCapacity();

    /** Kuyruk dolu olduğunda uygulanacak strateji. */
    @NonNull BackpressurePolicy getDispatcherBackpressurePolicy();

    @NonNull LocationRequestOptions getLiveOptions();
    @NonNull LocationRequestOptions getOneShotOptions();
}
