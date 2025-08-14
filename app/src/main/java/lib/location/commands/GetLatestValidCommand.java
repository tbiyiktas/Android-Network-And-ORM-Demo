package lib.location.commands;



import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import lib.location.ILocationCenter;
import lib.location.LocationData;
import lib.location.LocationResult;
import lib.location.SingleResultCallback;


public class GetLatestValidCommand extends LocationCommand {
    private final long maxAgeMs;
    @Nullable private final Float minAccuracyMeters;
    private final SingleResultCallback<LocationData> callback;

    public GetLatestValidCommand(@Nullable Looper deliverOn,
                                 long maxAgeMs,
                                 @Nullable Float minAccuracyMeters,
                                 @NonNull SingleResultCallback<LocationData> callback) {
        super(deliverOn);
        this.maxAgeMs = maxAgeMs;
        this.minAccuracyMeters = minAccuracyMeters;
        this.callback = callback;
    }

    @Override
    public void execute(@NonNull ILocationCenter center) {
        if (isCanceled()) return;
        LocationResult<LocationData> r = center.getLatestValid(maxAgeMs, minAccuracyMeters);
        deliverResult(r, callback);
    }
}
