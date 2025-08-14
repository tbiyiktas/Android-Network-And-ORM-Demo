package lib.location;


import androidx.annotation.NonNull;

public interface LocationUpdateListener {
    void onLocation(@NonNull LocationData data);
    void onError(@NonNull LocationException error);
}
