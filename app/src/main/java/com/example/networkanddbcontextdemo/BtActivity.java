package com.example.networkanddbcontextdemo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.networkanddbcontextdemo.adapter.BtDeviceAdapter;

import java.util.ArrayList;
import java.util.List;

import lib.bt.android.AndroidBluetoothAdapter;
import lib.bt.android.BluetoothClient;
import lib.bt.callbacks.ScanCallback;
import lib.bt.interfaces.IBluetoothDevice;

public class BtActivity extends AppCompatActivity implements BtDeviceAdapter.OnDeviceClickListener {
    private static final String TAG = "BtActivity";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private BluetoothClient bluetoothClient;
    private AndroidBluetoothAdapter customAdapter;

    private TextView statusTextView;
    private Button scanButton;
    private Button cancelButton;
    private RecyclerView devicesRecyclerView;
    private ProgressBar progressBar;
    private BtDeviceAdapter deviceAdapter;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt);

        statusTextView = findViewById(R.id.statusTextView);
        scanButton = findViewById(R.id.scanButton);
        cancelButton = findViewById(R.id.cancelButton);
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView);
        progressBar = findViewById(R.id.progressBar);

        customAdapter = new AndroidBluetoothAdapter(this);
        bluetoothClient = new BluetoothClient(customAdapter, customAdapter, customAdapter);

        deviceAdapter = new BtDeviceAdapter(new ArrayList<>());
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(deviceAdapter);
        deviceAdapter.setOnDeviceClickListener(this);

        scanButton.setOnClickListener(v -> {
            if (checkPermissions()) {
                startScanning();
            }
        });

        cancelButton.setOnClickListener(v -> stopScanning());
    }

    private boolean checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationEnabled()) {
            Toast.makeText(this, "Bluetooth tarama için konum servisi açık olmalıdır.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
            return false;
        }

        return true;
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (checkPermissions()) {
                    startScanning();
                }
            } else {
                Toast.makeText(this, "Bluetooth taraması için gerekli izinler reddedildi.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startScanning() {
        statusTextView.setText("Cihazlar taranıyor...");
        scanButton.setEnabled(false);
        cancelButton.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        deviceAdapter.clearDevices();

        bluetoothClient.startScan(new ScanCallback() {
            @Override
            public void onScanStarted() {
                Log.d(TAG, "Tarama başlatıldı.");
            }

            @Override
            public void onDeviceFound(IBluetoothDevice device) {
                mainThreadHandler.post(() -> deviceAdapter.addDevice(device));
            }

            @Override
            public void onScanFinished(List<IBluetoothDevice> devices) {
                mainThreadHandler.post(() -> {
                    statusTextView.setText("Tarama tamamlandı. " + devices.size() + " cihaz bulundu.");
                    scanButton.setEnabled(true);
                    cancelButton.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                });
            }

            @Override
            public void onScanFailed(String errorMessage) {
                mainThreadHandler.post(() -> {
                    statusTextView.setText("Tarama başarısız: " + errorMessage);
                    scanButton.setEnabled(true);
                    cancelButton.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                });
            }
        });
    }

    private void stopScanning() {
        bluetoothClient.stopScan();
        statusTextView.setText("Tarama durduruldu.");
        scanButton.setEnabled(true);
        cancelButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onDeviceClick(IBluetoothDevice device) {
        bluetoothClient.stopScan();

        if (bluetoothClient.isDevicePaired(device)) {
            Log.d(TAG, device.getName() + " zaten eşleşmiş. BtControlActivity başlatılıyor.");
            Intent intent = new Intent(this, BtControlActivity.class);
            intent.putExtra(BtControlActivity.EXTRA_DEVICE_ADDRESS, device.getAddress());
            startActivity(intent);
        } else {
            Log.d(TAG, device.getName() + " eşleşmemiş. BtPairingActivity başlatılıyor.");
            Intent intent = new Intent(this, BtPairingActivity.class);
            intent.putExtra(BtPairingActivity.EXTRA_DEVICE_ADDRESS, device.getAddress());
            startActivity(intent);
        }
    }
}