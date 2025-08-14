package lib.location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class LocationResult<T> {
    public enum Status { SUCCESS, ERROR }

    @NonNull public final Status status;
    @Nullable public final T data;
    @Nullable public final LocationErrorCode errorCode;
    @Nullable public final String errorMessage;
    @Nullable public final Throwable error;
    public final long occurredAtUtcMillis;

    private LocationResult(@NonNull Status status, @Nullable T data,
                           @Nullable LocationErrorCode errorCode, @Nullable String errorMessage,
                           @Nullable Throwable error, long ts) {
        this.status = status;
        this.data = data;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.error = error;
        this.occurredAtUtcMillis = ts;
    }

    @NonNull
    public static <T> LocationResult<T> success(@NonNull T data) {
        return new LocationResult<>(Status.SUCCESS, data, null, null, null, System.currentTimeMillis());
    }

    @NonNull
    public static <T> LocationResult<T> error(@NonNull LocationErrorCode code, @NonNull String message) {
        return new LocationResult<>(Status.ERROR, null, code, message, null, System.currentTimeMillis());
    }

    @NonNull
    public static <T> LocationResult<T> error(@NonNull LocationErrorCode code, @NonNull String message, @Nullable Throwable error) {
        return new LocationResult<>(Status.ERROR, null, code, message, error, System.currentTimeMillis());
    }

    public boolean isSuccess() { return status == Status.SUCCESS; }

    @Override
    public String toString() {
        return "LocationResult{" + status +
                (isSuccess() ? ", data=" + data : ", error=" + errorCode + " " + errorMessage) +
                ", t=" + occurredAtUtcMillis + "}";
    }
}
