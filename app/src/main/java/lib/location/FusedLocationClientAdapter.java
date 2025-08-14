package lib.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FusedLocationClientAdapter implements ILocationClient {

    private final FusedLocationProviderClient fused;
    private final SettingsClient settings;
    // Thread-safe aktif abonelikler
    private final Map<LocationSubscription, com.google.android.gms.location.LocationCallback> active = new ConcurrentHashMap<>();

    public FusedLocationClientAdapter(@NonNull Context ctx) {
        Context app = ctx.getApplicationContext();
        this.fused = LocationServices.getFusedLocationProviderClient(app);
        this.settings = LocationServices.getSettingsClient(app);
    }

    @Override
    public void getCurrentLocation(@NonNull Context context,
                                   @NonNull LocationRequestOptions options,
                                   @NonNull lib.location.LocationCallback cb) {

        if (!hasLocationPermission(context)) {
            cb.onError(new LocationException(LocationErrorCode.PERMISSION_DENIED, "Location permission missing"));
            return;
        }
        if (!isLocationEnabled(context)) {
            cb.onError(new LocationException(LocationErrorCode.PROVIDER_DISABLED, "Location providers disabled"));
            return;
        }

        com.google.android.gms.location.LocationRequest req = buildRequest(options);

        LocationSettingsRequest settingsReq = new LocationSettingsRequest.Builder()
                .addLocationRequest(req).build();

        settings.checkLocationSettings(settingsReq)
                .addOnSuccessListener(unused -> {
                    final Handler handler = new Handler(Looper.getMainLooper());
                    final Runnable timeout = () -> cb.onError(
                            new LocationException(LocationErrorCode.TIMEOUT, "Location request timed out")
                    );
                    handler.postDelayed(timeout, options.timeoutMs);

                    fused.getCurrentLocation(priorityToPs(options.priority), null)
                            .addOnSuccessListener(location -> {
                                handler.removeCallbacks(timeout);
                                if (location != null) {
                                    cb.onSuccess(map(location));
                                } else {
                                    // fallback: tek atımlık update
                                    requestSingleUpdate(options, cb, handler, timeout);
                                }
                            })
                            .addOnFailureListener(e -> {
                                handler.removeCallbacks(timeout);
                                requestSingleUpdate(options, cb, handler, timeout);
                            });

                })
                .addOnFailureListener(e ->
                        cb.onError(new LocationException(
                                LocationErrorCode.SETTINGS_CHANGE_REQUIRED,
                                "Location settings not satisfied", e))
                );
    }

    private void requestSingleUpdate(@NonNull LocationRequestOptions options,
                                     @NonNull lib.location.LocationCallback cb,
                                     @NonNull Handler handler,
                                     @NonNull Runnable timeout) {

        com.google.android.gms.location.LocationRequest req = buildRequest(options);

        // Google'ın LocationCallback'ını FQCN ile kullanıyoruz
        com.google.android.gms.location.LocationCallback oneShot =
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult result) {
                        handler.removeCallbacks(timeout);
                        fused.removeLocationUpdates(this);
                        if (result.getLastLocation() != null) {
                            cb.onSuccess(map(result.getLastLocation()));
                        } else {
                            cb.onError(new LocationException(LocationErrorCode.UNKNOWN, "Empty location result"));
                        }
                    }
                };

        fused.requestLocationUpdates(req, oneShot, Looper.getMainLooper())
                .addOnFailureListener(e -> {
                    handler.removeCallbacks(timeout);
                    cb.onError(new LocationException(LocationErrorCode.UNKNOWN, "requestLocationUpdates failed", e));
                });
    }

    @Override
    @NonNull
    public LocationSubscription startLocationUpdates(@NonNull Context context,
                                                     @NonNull LocationRequestOptions options,
                                                     @NonNull LocationUpdateListener listener) {
        LocationSubscription sub = new LocationSubscription();

        if (!hasLocationPermission(context)) {
            listener.onError(new LocationException(LocationErrorCode.PERMISSION_DENIED, "Location permission missing"));
            return sub;
        }
        if (!isLocationEnabled(context)) {
            listener.onError(new LocationException(LocationErrorCode.PROVIDER_DISABLED, "Location providers disabled"));
            return sub;
        }

        com.google.android.gms.location.LocationRequest req = buildRequest(options);

        // Google callback (FQCN)
        com.google.android.gms.location.LocationCallback callback =
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult result) {
                        if (!sub.active) return;
                        if (result.getLastLocation() != null) {
                            listener.onLocation(map(result.getLastLocation()));
                        }
                    }

                    @Override
                    public void onLocationAvailability(@NonNull LocationAvailability availability) {
                        // İstersen availability.isLocationAvailable() == false için uyarı üret
                    }
                };

        // !!! Aktif haritaya ekleme sadece başarı sonrası !!!
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
                .addOnSuccessListener(v -> {
                    if (!sub.active) {
                        // Bu sırada stop() çağrılmışsa hemen kaldır ve map’e ekleme
                        fused.removeLocationUpdates(callback);
                        return;
                    }
                    active.put(sub, callback);
                })
                .addOnFailureListener(e ->
                        listener.onError(new LocationException(LocationErrorCode.UNKNOWN, "requestLocationUpdates failed", e))
                );

        return sub;
    }

    @Override
    public void stopLocationUpdates(@NonNull LocationSubscription subscription) {
        com.google.android.gms.location.LocationCallback cb = active.remove(subscription);
        subscription.active = false;
        if (cb != null) {
            fused.removeLocationUpdates(cb);
        }
        // Not: Eğer start başarısız olduysa veya başarı callback’i henüz gelmediyse,
        // map’te kayıt olmayabilir; bu durumda removeLocationUpdates yapacak callback yoktur.
    }

    @Override
    public void getLastKnownLocation(@NonNull Context context,
                                     @NonNull lib.location.LocationCallback cb) {
        if (!hasLocationPermission(context)) {
            cb.onError(new LocationException(LocationErrorCode.PERMISSION_DENIED, "Location permission missing"));
            return;
        }
        fused.getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc != null) cb.onSuccess(map(loc));
                    else cb.onError(new LocationException(LocationErrorCode.UNKNOWN, "No last known location"));
                })
                .addOnFailureListener(e ->
                        cb.onError(new LocationException(LocationErrorCode.UNKNOWN, "getLastLocation failed", e)));
    }

    @Override
    public boolean hasLocationPermission(@NonNull Context context) {
        int f = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION);
        int c = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION);
        return f == PackageManager.PERMISSION_GRANTED || c == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean isLocationEnabled(@NonNull Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        try {
            return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Exception e) { return false; }
    }

    private com.google.android.gms.location.LocationRequest buildRequest(@NonNull LocationRequestOptions o) {
        return new com.google.android.gms.location.LocationRequest.Builder(
                priorityToPs(o.priority), o.intervalMs)
                .setMinUpdateIntervalMillis(o.fastestIntervalMs)
                .setMinUpdateDistanceMeters(o.minDistanceMeters)
                .build();
    }

    private int priorityToPs(@NonNull LocationRequestOptions.Priority p) {
        switch (p) {
            case HIGH_ACCURACY: return Priority.PRIORITY_HIGH_ACCURACY;
            case LOW_POWER:     return Priority.PRIORITY_LOW_POWER;
            case PASSIVE:       return Priority.PRIORITY_PASSIVE;
            case BALANCED:
            default:            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
    }

    private static LocationData map(@NonNull android.location.Location l) {
        final Long tMonoNs = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                ? l.getElapsedRealtimeNanos()
                : null;

        return new LocationData(
                l.getLatitude(),
                l.getLongitude(),
                l.hasAccuracy() ? l.getAccuracy() : null,
                l.hasAltitude() ? l.getAltitude() : null,
                l.hasBearing() ? l.getBearing() : null,
                l.hasSpeed() ? l.getSpeed() : null,
                l.getTime(),      // wall clock (fallback)
                tMonoNs           // monotonik timestamp (tercih edilen)
        );
    }
}
