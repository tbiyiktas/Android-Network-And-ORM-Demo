package com.example.networkanddbcontextdemo;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import lib.bt.client.IBluetoothClient;

public class BtActivity extends AppCompatActivity {

    private static final int RC_BT_PERMS  = 101;
    private static final int RC_ENABLE_BT = 102;

    private IBluetoothClient bt;

    private TextView statusTextView;
    private Button scanButton, cancelButton, saveButton;
    private ProgressBar progressBar;
    private RecyclerView devicesRecyclerView;
    private BtDeviceAdapter adapter;

    private boolean scanning = false;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt); // verdiğin ConstraintLayout

        bt = MyApplication.getBluetoothClient();

        statusTextView      = findViewById(R.id.statusTextView);
        scanButton          = findViewById(R.id.scanButton);
        cancelButton        = findViewById(R.id.cancelButton);
        saveButton          = findViewById(R.id.saveButton);
        progressBar         = findViewById(R.id.progressBar);
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView);

        // RecyclerView & adapter
        adapter = new BtDeviceAdapter(new ArrayList<>(), device -> {
            Intent it = new Intent(BtActivity.this, BtPairingActivity.class);
            it.putExtra(BtPairingActivity.EXTRA_DEVICE_ADDRESS, device.getAddress());
            it.putExtra(BtPairingActivity.EXTRA_DEVICE_NAME, device.getName());
            startActivity(it);
        });
        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        devicesRecyclerView.setAdapter(adapter);

        // Başlangıç UI durumu
        setScanning(false);
        saveButton.setVisibility(View.GONE); // bu ekranda kaydet kullanılmıyor

        // Bluetooth status takibi
        bt.onBluetoothStatusChanged(st -> {
            switch (st) {
                case ENABLED:
                    statusTextView.setText("Bluetooth açık");
                    break;
                case DISABLED:
                    statusTextView.setText("Bluetooth kapalı");
                    break;
                case ENABLING:
                    statusTextView.setText("Bluetooth açılıyor…");
                    break;
                case DISABLING:
                    statusTextView.setText("Bluetooth kapanıyor…");
                    break;
            }
        });
        // İlk durum:
        statusTextView.setText(bt.isBluetoothEnabled() ? "Bluetooth açık" : "Bluetooth kapalı");

        // Scan callback’leri
        bt.onDeviceFound(d -> adapter.addOrUpdate(d));
        bt.onDiscoveryFinished(() -> {
            scanning = false;
            setScanning(false);
            statusTextView.setText("Tarama bitti (" + adapter.getItemCount() + " cihaz)");
        });

        // Butonlar
        scanButton.setOnClickListener(v -> startScanWithGates());
        cancelButton.setOnClickListener(v -> cancelScan());
    }

    @Override protected void onStop() {
        super.onStop();
        if (scanning) {
            bt.stopScan();
            scanning = false;
        }
        setScanning(false);
    }

    // ================== Tarama Akışı ==================

    private void startScanWithGates() {
        // İzinler
        if (!hasAllBtPerms()) {
            ActivityCompat.requestPermissions(this, requiredPerms(), RC_BT_PERMS);
            return;
        }
        // Bluetooth açık mı?
        if (!isBluetoothEnabled()) {
            Intent enableBt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBt, RC_ENABLE_BT);
            return;
        }
        // (Gerekliyse) konum açık mı?
        if (requiresLocationForScan() && !isLocationEnabled()) {
            showLocationRequiredDialog();
            return;
        }
        // Hepsi tamam → taramayı başlat
        startScan();
    }

    private void startScan() {
        scanning = true;
        adapter.clear();
        setScanning(true);
        statusTextView.setText("Cihazlar taranıyor…");
        bt.startScan();
    }

    private void cancelScan() {
        bt.stopScan();
        scanning = false;
        setScanning(false);
        statusTextView.setText("Tarama iptal edildi");
    }

    private void setScanning(boolean on) {
        progressBar.setVisibility(on ? View.VISIBLE : View.GONE);
        scanButton.setEnabled(!on);
        cancelButton.setVisibility(on ? View.VISIBLE : View.GONE);
        cancelButton.setEnabled(on);
    }

    // ================== Permission / Activity sonuçları ==================

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == RC_BT_PERMS) {
            if (hasAllBtPerms()) {
                startScanWithGates();
            } else {
                toast("Bluetooth izinleri gerekli.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_ENABLE_BT) {
            if (isBluetoothEnabled()) {
                startScanWithGates();
            } else {
                toast("Bluetooth açılmadı.");
            }
        }
    }

    private void showLocationRequiredDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Konum Gerekli")
                .setMessage("Bazı cihazlarda tarama için konum servisleri açık olmalıdır.")
                .setPositiveButton("Ayarlar", (d, w) -> {
                    Intent i = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(i);
                })
                .setNegativeButton("İptal", null)
                .show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ================= Yardımcılar (birebir seninkiler) =================

    private boolean isBluetoothEnabled() {
        return bt.isBluetoothEnabled();
    }

    private boolean hasAllBtPerms() {
        for (String p : requiredPerms()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION // bazı cihazlarda discovery için gerekebiliyor
            };
        } else {
            return new String[]{ Manifest.permission.ACCESS_FINE_LOCATION };
        }
    }

    private boolean requiresLocationForScan() {
        // Üreticiye göre değişebiliyor; korumacı davranıyoruz.
        return true;
    }

    private boolean isLocationEnabled() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return false;
            boolean gps = false, net = false;
            try { gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignore) {}
            try { net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignore) {}
            return gps || net;
        } catch (Exception e) {
            return false;
        }
    }
}
