package lib.location;

import androidx.annotation.Nullable;

public class LocationData {
    public final double latitude;
    public final double longitude;
    @Nullable public final Float accuracy;   // metres
    @Nullable public final Double altitude;  // metres
    @Nullable public final Float bearing;    // degrees
    @Nullable public final Float speed;      // m/s

    /** Wall clock (UTC) — sadece geriye uyumluluk için tutuluyor */
    public final long timeMillis;
    /**
     * Monotonik saat damgası (elapsedRealtimeNanos).
     * Varsa yaş hesaplamasında **bunu** kullanacağız.
     */
    @Nullable public final Long elapsedRealtimeNanos;
    public LocationData(double lat, double lon, @Nullable Float accuracy, @Nullable Double altitude,
                        @Nullable Float bearing, @Nullable Float speed, long timeMillis, @Nullable Long elapsedRealtimeNanos) {
        this.latitude = lat;
        this.longitude = lon;
        this.accuracy = accuracy;
        this.altitude = altitude;
        this.bearing = bearing;
        this.speed = speed;
        this.timeMillis = timeMillis;
        this.elapsedRealtimeNanos = elapsedRealtimeNanos;
    }

    @Override
    public String toString() {
        return "LocationData{" +
                "lat=" + latitude +
                ", lon=" + longitude +
                ", acc=" + accuracy +
                ", t=" + timeMillis +
                ", tMonoNs=" + elapsedRealtimeNanos +
                '}';
    }
}
