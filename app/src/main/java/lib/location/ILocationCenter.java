package lib.location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public interface ILocationCenter {
    void start(@NonNull LocationRequestOptions options);
    void stop();
    void requestOneShot(@NonNull LocationRequestOptions options);

    @NonNull List<LocationResult<LocationData>> getHistoryNewestFirst();
    int getHistoryCapacity();
    void setHistoryCapacity(int n);
    boolean isRunning();

    /** Son GEÇERLİ (SUCCESS) konumu (filtre yok). */
    @NonNull LocationResult<LocationData> getLatestValid();

    /**
     * Filtreli son GEÇERLİ konum.
     * @param maxAgeMs        Sonucun azami yaşı (ms). <=0 ise yaş filtresi uygulanmaz.
     * @param minAccuracyMeters Minimum kabul edilen doğruluk (metre). null ise doğruluk filtresi uygulanmaz.
     */
    @NonNull LocationResult<LocationData> getLatestValid(long maxAgeMs, @Nullable Float minAccuracyMeters);
}
