package com.example.networkanddbcontextdemo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;

import lib.bt.android.AndroidBluetoothAdapter;
import lib.bt.android.BluetoothClient;
import lib.bt.interfaces.IBluetoothConnector_old;
import lib.bt.interfaces.IBluetoothDevice;

public class BtControlActivity extends AppCompatActivity {
    private static final String TAG = "BtControlActivity";
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";

    private BluetoothClient bluetoothClient;

    private TextView deviceNameTextView;
    private TextView connectionStatusTextView;
    private TextView receivedMessagesTextView;
    private Button connectButton;
    private Button disconnectButton;
    private EditText messageEditText;
    private Button sendButton;

    private String deviceAddress;
    private IBluetoothDevice connectedDevice;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt_control);

        deviceNameTextView = findViewById(R.id.deviceNameTextView);
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);
        receivedMessagesTextView = findViewById(R.id.receivedMessagesTextView);
        connectButton = findViewById(R.id.connectButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);

        AndroidBluetoothAdapter customAdapter = new AndroidBluetoothAdapter(this);
        bluetoothClient = new BluetoothClient(customAdapter, customAdapter, customAdapter);

        Intent intent = getIntent();
        if (intent != null) {
            deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
            if (deviceAddress != null) {
                connectedDevice = bluetoothClient.getDeviceFromAddress(deviceAddress);
                if (connectedDevice != null) {
                    deviceNameTextView.setText(connectedDevice.getName());
                    connectButton.setEnabled(true);
                } else {
                    deviceNameTextView.setText("Bilinmeyen Cihaz");
                    connectButton.setEnabled(false);
                }
            } else {
                Toast.makeText(this, "Geçerli bir cihaz adresi yok.", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Toast.makeText(this, "Bağlanacak cihaz bilgisi yok.", Toast.LENGTH_SHORT).show();
            finish();
        }

        bluetoothClient.onConnectionStatusChanged(this::updateUIForConnectionStatus);

        bluetoothClient.onDataReceived(data -> {
            mainThreadHandler.post(() -> {
                String message = new String(data, StandardCharsets.UTF_8);
                receivedMessagesTextView.append("Gelen: " + message + "\n");
                Log.d(TAG, "Gelen veri: " + message);
            });
        });

        connectButton.setOnClickListener(v -> {
            if (deviceAddress != null) {
                bluetoothClient.connect(deviceAddress);
            }
        });

        disconnectButton.setOnClickListener(v -> bluetoothClient.disconnect());

        sendButton.setOnClickListener(v -> {
            String message = messageEditText.getText().toString();
            if (!message.isEmpty() && bluetoothClient.isConnected()) {
                //bluetoothClient.sendData(message.getBytes(StandardCharsets.UTF_8));
                printText(message);
                messageEditText.setText("");
                receivedMessagesTextView.append("Giden: " + message + "\n");
            } else {
                Toast.makeText(this, "Bağlı değil veya mesaj boş.", Toast.LENGTH_SHORT).show();
            }
        });

        updateUIForConnectionStatus(new IBluetoothConnector_old.ConnectionStatus(connectedDevice, false, null));
    }

    private void updateUIForConnectionStatus(IBluetoothConnector_old.ConnectionStatus status) {
        mainThreadHandler.post(() -> {
            if (status.isConnected()) {
                connectionStatusTextView.setText("Durum: Bağlı");
                connectButton.setVisibility(View.GONE);
                disconnectButton.setVisibility(View.VISIBLE);
                messageEditText.setEnabled(true);
                sendButton.setEnabled(true);
                Toast.makeText(this, status.getDevice().getName() + " bağlandı.", Toast.LENGTH_SHORT).show();
            } else {
                connectionStatusTextView.setText("Durum: Bağlantı kesik");
                connectButton.setVisibility(View.VISIBLE);
                disconnectButton.setVisibility(View.GONE);
                messageEditText.setEnabled(false);
                sendButton.setEnabled(false);
                if (status.getErrorMessage() != null && !status.getErrorMessage().isEmpty()) {
                    Toast.makeText(this, "Bağlantı hatası: " + status.getErrorMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothClient != null) {
            bluetoothClient.disconnect();
            bluetoothClient.shutdown();
        }
    }

    // Yeni yazıcı komutlarını içeren metot
    private void printText(String textToPrint) {
        // ESC/POS komutları (Bixolon SPP-R310 çoğu ESC/POS komutuyla uyumludur)

        // Yazıcıyı başlatma komutu (temiz bir sayfa için)
        byte[] initPrinter = {0x1B, 0x40}; // ESC @

        // Yazdırılacak metin
        byte[] textBytes = textToPrint.getBytes(StandardCharsets.UTF_8);

        // Metinden sonra biraz boşluk bırakmak için satır atlama (line feed)
        byte[] lineFeeds = {0x0A, 0x0A, 0x0A}; // LF, LF, LF

        // Kağıdı kesme komutu (tam kesim)
        byte[] paperCut = {0x1D, 0x56, 0x00}; // GS V 0

        // Tüm komutları ve metni tek bir byte dizisinde birleştirme
        byte[] combinedCommand = new byte[initPrinter.length + textBytes.length + lineFeeds.length + paperCut.length];

        System.arraycopy(initPrinter, 0, combinedCommand, 0, initPrinter.length);
        System.arraycopy(textBytes, 0, combinedCommand, initPrinter.length, textBytes.length);
        System.arraycopy(lineFeeds, 0, combinedCommand, initPrinter.length + textBytes.length, lineFeeds.length);
        System.arraycopy(paperCut, 0, combinedCommand, initPrinter.length + textBytes.length + lineFeeds.length, paperCut.length);

        // Yazıcıya birleştirilmiş komutu gönderin
        bluetoothClient.sendData(combinedCommand);
    }
}