package lib.camera;

import android.os.Build;
import androidx.annotation.NonNull;

public final class CameraOptions {
    /** MediaStore RELATIVE_PATH (API 29+) veya P ve altı için Pictures alt klasörü adı */
    private final String relativePath;  // Örn: "Pictures/ExamCompass"
    /** Dosya adı prefix: IMG_, EC_, etc. */
    private final String filePrefix;
    /** Dosya adı tarih deseni */
    private final String datePattern;   // Örn: "yyyyMMdd_HHmmss"
    /** MIME türü (şimdilik image/jpeg önerilir) */
    private final String mimeType;      // "image/jpeg" | "image/png"
    /** JPEG kalite (1..100) sadece image/jpeg için */
    private final int jpegQuality;
    /** Çekim sonrası yeniden çekmeye izin verilsin mi (UI davranışı) */

    private final int maxDimension;  // uzun kenar en fazla 1600px
    private final boolean forcePortraitIfNeeded;

    private final boolean allowRetake;

    private CameraOptions(Builder b) {
        this.relativePath = b.relativePath;
        this.filePrefix   = b.filePrefix;
        this.datePattern  = b.datePattern;
        this.mimeType     = b.mimeType;
        this.jpegQuality  = b.jpegQuality;
        this.allowRetake  = b.allowRetake;
        this.maxDimension = b.maxDimension;
        this.forcePortraitIfNeeded = b.forcePortraitIfNeeded;
    }

    public String getRelativePath() { return relativePath; }
    public String getFilePrefix()   { return filePrefix; }
    public String getDatePattern()  { return datePattern; }
    public String getMimeType()     { return mimeType; }
    public int getJpegQuality()     { return jpegQuality; }
    public boolean isAllowRetake()  { return allowRetake; }

    public  int getMaxDimension(){return maxDimension; }
    public  boolean getForcePortraitIfNeeded(){return  forcePortraitIfNeeded;}

    public static class Builder {
        private String relativePath = defaultRelativePath();
        private String filePrefix   = "IMG_";
        private String datePattern  = "yyyyMMdd_HHmmss";
        private String mimeType     = "image/jpeg";
        private int jpegQuality     = 92;
        private int maxDimension = 1600;   // uzun kenar en fazla 1600px
        private boolean forcePortraitIfNeeded;
        private boolean allowRetake = true;

        public Builder relativePath(@NonNull String rp) { this.relativePath = rp; return this; }
        public Builder filePrefix(@NonNull String p)    { this.filePrefix = p; return this; }
        public Builder datePattern(@NonNull String d)   { this.datePattern = d; return this; }
        public Builder mimeType(@NonNull String m)      { this.mimeType = m; return this; }
        public Builder jpegQuality(int q)               { this.jpegQuality = q; return this; }
        public Builder allowRetake(boolean v)           { this.allowRetake = v; return this; }
        public Builder maxDimension(int d)              {this.maxDimension = d; return  this;}
        public Builder forcePortraitIfNeeded(boolean b)  {this.forcePortraitIfNeeded = b; return  this;}

        public CameraOptions build()                    { return new CameraOptions(this); }

        private static String defaultRelativePath() {
            // Varsayılan: Pictures/YourApp
            // API29+ için RELATIVE_PATH; altı için Environment.DIRECTORY_PICTURES/YourApp kullanılacak
            return (Build.VERSION.SDK_INT >= 29) ? "Pictures/YourApp" : "Pictures/YourApp";
        }
    }
}
