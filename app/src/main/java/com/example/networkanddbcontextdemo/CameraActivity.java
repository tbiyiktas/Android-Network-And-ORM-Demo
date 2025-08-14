package com.example.networkanddbcontextdemo;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;

import lib.camera.CameraResult;

public class CameraActivity  extends ComponentActivity {

    private ImageView imageView;

//    private final ActivityResultLauncher<android.content.Intent> cameraLauncher =
//            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
//                if (result.getResultCode() == RESULT_OK) {
//                    Uri photo = CameraCaptureActivity.getResultUri(result.getData());
//                    if (photo != null) {
//                        imageView.setImageURI(photo);
//                    }
//                }
//            });

    private final ActivityResultLauncher<android.content.Intent> cameraLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                CameraResult cam = CameraCaptureActivity.getCameraResult(result.getData());
                if (result.getResultCode() == RESULT_OK && cam != null && cam.isSuccess()) {
                    // Görseli göster
                    ImageView img = findViewById(R.id.imageView);
                    if (cam.getUri() != null) img.setImageURI(cam.getUri());

                    // İstersen dosya bilgilerini kullan
                    // cam.getFileName(), cam.getRelativePath(), cam.getAbsolutePath(), cam.getSizeBytes() ...
                } else {
                    // İptal / hata
                    if (cam != null && !cam.isSuccess()) {
                        Toast.makeText(this, cam.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        Button btnOpenCamera = findViewById(R.id.btnOpenCamera);
        imageView = findViewById(R.id.imageView);

        btnOpenCamera.setOnClickListener(v ->
                cameraLauncher.launch(CameraCaptureActivity.createIntent(this))
        );
    }
}