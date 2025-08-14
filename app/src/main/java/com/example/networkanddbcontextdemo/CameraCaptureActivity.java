package com.example.networkanddbcontextdemo;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import lib.camera.CameraKit;
import lib.camera.CameraOptions;
import lib.camera.CameraResult;

public class CameraCaptureActivity extends ComponentActivity {

    public static final String EXTRA_PHOTO_URI = "extra_photo_uri";           // backward compat
    public static final String EXTRA_CAMERA_RESULT = "extra_camera_result";   // yeni

    public static Intent createIntent(android.content.Context ctx) {
        return new Intent(ctx, CameraCaptureActivity.class);
    }

    public static @Nullable Uri getResultUri(@Nullable Intent data) {
        if (data == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return data.getParcelableExtra(EXTRA_PHOTO_URI, Uri.class);
        } else {
            @SuppressWarnings("deprecation")
            Uri u = data.getParcelableExtra(EXTRA_PHOTO_URI);
            return u;
        }
    }

    public static @Nullable CameraResult getCameraResult(@Nullable Intent data) {
        if (data == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return data.getParcelableExtra(EXTRA_CAMERA_RESULT, CameraResult.class);
        } else {
            @SuppressWarnings("deprecation")
            CameraResult r = data.getParcelableExtra(EXTRA_CAMERA_RESULT);
            return r;
        }
    }

    private CameraOptions opts;

    // UI
    private ImageView preview;
    private Button btnRetake, btnSave, btnBack;
    private SeekBar sbScale, sbQuality;
    private TextView tvScale, tvQuality, tvInfo;

    // Bitmaps
    private Bitmap baseBitmap;   // normalize edilmiş, kullanıcı ayarlarının kaynağı
    private Bitmap lastBitmap;   // kullanıcı ayarlarına göre ölçeklenmiş (önizleme + kaydedilecek)

    // Launchers
    private ActivityResultLauncher<String[]> permLauncher;
    private ActivityResultLauncher<Void> takePreview;

    // Kullanıcı seçimleri
    private int scalePercent;     // 25..100
    private int qualityPercent;   // 50..100 (sadece JPEG anlamlı)

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        opts = CameraKit.get();

        // ---- UI (programatik) ----
        int p = (int) (16 * getResources().getDisplayMetrics().density);

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

        btnRetake = new Button(this); btnRetake.setText("Yeniden Çek"); btnRetake.setEnabled(false);
        btnSave   = new Button(this); btnSave.setText("Kaydet");        btnSave.setEnabled(false);
        btnBack   = new Button(this); btnBack.setText("Geri Dön");

        sbScale   = new SeekBar(this);
        sbQuality = new SeekBar(this);
        tvScale   = new TextView(this);
        tvQuality = new TextView(this);
        tvInfo    = new TextView(this);

        // Ölçek: 25..100 (%)
        sbScale.setMax(100 - 25);
        sbScale.setProgress(100 - 25); // varsayılan 100%
        scalePercent = 100;

        // Kalite: 50..100
        sbQuality.setMax(100 - 50);
        int initialQ = Math.max(50, Math.min(100, opts.getJpegQuality()));
        sbQuality.setProgress(initialQ - 50);
        qualityPercent = initialQ;

        // JPEG dışı MIME ise kalite kontrolünü pasifleştir
        boolean isJpeg = "image/jpeg".equalsIgnoreCase(opts.getMimeType());
        sbQuality.setEnabled(isJpeg);
        tvQuality.setEnabled(isJpeg);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(p, p, p, p);

        // Önizleme
        panel.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bilgi satırı
        tvInfo.setTextSize(14f);
        panel.addView(tvInfo);

        // Ölçek kontrol satırı
        tvScale.setText("Ölçek: 100%");
        panel.addView(tvScale);
        panel.addView(sbScale);

        // Kalite kontrol satırı
        tvQuality.setText("Kalite (JPEG): " + qualityPercent + "%");
        panel.addView(tvQuality);
        panel.addView(sbQuality);

        // Butonlar
        panel.addView(btnRetake);
        panel.addView(btnSave);
        panel.addView(btnBack);

        setContentView(panel);
        // ---------------------------

        // Launchers
        takePreview = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bmp -> {
                    if (bmp != null) {
                        // Normalize: ops.maxDimension + forcePortraitIfNeeded
                        Bitmap normalized = normalizeBitmap(bmp, opts.getMaxDimension(), opts.getForcePortraitIfNeeded());
                        // Eski base'i düş
                        if (baseBitmap != null && !baseBitmap.isRecycled() && baseBitmap != bmp) baseBitmap.recycle();
                        baseBitmap = normalized;

                        // Kullanıcı ayarlarıyla önizleme üret
                        applyUserProcessing();

                        btnSave.setEnabled(true);
                        btnRetake.setEnabled(opts.isAllowRetake());
                    } else {
                        Toast.makeText(this, "Fotoğraf çekilemedi.", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_CANCELED,
                                new Intent().putExtra(EXTRA_CAMERA_RESULT, CameraResult.failure("Çekim başarısız")));
                        finish();
                    }
                });

        permLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean cameraOk = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                    boolean writeOk = Build.VERSION.SDK_INT > Build.VERSION_CODES.P
                            || Boolean.TRUE.equals(result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE));

                    if (cameraOk && writeOk) {
                        takePreview.launch(null);
                    } else {
                        Toast.makeText(this, "Gerekli izinler verilmedi.", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_CANCELED,
                                new Intent().putExtra(EXTRA_CAMERA_RESULT, CameraResult.failure("İzin reddedildi")));
                        finish();
                    }
                });

        // SeekBar listeners
        sbScale.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                scalePercent = 25 + progress; // 25..100
                tvScale.setText("Ölçek: " + scalePercent + "%");
                if (fromUser) applyUserProcessing();
            }
        });

        sbQuality.setOnSeekBarChangeListener(new SimpleSeek() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                qualityPercent = 50 + progress; // 50..100
                tvQuality.setText("Kalite (JPEG): " + qualityPercent + "%");
                if (fromUser && "image/jpeg".equalsIgnoreCase(opts.getMimeType())) {
                    // yalnızca bilgi güncelle (önizleme bitmap'i değişmez)
                    updateInfoLabel();
                }
            }
        });

        // Buton olayları
        btnRetake.setOnClickListener(v -> {
            if (opts.isAllowRetake()) takePreview.launch(null);
        });

        btnSave.setOnClickListener(v -> {
            if (lastBitmap != null) {
                Uri saved = saveToGallery(lastBitmap, qualityPercent);
                if (saved != null) {
                    CameraResult res = buildSuccessResult(saved, lastBitmap);
                    Intent out = new Intent()
                            .putExtra(EXTRA_PHOTO_URI, saved)      // backward compat
                            .putExtra(EXTRA_CAMERA_RESULT, res);   // yeni
                    setResult(RESULT_OK, out);
                    finish();
                } else {
                    Toast.makeText(this, "Kaydedilemedi.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED,
                            new Intent().putExtra(EXTRA_CAMERA_RESULT, CameraResult.failure("Kaydedilemedi")));
                    finish();
                }
            }
        });

        btnBack.setOnClickListener(v -> returnCanceled("Kullanıcı iptal etti"));

        // Donanımsal geri tuşu -> RESULT_CANCELED garantisi
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                returnCanceled("Kullanıcı iptal etti");
            }
        });

        requestCameraPermissionThenOpen();
    }

    private void requestCameraPermissionThenOpen() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permLauncher.launch(new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        } else {
            permLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    // --- Kullanıcı ayarlarını uygula ve önizlemeyi güncelle ---
    private void applyUserProcessing() {
        if (baseBitmap == null) return;

        // Hedef uzun kenar: opts.maxDimension * (scalePercent/100)
        int targetLong = Math.max(1, Math.round(opts.getMaxDimension() * (scalePercent / 100f)));

        Bitmap scaled = scaleDown(baseBitmap, targetLong);

        // Eski önizlemeyi bırak
        if (lastBitmap != null && lastBitmap != baseBitmap && !lastBitmap.isRecycled()) {
            lastBitmap.recycle();
        }
        lastBitmap = (scaled != null ? scaled : baseBitmap);
        preview.setImageBitmap(lastBitmap);

        // Bilgi etiketini güncelle (çözünürlük + tahmini boyut)
        updateInfoLabel();
    }

    private void updateInfoLabel() {
        if (lastBitmap == null) {
            tvInfo.setText("");
            return;
        }
        String dims = lastBitmap.getWidth() + " x " + lastBitmap.getHeight() + " px";
        String est  = estimateSizeString(lastBitmap, opts.getMimeType(), qualityPercent);
        tvInfo.setText("Çözünürlük: " + dims + "   •   Tahmini boyut: " + est);
    }

    private String estimateSizeString(Bitmap bmp, String mime, int quality) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if ("image/png".equalsIgnoreCase(mime)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos); // kalite yok sayılır
            } else {
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            }
            long bytes = bos.size();
            bos.close();
            return humanSize(bytes);
        } catch (Exception e) {
            return "—";
        }
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        float kb = bytes / 1024f;
        if (kb < 1024f) return String.format(Locale.US, "%.1f KB", kb);
        float mb = kb / 1024f;
        return String.format(Locale.US, "%.2f MB", mb);
    }

    private void returnCanceled(@Nullable String msg) {
        Intent out = new Intent();
        if (msg != null) {
            out.putExtra(EXTRA_CAMERA_RESULT, CameraResult.failure(msg));
        }
        setResult(RESULT_CANCELED, out);
        finish();
    }

    // --- Kaydetme ---

    private @Nullable Uri saveToGallery(Bitmap bmp, int jpegQualityToUse) {
        try {
            String time = new SimpleDateFormat(opts.getDatePattern(), Locale.US).format(new Date());
            String extension = opts.getMimeType().equalsIgnoreCase("image/png") ? ".png" : ".jpg";
            String fileName = opts.getFilePrefix() + time + extension;

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, opts.getMimeType());

            String absolutePath = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, opts.getRelativePath());
            } else {
                File pics = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                String sub = opts.getRelativePath().replace("Pictures/", "");
                File dir = new File(pics, sub);
                if (!dir.exists()) dir.mkdirs();
                absolutePath = new File(dir, fileName).getAbsolutePath();
                values.put(MediaStore.Images.Media.DATA, absolutePath);
            }

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) return null;

            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) return null;
                if (opts.getMimeType().equalsIgnoreCase("image/png")) {
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, os)) return null;
                } else {
                    int q = Math.max(1, Math.min(100, jpegQualityToUse));
                    if (!bmp.compress(Bitmap.CompressFormat.JPEG, q, os)) return null;
                }
            }
            return uri;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private CameraResult buildSuccessResult(Uri uri, Bitmap bmp) {
        String fileName = null;
        long size = -1L;
        try (Cursor c = getContentResolver().query(
                uri,
                new String[]{
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.SIZE
                },
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idxName = c.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                int idxSize = c.getColumnIndex(MediaStore.Images.Media.SIZE);
                if (idxName >= 0) fileName = c.getString(idxName);
                if (idxSize >= 0) size = c.getLong(idxSize);
            }
        } catch (Exception ignore) {}

        String absolutePath = null;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            String sub = opts.getRelativePath().replace("Pictures/", "");
            absolutePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    + File.separator + sub + File.separator + (fileName != null ? fileName : "");
        }

        return CameraResult.success(
                uri,
                opts.getMimeType(),
                fileName,
                opts.getRelativePath(),
                absolutePath,
                bmp.getWidth(),
                bmp.getHeight(),
                size
        );
    }

    // --- Görsel yardımcılar ---

    /** Uzun kenarı maxDim'e indirger; istenirse portre zorlar (normalize başlangıç). */
    private Bitmap normalizeBitmap(Bitmap src, int maxDim, boolean forcePortraitIfNeeded) {
        Bitmap scaled = scaleDown(src, maxDim);
        if (forcePortraitIfNeeded && scaled.getWidth() > scaled.getHeight()) {
            if (getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                Bitmap rotated = rotate(scaled, 90f);
                if (scaled != src && !scaled.isRecycled()) scaled.recycle();
                return rotated;
            }
        }
        return (scaled != null) ? scaled : src;
    }

    /** Uzun kenarı targetLong olacak şekilde ölçekler (aspect korunur). */
    private Bitmap scaleDown(Bitmap src, int targetLong) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longSide = Math.max(w, h);
        if (targetLong <= 0 || longSide <= targetLong) return src;
        float ratio = (float) targetLong / longSide;
        int nw = Math.round(w * ratio);
        int nh = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private Bitmap rotate(Bitmap src, float degrees) {
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    private abstract static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lastBitmap != null && !lastBitmap.isRecycled()) lastBitmap.recycle();
        if (baseBitmap != null && !baseBitmap.isRecycled()) baseBitmap.recycle();
    }
}
