package com.example.networkanddbcontextdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;

import lib.bt.client.IBluetoothClient;
import lib.bt.interfaces.IBluetoothConnector;
import lib.bt.model.BtResult;

public class BtControlActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ADDRESS = "extra_device_address";
    public static final String EXTRA_DEVICE_NAME    = "extra_device_name";
    public static final String EXTRA_AUTO_CONNECT   = "extra_auto_connect"; // opsiyonel

    private static final int RC_BT_CONNECT = 301;

    private IBluetoothClient bt;

    private TextView deviceNameTextView;
    private TextView connectionStatusTextView;
    private TextView receivedMessagesTextView;
    private Button connectButton, disconnectButton, sendButton;
    private EditText messageEditText;

    private String deviceAddress;
    private String deviceName;

    private boolean connectedUI = false; // sadece UI durumunu tutuyoruz

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bt_control);

        bt = MyApplication.getBluetoothClient();

        // Views
        deviceNameTextView       = findViewById(R.id.deviceNameTextView);
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView);
        receivedMessagesTextView = findViewById(R.id.receivedMessagesTextView);
        connectButton            = findViewById(R.id.connectButton);
        disconnectButton         = findViewById(R.id.disconnectButton);
        sendButton               = findViewById(R.id.sendButton);
        messageEditText          = findViewById(R.id.messageEditText);

        // Extras
        Intent it = getIntent();
        deviceAddress = it.getStringExtra(EXTRA_DEVICE_ADDRESS);
        deviceName    = it.getStringExtra(EXTRA_DEVICE_NAME);
        boolean autoConnect = it.getBooleanExtra(EXTRA_AUTO_CONNECT, false);

        if (TextUtils.isEmpty(deviceAddress)) {
            toast("Cihaz adresi yok");
            finish();
            return;
        }

        deviceNameTextView.setText(!TextUtils.isEmpty(deviceName) ? deviceName : deviceAddress);
        setUiConnected(false);
        setBusy(false);

        // Bağlantı durumu dinleyicisi
        bt.onConnectionStatusChanged(new java.util.function.Consumer<IBluetoothConnector.ConnectionStatus>() {
            @Override public void accept(IBluetoothConnector.ConnectionStatus st) {
                switch (st) {
                    case CONNECTING:
                        connectionStatusTextView.setText("Durum: Bağlanıyor…");
                        connectedUI = false;
                        setUiConnected(false);
                        setBusy(true);
                        break;
                    case CONNECTED:
                        connectionStatusTextView.setText("Durum: Bağlandı");
                        connectedUI = true;
                        setUiConnected(true);
                        setBusy(false);
                        break;
                    case DISCONNECTED:
                    case FAILED:
                    default:
                        connectionStatusTextView.setText("Durum: Bağlı değil");
                        connectedUI = false;
                        setUiConnected(false);
                        setBusy(false);
                        break;
                }
            }
        });

        // RX
        bt.onDataReceived(bytes -> {
            String msg = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            appendLine("RX", msg);
        });

        // Butonlar
        connectButton.setOnClickListener(v -> tryConnect());
        disconnectButton.setOnClickListener(v -> bt.disconnect());
        sendButton.setOnClickListener(v -> {
            if (!connectedUI) { toast("Önce bağlanın"); return; }
            String text = messageEditText.getText().toString();
            if (text.isEmpty()) return;

            //boolean ok = bt.send(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            boolean ok = printBitmap(text);

            appendLine(ok ? "TX" : "TX(FAIL)", text);
            if (ok) messageEditText.setText("");
        });

        // İstersen otomatik bağlan (intent ile kontrol)
        if (autoConnect) {
            tryConnect();
        }
    }

    // ---------- Connect akışı (izin kapılı) ----------

    private void tryConnect() {
        if (!hasBtConnectPerm()) {
            requestBtConnectPerm();
            return;
        }
        doConnect();
    }

    private void doConnect() {
        setBusy(true);
        connectionStatusTextView.setText("Durum: Bağlanıyor…");

        bt.connectAsync(deviceAddress, new java.util.function.Consumer<BtResult<Void>>() {
            @Override public void accept(BtResult<Void> res) {
                if (res.isSuccess()) {
                    // onConnectionStatusChanged zaten UI'ı güncelleyecek
                } else if (res.isCancelled()) {
                    setBusy(false);
                    toast("Bağlantı iptal/zaman aşımı");
                } else {
                    setBusy(false);
                    String msg = (res.errorOrNull() != null && res.errorOrNull().getMessage() != null)
                            ? res.errorOrNull().getMessage() : "Bağlantı hatası";
                    toast(msg);
                    connectionStatusTextView.setText("Durum: Bağlantı başarısız");
                }
            }
        });
    }

    // ---------- UI yardımcıları ----------

    private void setUiConnected(boolean connected) {
        connectButton.setEnabled(!connected);
        disconnectButton.setVisibility(connected ? View.VISIBLE : View.GONE);
        messageEditText.setEnabled(connected);
        sendButton.setEnabled(connected);
    }

    private void setBusy(boolean busy) {
        // Basit durum: bağlanırken connect kapalı, bağlantı kurulunca onConnectionStatusChanged açar
        connectButton.setEnabled(!busy && !connectedUI);
        messageEditText.setEnabled(!busy && connectedUI);
        sendButton.setEnabled(!busy && connectedUI);
    }

    private void appendLine(String dir, String msg) {
        String line = String.format("[%tT] %s: %s%n", new java.util.Date(), dir, msg);
        receivedMessagesTextView.append(line);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // ---------- Permission kapısı (CONNECT) ----------

    private boolean hasBtConnectPerm() {
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true; // 31 altı için runtime CONNECT izni yok
    }

    private void requestBtConnectPerm() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{ Manifest.permission.BLUETOOTH_CONNECT }, RC_BT_CONNECT);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, perms, results);
        if (requestCode == RC_BT_CONNECT) {
            if (hasBtConnectPerm()) {
                doConnect();
            } else {
                toast("Bağlanmak için Bluetooth izni gerekli.");
            }
        }
    }

    //--------------------------------------------------
    // Yeni yazıcı komutlarını içeren metot
    private boolean printText(String textToPrint) {
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
        return bt.send(combinedCommand);
    }

    //--------------------
    private boolean printBitmap(String textToPrint) {
        if (!bt.isConnected()) {
            toast("Önce bağlanın");
            return false;
        }

        // 1. Yazdırılacak metni bir Bitmap'e çizme
        Bitmap bitmap = createBitmapFromText(textToPrint, 500, 100);

        if (bitmap == null) {
            toast("Bitmap oluşturulamadı.");
            return false;
        }

        // 2. Bitmap verisini ESC/POS'a uygun formata dönüştürme
        byte[] escposCommand = convertBitmapToEscposRaster(bitmap);

        // Hata kontrolü
        if (escposCommand == null) {
            toast("Raster verisi oluşturulamadı.");
            return false;
        }

        // 3. Yazıcıya gönderin
        return bt.send(escposCommand);
    }

// Yardımcı metotlar

    // Metni, beyaz arka plan üzerinde siyah metin olarak bir Bitmap'e çizer.
    private Bitmap createBitmapFromText(String text, int width, int height) {
        try {
            Paint paint = new Paint();
            paint.setColor(android.graphics.Color.BLACK);
            paint.setTextSize(40);
            paint.setTextAlign(Paint.Align.LEFT);

            // Metin boyutunu hesapla ve bitmap'i dinamik olarak boyutlandır
            Rect textBounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), textBounds);

            int finalWidth = textBounds.width() + 20; // 20 piksel kenar boşluğu
            int finalHeight = textBounds.height() + 20;

            Bitmap bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(android.graphics.Color.WHITE); // Beyaz arka plan

            // Metni çizme
            canvas.drawText(text, 10, finalHeight - 10, paint);

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Bitmap'i ESC/POS raster formatına dönüştüren ana metot
    private byte[] convertBitmapToEscposRaster(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int byteWidth = (width + 7) / 8; // Her 8 piksel için 1 byte
        byte[] rasterData = new byte[byteWidth * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                // Beyaz piksel ise 0, siyah piksel ise 1 olarak kabul et
                if (android.graphics.Color.red(pixel) < 128 && android.graphics.Color.green(pixel) < 128 && android.graphics.Color.blue(pixel) < 128) {
                    // Siyah piksel
                    rasterData[y * byteWidth + x / 8] |= (byte) (0x80 >> (x % 8));
                }
            }
        }

        // ESC/POS komutunu ve raster verisini birleştirme
        byte[] escposHeader = new byte[8];
        escposHeader[0] = 0x1D; // GS
        escposHeader[1] = 0x76; // v
        escposHeader[2] = 0x30; // 0
        escposHeader[3] = 0x00; // m (normal mod)
        escposHeader[4] = (byte)(byteWidth % 256); // xL
        escposHeader[5] = (byte)(byteWidth / 256); // xH
        escposHeader[6] = (byte)(height % 256); // yL
        escposHeader[7] = (byte)(height / 256); // yH

        // Kağıt kesme komutu
        byte[] cutCommand = new byte[] {0x0A, 0x0A, 0x1D, 0x56, 0x00}; // İki satır atla ve kes

        byte[] combinedCommand = new byte[escposHeader.length + rasterData.length + cutCommand.length];
        System.arraycopy(escposHeader, 0, combinedCommand, 0, escposHeader.length);
        System.arraycopy(rasterData, 0, combinedCommand, escposHeader.length, rasterData.length);
        System.arraycopy(cutCommand, 0, combinedCommand, escposHeader.length + rasterData.length, cutCommand.length);

        return combinedCommand;
    }
    //--------------------


    // Yardımcı metotlar
    private Bitmap textAsBitmap(String text, int width, int height) {
        try {
            Paint paint = new Paint();
            paint.setTextSize(40);
            paint.setColor(Color.BLACK);
            paint.setTextAlign(Paint.Align.LEFT);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE); // Beyaz arka plan
            canvas.drawText(text, 0, height / 2, paint);

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap convertToMonochrome(Bitmap bitmap) {
        Bitmap monochromeBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(monochromeBitmap);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0); // Renkleri kaldır
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return monochromeBitmap;
    }

    private byte[] convertBitmapToRasterData(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int byteWidth = (width + 7) / 8; // Her 8 piksel için 1 byte
        byte[] result = new byte[byteWidth * height];
        int k = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                // Siyah piksel ise ilgili biti ayarla
                if (Color.alpha(pixel) > 128) {
                    result[k + x / 8] |= (byte)(0x80 >> (x % 8));
                }
            }
            k += byteWidth;
        }
        return result;
    }
    //--------------------------------------------------
}
