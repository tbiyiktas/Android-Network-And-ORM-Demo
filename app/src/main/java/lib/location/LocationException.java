package lib.location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LocationException extends Exception {
    public final LocationErrorCode code;

    public LocationException(@NonNull LocationErrorCode code, @NonNull String message) {
        super(message);
        this.code = code;
    }

    public LocationException(@NonNull LocationErrorCode code, @NonNull String message, @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}