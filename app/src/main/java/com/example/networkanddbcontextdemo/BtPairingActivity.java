package com.example.networkanddbcontextdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.util.List;

import app.model.BtDevice;
import app.repositories.BtDeviceRepository;
import lib.bt.client.IBluetoothClient;
import lib.bt.interfaces.IBluetoothDevice;
import lib.bt.model.BtResult;
import lib.persistence.DbCallback;
import lib.persistence.DbResult;
import lib.persistence.RepositoryFactory;

public class BtPairingActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ADDRESS = "extra_device_address";
    public static final String EXTRA_DEVICE_NAME    = "extra_device_name";

    private static final int RC_BT_CONNECT = 201;

    private IBluetoothClient bt;

    private TextView deviceNameTextView;
    private TextView pairingStatusTextView;
    private ProgressBar pairingProgressBar;
    private Button saveButton;
    private Button cancelButton;

    private String deviceAddress;
    private String deviceName;
    private IBluetoothDevice deviceToPair;

    private boolean pairingInProgress = false;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_btpairing);

        bt = MyApplication.getBluetoothClient();

        deviceNameTextView    = findViewById(R.id.deviceNameTextView);
        pairingStatusTextView = findViewById(R.id.pairingStatusTextView);
        pairingProgressBar    = findViewById(R.id.pairingProgressBar);
        saveButton            = findViewById(R.id.saveButton);
        cancelButton          = findViewById(R.id.cancelButton);

        Intent it = getIntent();
        deviceAddress = it.getStringExtra(EXTRA_DEVICE_ADDRESS);
        deviceName    = it.getStringExtra(EXTRA_DEVICE_NAME);

        if (deviceAddress == null || deviceAddress.isEmpty()) {
            toast("Geçersiz cihaz adresi");
            finish(); return;
        }

        deviceNameTextView.setText(deviceName != null && !deviceName.isEmpty() ? deviceName : deviceAddress);
        setBusy(false);
        showSave(false);

        // Pairing durumlarını dinle (yalnızca hedef cihaza tepki ver)
        bt.onPairingStatusChanged(ps -> {
            if (ps.getDevice() == null || ps.getDevice().getAddress() == null) return;
            if (!deviceAddress.equals(ps.getDevice().getAddress())) return;

            if (ps.isPaired()) {
                pairingStatusTextView.setText("Eşleştirme başarılı");
                pairingInProgress = false;
                setBusy(false);
                showSave(true);
            } else if (ps.isFinal()) {
                pairingStatusTextView.setText("Eşleştirme başarısız");
                pairingInProgress = false;
                setBusy(false);
                showSave(false);
            } else {
                pairingStatusTextView.setText("Eşleştiriliyor…");
            }
        });

        // Butonlar
        cancelButton.setOnClickListener(v -> {
            if (pairingInProgress) {
                bt.cancelPairing();
                bt.stopListeningForPairingStatus();
                pairingInProgress = false;
                setBusy(false);
                showSave(false);
                pairingStatusTextView.setText("İptal edildi");
            } else {
                finish();
            }
        });

        saveButton.setOnClickListener(v -> savePairedDevice());

        // İzin kapısı → zaten eşleşmiş mi? → değilse eşleştirmeyi başlat
        ensureConnectPermThenStart();
    }

    @Override protected void onStop() {
        super.onStop();
        // Yayıncıları kapatmak için
        bt.stopListeningForPairingStatus();
        pairingInProgress = false;
        setBusy(false);
    }

    // ---------- Akış ----------

    private void ensureConnectPermThenStart() {
        if (!hasBtConnectPerm()) {
            ActivityCompat.requestPermissions(this, requiredPairingPerms(), RC_BT_CONNECT);
            return;
        }
        if (isAlreadyPaired()) {
            pairingStatusTextView.setText("Zaten eşleşmiş");
            showSave(true);
        } else {
            startPairing();
        }
    }

    private void startPairing() {
        // Sadece adres/ad ile basit bir cihaz nesnesi
        deviceToPair = new SimpleDevice(deviceAddress, deviceName);

        setBusy(true);
        showSave(false);
        pairingStatusTextView.setText("Eşleştiriliyor…");
        pairingInProgress = true;

        bt.pairAsync(deviceToPair, (BtResult<IBluetoothDevice> res) -> {
            pairingInProgress = false;
            if (res.isSuccess()) {
                pairingStatusTextView.setText("Eşleştirme başarılı");
                setBusy(false);
                showSave(true);
            } else if (res.isCancelled()) {
                pairingStatusTextView.setText("İşlem iptal/zaman aşımı");
                setBusy(false);
                showSave(false);
            } else {
                pairingStatusTextView.setText("Eşleştirme hatası");
                setBusy(false);
                showSave(false);
                Exception e = res.errorOrNull();
                if (e != null && e.getMessage() != null) toast(e.getMessage());
            }
        });
    }

    private boolean isAlreadyPaired() {
        List<IBluetoothDevice> bonded = bt.getPairedDevices(); // CONNECT izni gerekli olabilir
        if (bonded == null) return false;
        for (IBluetoothDevice d : bonded) {
            if (d != null && deviceAddress.equals(d.getAddress())) {
                deviceToPair = d;
                return true;
            }
        }
        return false;
    }

    // ---------- UI ----------

    private void setBusy(boolean inProgress) {
        pairingProgressBar.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        cancelButton.setVisibility(inProgress ? View.VISIBLE : View.GONE);
        cancelButton.setEnabled(inProgress);
        saveButton.setEnabled(!inProgress);
    }

    private void showSave(boolean show) {
        saveButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- Permission ----------

    private boolean hasBtConnectPerm() {
        for (String p : requiredPairingPerms()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredPairingPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{ Manifest.permission.BLUETOOTH_CONNECT };
        } else {
            // 31 altı için ek bir runtime izin gerekmez; eski cihazlarda pairing için genelde manifest yeter
            return new String[]{};
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == RC_BT_CONNECT) {
            if (hasBtConnectPerm()) {
                ensureConnectPermThenStart();
            } else {
                toast("Eşleştirme için Bluetooth izni gerekli.");
                finish();
            }
        }
    }

    // ---------- Kaydet (senin mevcut DB akışın) ----------
    private void savePairedDevice() {
        if (deviceToPair == null) {
            Toast.makeText(this, "Eşleştirilmiş bir cihaz yok.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Çift tıklamayı engelle
        saveButton.setEnabled(false);

        final BtDevice btDevice = new BtDevice();
        btDevice.name = deviceToPair.getName();
        btDevice.address = deviceToPair.getAddress();

        final BtDeviceRepository repo = RepositoryFactory.getBtDeviceRepository(getApplicationContext());
        if (repo == null) {
            runOnUiThread(() -> {
                saveButton.setEnabled(true);
                Toast.makeText(this, "Veritabanı hazır değil.", Toast.LENGTH_LONG).show();
            });
            return;
        }

        // 1) Öncekileri sil → 2) Ekle
        repo.deleteAll(new DbCallback<Integer>() {
            @Override
            public void onResult(DbResult<Integer> delResult) {
                // UI işlemlerini ana threade post et
                runOnUiThread(() -> {
                    if (!delResult.isSuccess()) {
                        // Silme başarısız olsa da eklemeyi deneyebiliriz (iş mantığına göre)
                        // İstersen burada return edip butonu açabilirsin.
                    }
                });

                repo.insert(btDevice, new DbCallback<BtDevice>() {
                    @Override
                    public void onResult(DbResult<BtDevice> insResult) {
                        runOnUiThread(() -> {
                            if (insResult.isSuccess()) {
                                Toast.makeText(BtPairingActivity.this,
                                        deviceToPair.getName() + " kaydedildi.",
                                        Toast.LENGTH_LONG).show();

                                Intent control = new Intent(BtPairingActivity.this, BtControlActivity.class);
                                control.putExtra(BtControlActivity.EXTRA_DEVICE_ADDRESS, deviceToPair.getAddress());
                                control.putExtra(BtControlActivity.EXTRA_DEVICE_NAME, deviceToPair.getName());
                                control.putExtra(BtControlActivity.EXTRA_AUTO_CONNECT, true); // istersen otomatik bağlansın
                                startActivity(control);

                                finish();
                            } else {
                                saveButton.setEnabled(true);
                                Toast.makeText(BtPairingActivity.this,
                                        deviceToPair.getName() + " kaydedilemedi.",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            }
        });
    }


    // ---------- Basit cihaz adaptörü ----------
    private static final class SimpleDevice implements IBluetoothDevice {
        private final String addr;
        private final String name;
        SimpleDevice(String addr, String name) { this.addr = addr; this.name = name; }
        @Override public String getAddress() { return addr; }
        @Override public String getName() { return (name != null && !name.isEmpty()) ? name : addr; }
        @Override public Type getType() { return Type.UNKNOWN; }
        @Override public BondState getBondState() { return BondState.NONE; }
    }
}
