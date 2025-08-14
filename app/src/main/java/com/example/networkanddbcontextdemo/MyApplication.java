package com.example.networkanddbcontextdemo;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import lib.bt.android.client.AndroidBluetoothClient;
import lib.bt.client.IBluetoothClient;
import lib.camera.CameraKit;
import lib.camera.CameraOptions;
import lib.location.FusedLocationClientAdapter;
import lib.location.ILocationCenter;
import lib.location.LocationCenter;
import lib.location.LocationConfigProvider;
import lib.location.StaticLocationConfigProvider;
import lib.location.runtime.LocationDispatcher;
import lib.net.NetworkManager;
import lib.net.connection.OkHttpConnectionFactory;


public class MyApplication extends Application {

    private static MyApplication instance;
    private NetworkManager networkManager;
    private IBluetoothClient bluetoothClient;

    private static ILocationCenter LOCATION_CENTER;
    private static LocationDispatcher LOCATION_DISPATCHER;
    private static LocationConfigProvider LOCATION_CONFIG;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        // ---- Network
        //networkManager = NetworkManager.create("https://jsonplaceholder.typicode.com");

        networkManager = new NetworkManager.Builder()
                .basePath("https://api.example.com")
                .factory(new OkHttpConnectionFactory())
                .build();

        // ---- Bluetooth ----
        bluetoothClient = AndroidBluetoothClient.createClassic(getApplicationContext());

        // 1) Konfig (statik provider)
        LOCATION_CONFIG = new StaticLocationConfigProvider();

        // 2) LocationClient adapter (Play Services sarıcısı)
        FusedLocationClientAdapter adapter =
                new FusedLocationClientAdapter(getApplicationContext());

        // 3) LocationCenter (ring buffer + filtreler + monotonik saat)
        LOCATION_CENTER = new LocationCenter(
                getApplicationContext(),
                adapter,
                LOCATION_CONFIG.getHistorySize(),
                LOCATION_CONFIG
        );

        // 4) Producer–Consumer Dispatcher (bounded queue + backpressure policy)
        LOCATION_DISPATCHER = new LocationDispatcher(LOCATION_CENTER, LOCATION_CONFIG);


        // 5) Uygulama ömrü boyunca otomatik start/stop davranışı (konfige bağlı)
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                if (LOCATION_CONFIG.startOnAppStart() && !LOCATION_CENTER.isRunning()) {
                    LOCATION_CENTER.start(LOCATION_CONFIG.getLiveOptions());
                }
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                if (LOCATION_CONFIG.stopOnBackground() && LOCATION_CENTER.isRunning()) {
                    LOCATION_CENTER.stop();
                }
            }
        });

        CameraKit.init(
                new CameraOptions.Builder()
                        .relativePath("Pictures/MyApplication") // >> klasörü buradan değiştir
                        .filePrefix("EC_")
                        .datePattern("yyyyMMdd_HHmmss")
                        .mimeType("image/jpeg")
                        .jpegQuality(92)
                        .allowRetake(true)
                        .build()
        );
    }


    @Override
    public void onTerminate() {
        super.onTerminate();
        // Not: onTerminate() her zaman çağrılmaz; yine de güvenli kapanış:
        if (LOCATION_CENTER instanceof LocationCenter) {
            ((LocationCenter) LOCATION_CENTER).shutdown();
        }
        if (LOCATION_DISPATCHER != null) {
            LOCATION_DISPATCHER.shutdown();
        }
    }

    // --- statik erişim yardımcıları ---
    public static ILocationCenter locationCenter() { return LOCATION_CENTER; }
    public static LocationDispatcher locationDispatcher() { return LOCATION_DISPATCHER; }
    public static LocationConfigProvider locationConfig() { return LOCATION_CONFIG; }

    public static MyApplication getInstance() {
        if (instance == null) throw new IllegalStateException("Application not initialized");
        return instance;
    }
    public static NetworkManager getNetworkManager() {
        return getInstance().networkManager;
    }

    public static IBluetoothClient  getBluetoothClient() {
        return getInstance().bluetoothClient;
    }
}