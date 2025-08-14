// lib/camera/CameraResult.java
package lib.camera;

import android.net.Uri;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Nullable;

public class CameraResult implements Parcelable {
    private final boolean success;
    @Nullable private final String message;

    // Dosya/metaveri
    @Nullable private final String uriString;     // content://...  (her zaman önerilir)
    @Nullable private final String mimeType;      // image/jpeg, image/png
    @Nullable private final String fileName;      // IMG_2025...jpg
    @Nullable private final String relativePath;  // API 29+: "Pictures/YourApp"
    @Nullable private final String absolutePath;  // API ≤28: /storage/emulated/0/Pictures/YourApp/...
    private final int width;                      // bitmap genişlik
    private final int height;                     // bitmap yükseklik
    private final long sizeBytes;                 // MediaStore.SIZE veya File.length()

    public static CameraResult success(
            Uri uri,
            String mimeType,
            String fileName,
            @Nullable String relativePath,
            @Nullable String absolutePath,
            int width,
            int height,
            long sizeBytes
    ) {
        return new CameraResult(
                true, null,
                uri != null ? uri.toString() : null,
                mimeType, fileName, relativePath, absolutePath,
                width, height, sizeBytes
        );
    }

    public static CameraResult failure(String message) {
        return new CameraResult(false, message, null, null, null, null, null, 0, 0, -1);
    }

    private CameraResult(boolean success, @Nullable String message,
                         @Nullable String uriString, @Nullable String mimeType,
                         @Nullable String fileName, @Nullable String relativePath,
                         @Nullable String absolutePath, int width, int height, long sizeBytes) {
        this.success = success;
        this.message = message;
        this.uriString = uriString;
        this.mimeType = mimeType;
        this.fileName = fileName;
        this.relativePath = relativePath;
        this.absolutePath = absolutePath;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
    }

    // Getters
    public boolean isSuccess() { return success; }
    @Nullable public String getMessage() { return message; }
    @Nullable public String getUriString() { return uriString; }
    @Nullable public Uri getUri() { return uriString != null ? Uri.parse(uriString) : null; }
    @Nullable public String getMimeType() { return mimeType; }
    @Nullable public String getFileName() { return fileName; }
    @Nullable public String getRelativePath() { return relativePath; }
    @Nullable public String getAbsolutePath() { return absolutePath; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getSizeBytes() { return sizeBytes; }

    // Parcelable
    protected CameraResult(Parcel in) {
        this.success = in.readByte() != 0;
        this.message = in.readString();
        this.uriString = in.readString();
        this.mimeType = in.readString();
        this.fileName = in.readString();
        this.relativePath = in.readString();
        this.absolutePath = in.readString();
        this.width = in.readInt();
        this.height = in.readInt();
        this.sizeBytes = in.readLong();
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (success ? 1 : 0));
        dest.writeString(message);
        dest.writeString(uriString);
        dest.writeString(mimeType);
        dest.writeString(fileName);
        dest.writeString(relativePath);
        dest.writeString(absolutePath);
        dest.writeInt(width);
        dest.writeInt(height);
        dest.writeLong(sizeBytes);
    }
    @Override public int describeContents() { return 0; }

    public static final Creator<CameraResult> CREATOR = new Creator<CameraResult>() {
        @Override public CameraResult createFromParcel(Parcel in) { return new CameraResult(in); }
        @Override public CameraResult[] newArray(int size) { return new CameraResult[size]; }
    };
}
