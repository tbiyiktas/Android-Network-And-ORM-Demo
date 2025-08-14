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
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lib.location.Cancellable;
import lib.location.LocationData;
import lib.location.LocationResult;
import lib.location.commands.LocationCommands;

public class LocationActivity extends AppCompatActivity {

    private static final int RC_LOCATION = 1001;
    private static final long UI_REFRESH_MS = 1000L;      // 1 sn
    private static final long LATEST_MAX_AGE_MS = 60_000L; // ≤60 sn
    private static final Float LATEST_MIN_ACC_M = 30f;     // ≤30 m

    private RecyclerView rvHistory;
    private TextView txtLatest;
    private Button btnEnableLocation;

    private LocationHistoryAdapter adapter;
    private Handler uiHandler;
    private boolean uiLoopRunning = false;

    private long lastTopTimestamp = -1L;
    private int lastCount = -1;

    private Cancellable inFlightLatestCall;

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            refreshHistoryIfChanged();
            fetchLatestAsync(); // üstteki satırı async güncelle
            if (uiLoopRunning) uiHandler.postDelayed(this, UI_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        uiHandler = new Handler(Looper.getMainLooper());

        rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LocationHistoryAdapter();
        rvHistory.setAdapter(adapter);

        txtLatest = findViewById(R.id.txtLatest);

        btnEnableLocation = findViewById(R.id.btnEnableLocation);
        btnEnableLocation.setOnClickListener(v -> openLocationSettings());

        btnEnableLocation.setVisibility(View.VISIBLE);

        ensureLocationPermission(); // izin yoksa iste
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateUiState(); // konum/izin durumuna göre görünürlük ve akış
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ayarlardan dönünce tekrar değerlendir
        updateUiState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopUiLoop();
        cancelLatestCall();
        // Global start/stop ProcessLifecycle + config ile yönetiliyor.
    }

    // --- Görünürlük & akış kontrolü ---

    private void updateUiState() {
        boolean hasPerm = hasLocationPermission();
        boolean enabled = isLocationEnabled(this);

        // Konum kapalıysa: buton VISIBLE, içerik GONE
        // Konum açıksa: buton GONE, içerik VISIBLE
        //btnEnableLocation.setVisibility(enabled ? View.GONE : View.VISIBLE);

        btnEnableLocation.setVisibility(View.VISIBLE);
        btnEnableLocation.setEnabled(!enabled);
        txtLatest.setVisibility(enabled ? View.VISIBLE : View.GONE);
        rvHistory.setVisibility(enabled ? View.VISIBLE : View.GONE);

        if (!hasPerm) {
            ensureLocationPermission();
            stopUiLoop();
            cancelLatestCall();
            return;
        }

        if (!enabled) {
            stopUiLoop();
            cancelLatestCall();
            txtLatest.setText("Konum kapalı");
            return;
        }

        // İzin var + konum açık → akış + UI döngüsü
        startFlowIfNeeded();
        startUiLoop();
        fetchLatestAsync(); // anında ilk değer
    }

    private void startFlowIfNeeded() {
        if (!MyApplication.locationCenter().isRunning()) {
            MyApplication.locationDispatcher().enqueue(
                    LocationCommands.start(MyApplication.locationConfig().getLiveOptions())
            );
        }
    }

    private void startUiLoop() {
        if (uiLoopRunning) return;
        uiLoopRunning = true;
        uiHandler.post(pollTask);
    }

    private void stopUiLoop() {
        if (!uiLoopRunning) return;
        uiLoopRunning = false;
        uiHandler.removeCallbacks(pollTask);
    }

    private void refreshHistoryIfChanged() {
        List<LocationResult<LocationData>> all = MyApplication.locationCenter().getHistoryNewestFirst();
        List<LocationResult<LocationData>> top20 = all.size() > 20 ? all.subList(0, 20) : all;

        long topTs = top20.isEmpty() ? -1L : top20.get(0).occurredAtUtcMillis;
        if (topTs != lastTopTimestamp || top20.size() != lastCount) {
            lastTopTimestamp = topTs;
            lastCount = top20.size();
            adapter.submit(new ArrayList<>(top20)); // copy-out
        }
    }

    private void fetchLatestAsync() {
        cancelLatestCall(); // kuyruğu şişirmemek için

        inFlightLatestCall = MyApplication.locationDispatcher().enqueue(
                LocationCommands.getLatestValid(LATEST_MAX_AGE_MS, LATEST_MIN_ACC_M, result -> {
                    if (isFinishing() || isDestroyed()) return;

                    if (result.isSuccess() && result.data != null) {
                        LocationData d = result.data;

                        long ageMs;
                        if (d.elapsedRealtimeNanos != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            long nowNs = SystemClock.elapsedRealtimeNanos();
                            long deltaNs = nowNs - d.elapsedRealtimeNanos;
                            if (deltaNs < 0) deltaNs = 0;
                            ageMs = deltaNs / 1_000_000L;
                        } else {
                            long delta = System.currentTimeMillis() - d.timeMillis;
                            if (delta < 0) delta = 0;
                            ageMs = delta;
                        }

                        String accStr = (d.accuracy == null) ? "—" : (Math.round(d.accuracy) + " m");
                        String txt = String.format(Locale.getDefault(),
                                "Son (≤60sn, ≤30m): age=%dms  lat=%.5f  lon=%.5f  acc=%s",
                                ageMs, d.latitude, d.longitude, accStr);
                        txtLatest.setText(txt);
                    } else {
                        txtLatest.setText("Son (≤60sn, ≤30m): —");
                    }
                })
        );
    }

    private void cancelLatestCall() {
        if (inFlightLatestCall != null && !inFlightLatestCall.isCanceled()) {
            inFlightLatestCall.cancel();
        }
        inFlightLatestCall = null;
    }

    // --- Permission & Settings ---

    private boolean hasLocationPermission() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return fine || coarse;
    }

    private void ensureLocationPermission() {
        if (hasLocationPermission()) return;

        ArrayList<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Bu Activity için arkaplan şart değil; gerekiyorsa ayrıca isteriz.
            // perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), RC_LOCATION);
    }

    private void openLocationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        } catch (Exception ignored) { }
        // Dönüşte onResume çağrılacak ve updateUiState() akışı başlatacak.
    }

    private static boolean isLocationEnabled(@NonNull Context context) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return false;
        try {
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updateUiState();
    }
}
