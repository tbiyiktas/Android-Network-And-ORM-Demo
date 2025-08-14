package lib.bt.android.transport.classic;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import lib.bt.interfaces.IBluetoothTransport;

public final class ClassicTransport implements IBluetoothTransport {

    private static final String TAG = "ClassicTransport";

    private volatile BluetoothSocket socket;
    private volatile InputStream in;
    private volatile OutputStream out;
    private volatile Thread reader;
    private volatile Runnable onClosed;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Consumer<byte[]> onData = new Consumer<byte[]>() {
        @Override public void accept(byte[] bytes) { /* no-op */ }
    };

    @Override
    public synchronized void attach(Object lowLevelChannel) {
        detach(); // idempotent: önceki varsa kapat
        if (!(lowLevelChannel instanceof BluetoothSocket)) {
            Log.e(TAG, "attach: channel is not BluetoothSocket");
            return;
        }

        this.socket = (BluetoothSocket) lowLevelChannel;
        try {
            this.in  = socket.getInputStream();
            this.out = socket.getOutputStream();
        } catch (Exception e) {
            Log.e(TAG, "attach: stream error " + e);
            detach();
            return;
        }

        running.set(true);
        reader = new Thread(new Runnable() {
            @Override public void run() { readLoop(); }
        }, "bt-reader");
        reader.start();
    }

//    @Override
//    public synchronized void detach() {
//        running.set(false);
//        // soket kapanınca readLoop sonlanır
//        try { if (in != null) in.close(); } catch (Exception ignored) {}
//        try { if (out != null) out.close(); } catch (Exception ignored) {}
//        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
//        in = null; out = null; socket = null;
//        Thread t = reader; reader = null;
//        if (t != null) try { t.interrupt(); } catch (Exception ignored) {}
//    }

    @Override
    public boolean send(byte[] data) {
        OutputStream o = out;
        if (o == null) {
            Log.e(TAG, "send: not attached");
            return false;
        }
        try {
            o.write(data);
            o.flush();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "send error: " + e);
            return false;
        }
    }

    @Override
    public void onDataReceived(Consumer<byte[]> callback) {
        this.onData = (callback != null) ? callback : new Consumer<byte[]>() {
            @Override public void accept(byte[] bytes) { /* no-op */ }
        };
    }

//    private void readLoop() {
//        final byte[] buf = new byte[1024];
//        while (running.get()) {
//            try {
//                int n = in != null ? in.read(buf) : -1;
//                if (n == -1) {
//                    // bağlantı kapandı
//                    break;
//                }
//                if (n > 0) {
//                    byte[] copy = new byte[n];
//                    System.arraycopy(buf, 0, copy, 0, n);
//                    try { onData.accept(copy); } catch (Exception ignored) {}
//                }
//            } catch (Exception e) {
//                // socket kapanınca IOException beklenir -> döngüden çık
//                break;
//            }
//        }
//    }

//    private void readLoop() {
//        final byte[] buf = new byte[1024];
//        final InputStream localIn = in; // snapshot; detach() kapatırsa IOException alırız
//
//        try {
//            while (running.get() && !Thread.currentThread().isInterrupted()) {
//                int n;
//                try {
//                    n = (localIn != null) ? localIn.read(buf) : -1;
//                } catch (Exception e) { // IOException vs.
//                    break; // bağlantı koptu
//                }
//
//                if (n <= 0) {
//                    // n == -1: karşı taraf kapattı
//                    break;
//                }
//
//                byte[] copy = new byte[n];
//                System.arraycopy(buf, 0, copy, 0, n);
//                try { onData.accept(copy); } catch (Throwable ignore) {}
//            }
//        } finally {
//            // Döngü bittiyse bir KERE kapat ve bildir
//            closeStreamsInternal();
//            Runnable cb = onClosed;
//            if (cb != null) {
//                try { cb.run(); } catch (Throwable ignore) {}
//            }
//        }
//    }

    private void readLoop() {
        final byte[] buf = new byte[1024];
        final InputStream localIn = in;

        try {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                int n;
                try { n = (localIn != null) ? localIn.read(buf) : -1; }
                catch (Exception e) { break; }
                if (n <= 0) break;

                byte[] copy = new byte[n];
                System.arraycopy(buf, 0, copy, 0, n);
                try { onData.accept(copy); } catch (Throwable ignore) {}
            }
        } finally {
            closeStreamsInternal();
            Runnable cb = onClosed;
            if (cb != null) { try { cb.run(); } catch (Throwable ignore) {} }
        }
    }

    @Override
    public synchronized void detach() {
        running.set(false);
        closeStreamsInternal();                 // tüm kaynakları kapat (idempotent)
        Thread t = reader;
        reader = null;
        if (t != null && t != Thread.currentThread()) {
            try { t.interrupt(); } catch (Throwable ignore) {}
        }
    }

    private void closeStreamsInternal() {
        InputStream  i = in;     in = null;
        OutputStream o = out;    out = null;
        BluetoothSocket s = socket; socket = null;

        try { if (i != null) i.close(); } catch (Throwable ignore) {}
        try { if (o != null) o.close(); } catch (Throwable ignore) {}
        try { if (s != null) s.close(); } catch (Throwable ignore) {}
    }
    @Override
    public void onClosed(Runnable callback) {
        this.onClosed = callback;
    }
}
