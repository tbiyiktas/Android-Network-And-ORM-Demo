package lib.location.commands;


import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import lib.location.LocationData;
import lib.location.LocationRequestOptions;
import lib.location.SingleResultCallback;


public final class LocationCommands {
    private LocationCommands() {}

    // GetLatestValid (default: main thread teslimi)
    @NonNull
    public static GetLatestValidCommand getLatestValid(long maxAgeMs,
                                                       @Nullable Float minAccuracyMeters,
                                                       @NonNull SingleResultCallback<LocationData> cb) {
        return new GetLatestValidCommand(null, maxAgeMs, minAccuracyMeters, cb);
    }

    @NonNull
    public static GetLatestValidCommand getLatestValid(@Nullable Looper deliverOn,
                                                       long maxAgeMs,
                                                       @Nullable Float minAccuracyMeters,
                                                       @NonNull SingleResultCallback<LocationData> cb) {
        return new GetLatestValidCommand(deliverOn, maxAgeMs, minAccuracyMeters, cb);
    }

    // Start/Stop
    @NonNull
    public static StartUpdatesCommand start(@NonNull LocationRequestOptions opts) {
        return new StartUpdatesCommand(null, opts);
    }

    @NonNull
    public static StartUpdatesCommand start(@Nullable Looper deliverOn, @NonNull LocationRequestOptions opts) {
        return new StartUpdatesCommand(deliverOn, opts);
    }

    @NonNull
    public static StopUpdatesCommand stop() {
        return new StopUpdatesCommand(null);
    }

    @NonNull
    public static StopUpdatesCommand stop(@Nullable Looper deliverOn) {
        return new StopUpdatesCommand(deliverOn);
    }
}
