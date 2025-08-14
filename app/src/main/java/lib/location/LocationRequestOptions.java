package lib.location;

import androidx.annotation.NonNull;

public class LocationRequestOptions {
    public enum Priority { HIGH_ACCURACY, BALANCED, LOW_POWER, PASSIVE }

    @NonNull public final Priority priority;
    public final long intervalMs;
    public final long fastestIntervalMs;
    public final float minDistanceMeters;
    public final long timeoutMs;
    public final boolean useForegroundService;

    private LocationRequestOptions(Builder b) {
        this.priority = b.priority;
        this.intervalMs = b.intervalMs;
        this.fastestIntervalMs = b.fastestIntervalMs;
        this.minDistanceMeters = b.minDistanceMeters;
        this.timeoutMs = b.timeoutMs;
        this.useForegroundService = b.useForegroundService;
    }

    public static class Builder {
        private Priority priority = Priority.BALANCED;
        private long intervalMs = 10_000;
        private long fastestIntervalMs = 5_000;
        private float minDistanceMeters = 0f;
        private long timeoutMs = 15_000;
        private boolean useForegroundService = false;

        public Builder priority(@NonNull Priority p){ this.priority = p; return this; }
        public Builder intervalMs(long v){ this.intervalMs = v; return this; }
        public Builder fastestIntervalMs(long v){ this.fastestIntervalMs = v; return this; }
        public Builder minDistanceMeters(float v){ this.minDistanceMeters = v; return this; }
        public Builder timeoutMs(long v){ this.timeoutMs = v; return this; }
        public Builder useForegroundService(boolean v){ this.useForegroundService = v; return this; }

        public LocationRequestOptions build(){ return new LocationRequestOptions(this); }
    }
}
