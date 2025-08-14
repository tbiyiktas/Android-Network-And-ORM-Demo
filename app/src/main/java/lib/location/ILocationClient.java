package lib.location;

import android.content.Context;

import androidx.annotation.NonNull;

public interface ILocationClient {

    void getCurrentLocation(
            @NonNull Context context,
            @NonNull LocationRequestOptions options,
            @NonNull lib.location.LocationCallback callback
    );

    @NonNull
    LocationSubscription startLocationUpdates(
            @NonNull Context context,
            @NonNull LocationRequestOptions options,
            @NonNull LocationUpdateListener listener
    );

    void stopLocationUpdates(@NonNull LocationSubscription subscription);

    void getLastKnownLocation(
            @NonNull Context context,
            @NonNull lib.location.LocationCallback callback
    );

    boolean hasLocationPermission(@NonNull Context context);
    boolean isLocationEnabled(@NonNull Context context);
}
