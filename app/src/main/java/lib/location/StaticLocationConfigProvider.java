package lib.location;

import androidx.annotation.NonNull;

public class StaticLocationConfigProvider implements LocationConfigProvider {

    // Statik sabitler (XML yok)
    private static final int HISTORY_SIZE = 20;
    //private static final boolean START_ON_APP_START = false;
    private static final boolean ENABLE_FG_SERVICE = false;

    // App arka plana geçince otomatik stop (pil için önerilir)
    private static final boolean STOP_ON_BACKGROUND = true;
    private static final boolean START_ON_APP_START = true;
    // >>> Yeni politika: accuracy filtresi varken accuracy==null kabul edilsin mi?
    // Katı mod (önerilen): false  -> null accuracy REDDEDİLİR
    // Gevşek mod          : true   -> null accuracy KABUL EDİLİR
    private static final boolean ACCEPT_NULL_ACCURACY_IF_FILTERED = false;

    // ---- Dispatcher backpressure ----
    private static final int DISPATCHER_QUEUE_CAPACITY = 256;
    private static final BackpressurePolicy DISPATCHER_POLICY = BackpressurePolicy.DROP_OLDEST;
    // Alternatifler: DROP_LATEST, RUN_ON_CALLER, ABORT


    private static final LocationRequestOptions LIVE_OPTIONS =
            new LocationRequestOptions.Builder()
                    .priority(LocationRequestOptions.Priority.BALANCED)
                    .intervalMs(5_000)
                    .fastestIntervalMs(2_000)
                    .minDistanceMeters(5f)
                    .useForegroundService(ENABLE_FG_SERVICE)
                    .build();

    private static final LocationRequestOptions ONESHOT_OPTIONS =
            new LocationRequestOptions.Builder()
                    .priority(LocationRequestOptions.Priority.HIGH_ACCURACY)
                    .timeoutMs(12_000)
                    .build();

    @Override public int getHistorySize() { return HISTORY_SIZE; }
    @Override public boolean startOnAppStart() { return START_ON_APP_START; }
    @Override public boolean enableForegroundService() { return ENABLE_FG_SERVICE; }
    @Override public boolean stopOnBackground() { return STOP_ON_BACKGROUND; }
    @Override
    public boolean acceptNullAccuracyIfFiltered() { return ACCEPT_NULL_ACCURACY_IF_FILTERED; }

    @Override public int getDispatcherQueueCapacity() { return DISPATCHER_QUEUE_CAPACITY; }
    @NonNull @Override public BackpressurePolicy getDispatcherBackpressurePolicy() { return DISPATCHER_POLICY; }


    @NonNull @Override public LocationRequestOptions getLiveOptions() { return LIVE_OPTIONS; }
    @NonNull @Override public LocationRequestOptions getOneShotOptions() { return ONESHOT_OPTIONS; }
}
