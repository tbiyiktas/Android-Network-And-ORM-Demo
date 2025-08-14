package lib.camera;


import androidx.annotation.NonNull;

public final class CameraKit {
    private static CameraOptions options;

    private CameraKit() {}

    public static synchronized void init(@NonNull CameraOptions opts) {
        options = opts;
    }

    public static synchronized CameraOptions get() {
        if (options == null) {
            options = new CameraOptions.Builder().build(); // güvenli varsayılanlar
        }
        return options;
    }
}
