package lib.location;

import androidx.annotation.NonNull;

public interface LocationCallback {
    void onSuccess(@NonNull LocationData data);
    void onError(@NonNull LocationException error);
}