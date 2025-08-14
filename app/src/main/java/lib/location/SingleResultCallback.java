package lib.location;

import androidx.annotation.NonNull;

public interface SingleResultCallback<T> {
    void onResult(@NonNull LocationResult<T> result);
}
