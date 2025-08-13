package com.example.networkanddbcontextdemo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import app.model.BtDevice;
import app.repositories.BtDeviceRepository;
import lib.bt.android.AndroidBluetoothAdapter;
import lib.bt.android.BluetoothClient;
import lib.bt.callbacks.PairingCallback;
import lib.bt.interfaces.IBluetoothDevice;
import lib.persistence.DbCallback;
import lib.persistence.DbResult;
import lib.persistence.RepositoryFactory;

public class BtPairingActivity extends AppCompatActivity {
    private static final String TAG = "BtPairingActivity";

    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    public static final String EXTRA_DEVICE_NAME = "device_name";

    private BluetoothClient bluetoothClient;

    private TextView deviceNameTextView;
    private TextView pairingStatusTextView;
    private ProgressBar pairingProgressBar;
    private Button saveButton;

    private IBluetoothDevice deviceToPair;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_btpairing);

        deviceNameTextView = findViewById(R.id.deviceNameTextView);
        pairingStatusTextView = findViewById(R.id.pairingStatusTextView);
        pairingProgressBar = findViewById(R.id.pairingProgressBar);
        saveButton = findViewById(R.id.saveButton);

        AndroidBluetoothAdapter customAdapter = new AndroidBluetoothAdapter(this);
        bluetoothClient = new BluetoothClient(customAdapter, customAdapter, customAdapter);

        Intent intent = getIntent();
        if (intent != null) {
            String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
            if (deviceAddress != null) {
                deviceToPair = bluetoothClient.getDeviceFromAddress(deviceAddress);
                if (deviceToPair != null) {
                    deviceNameTextView.setText(deviceToPair.getName() != null ? deviceToPair.getName() : deviceAddress);
                    startPairing(deviceToPair);
                } else {
                    Toast.makeText(this, "Geçersiz cihaz adresi.", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else {
                Toast.makeText(this, "Cihaz adresi yok.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }

        saveButton.setOnClickListener(v -> savePairedDevice());
    }

    private void startPairing(IBluetoothDevice device) {
        pairingStatusTextView.setText("Eşleştirme başlatılıyor...");
        pairingProgressBar.setVisibility(View.VISIBLE);
        saveButton.setVisibility(View.GONE);

        bluetoothClient.pairDevice(device, new PairingCallback() {
            @Override
            public void onPairingStarted(IBluetoothDevice startedDevice) {
                mainThreadHandler.post(() -> {
                    pairingStatusTextView.setText("Eşleşiyor...");
                    Log.d(TAG, startedDevice.getName() + " eşleştirme başladı.");
                });
            }

            @Override
            public void onPairingSuccess(IBluetoothDevice pairedDevice) {
                mainThreadHandler.post(() -> {
                    pairingStatusTextView.setText("Eşleştirme başarılı!");
                    pairingProgressBar.setVisibility(View.GONE);
                    saveButton.setVisibility(View.VISIBLE);
                    Toast.makeText(BtPairingActivity.this, "Cihaz eşleştirildi: " + pairedDevice.getName(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onPairingFailed(IBluetoothDevice failedDevice, String errorMessage) {
                mainThreadHandler.post(() -> {
                    pairingStatusTextView.setText("Eşleştirme başarısız: " + errorMessage);
                    pairingProgressBar.setVisibility(View.GONE);
                    Toast.makeText(BtPairingActivity.this, "Eşleştirme başarısız: " + errorMessage, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void savePairedDevice() {
        if (deviceToPair == null) {
            Toast.makeText(this, "Eşleştirilmiş bir cihaz yok.", Toast.LENGTH_SHORT).show();
            return;
        }
        BtDevice btDevice = new BtDevice();
        btDevice.name = deviceToPair.getName();
        btDevice.address = deviceToPair.getAddress();

        BtDeviceRepository btDeviceRepository = RepositoryFactory.getBtDeviceRepository(getApplicationContext());

        btDeviceRepository.deleteAll(new DbCallback<Integer>() {
            @Override
            public void onResult(DbResult<Integer> result) {
                if (result.isSuccess()) {
                    Log.d(TAG, "Önceki cihaz başarıyla silindi.");
                } else {
                    Log.e(TAG, "Önceki cihaz silinirken hata oluştu.");
                }

                btDeviceRepository.insert(btDevice, new DbCallback<BtDevice>() {
                    @Override
                    public void onResult(DbResult<BtDevice> result) {
                        if (result.isSuccess()) {
                            Toast.makeText(BtPairingActivity.this, deviceToPair.getName() + " kaydedildi.", Toast.LENGTH_LONG).show();

                            Intent controlIntent = new Intent(BtPairingActivity.this, BtControlActivity.class);
                            controlIntent.putExtra(BtControlActivity.EXTRA_DEVICE_ADDRESS, deviceToPair.getAddress());
                            startActivity(controlIntent);

                            finish();
                        } else {
                            Toast.makeText(BtPairingActivity.this, deviceToPair.getName() + " kaydedilemedi.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
}