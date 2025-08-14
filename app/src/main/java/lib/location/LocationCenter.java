package lib.location;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocationCenter implements ILocationCenter {

    private final Context appContext;
    private final ILocationClient client;
    private final LocationHistoryStore<LocationResult<LocationData>> history;
    private final LocationConfigProvider config; // <<<< ENJEKTE EDİLEN KONFİG

    private final HandlerThread workerThread;
    private final Handler worker;
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile LocationSubscription subscription;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LocationCenter(@NonNull Context appContext,
                          @NonNull ILocationClient client,
                          int historyCapacity,
                          @NonNull LocationConfigProvider config) { // <<< yeni parametre
        this.appContext = appContext.getApplicationContext();
        this.client = client;
        this.history = new LocationHistoryStore<>(historyCapacity);
        this.config = config;

        this.workerThread = new HandlerThread("LocationCenterThread");
        this.workerThread.start();
        this.worker = new Handler(workerThread.getLooper());
    }

    @Override
    public void start(@NonNull LocationRequestOptions options) {
        if (running.getAndSet(true)) return;

        subscription = client.startLocationUpdates(appContext, options, new LocationUpdateListener() {
            @Override
            public void onLocation(@NonNull LocationData data) {
                worker.post(() -> history.add(LocationResult.success(data)));
            }

            @Override
            public void onError(@NonNull LocationException error) {
                worker.post(() -> history.add(LocationResult.<LocationData>error(error.code, error.getMessage(), error)));
            }
        });
    }

    @Override
    public void stop() {
        if (!running.getAndSet(false)) return;
        LocationSubscription sub = subscription;
        subscription = null;
        if (sub != null) {
            client.stopLocationUpdates(sub);
        }
    }

    @Override
    public void requestOneShot(@NonNull LocationRequestOptions options) {
        client.getCurrentLocation(appContext, options, new LocationCallback() {
            @Override
            public void onSuccess(@NonNull LocationData data) {
                worker.post(() -> history.add(LocationResult.success(data)));
            }
            @Override
            public void onError(@NonNull LocationException error) {
                worker.post(() -> history.add(LocationResult.<LocationData>error(error.code, error.getMessage(), error)));
            }
        });
    }

    @Override
    @NonNull
    public List<LocationResult<LocationData>> getHistoryNewestFirst() {
        return history.snapshotNewestFirst();
    }

    @Override
    public int getHistoryCapacity() { return history.capacity(); }

    @Override
    public void setHistoryCapacity(int n) { history.setCapacity(n); }

    @Override
    public boolean isRunning() { return running.get(); }

    @Override
    @NonNull
    public LocationResult<LocationData> getLatestValid() {
        return getLatestValid(0, null);
    }

    @Override
    @NonNull
    public LocationResult<LocationData> getLatestValid(long maxAgeMs, @Nullable Float minAccuracyMeters) {
        final long nowMonoNs = SystemClock.elapsedRealtimeNanos();   // monotonik “şimdi”
        final long nowWallMs = System.currentTimeMillis();           // fallback

        final boolean acceptNullAcc = config.acceptNullAccuracyIfFiltered();

        for (LocationResult<LocationData> r : history.snapshotNewestFirst()) {
            if (r == null || !r.isSuccess() || r.data == null) continue;

            // 1) Yaş filtresi (öncelik: monotonik)
            if (maxAgeMs > 0) {
                long ageMs;
                if (r.data.elapsedRealtimeNanos != null) {
                    long deltaNs = nowMonoNs - r.data.elapsedRealtimeNanos;
                    if (deltaNs < 0) deltaNs = 0;
                    ageMs = deltaNs / 1_000_000L;
                } else {
                    long deltaMs = nowWallMs - r.data.timeMillis;
                    if (deltaMs < 0) deltaMs = 0;
                    ageMs = deltaMs;
                }
                if (ageMs > maxAgeMs) continue; // çok eski
            }

            // 2) Doğruluk filtresi (politika ile)
            if (minAccuracyMeters != null) {
                final Float acc = r.data.accuracy;
                if (acc == null) {
                    if (!acceptNullAcc) continue; // katı politika: null kabul edilmez
                } else {
                    if (acc > minAccuracyMeters) continue; // çok dağınık
                }
            }

            return r; // en yeni geçerli konum
        }

        return LocationResult.error(LocationErrorCode.NO_VALID_LOCATION, "No valid location in ring buffer");
    }

    /** Uygulama kapanışı için kaynak kapanışı. */
    @WorkerThread
    public void shutdown() {
        stop();
        workerThread.quitSafely();
    }
}
