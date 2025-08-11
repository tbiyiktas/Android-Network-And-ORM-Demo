package com.example.networkanddbcontextdemo;

import android.app.Application;

import lib.net.NetworkManager;
import lib.net.connection.OkHttpConnectionFactory;

public class MyApplication extends Application {

    private static MyApplication instance;
    private NetworkManager networkManager;

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;
        //networkManager = NetworkManager.create("https://jsonplaceholder.typicode.com");

        networkManager = new NetworkManager.Builder()
                .basePath("https://api.example.com")
                .factory(new OkHttpConnectionFactory())
                .build();
    }

    public static NetworkManager getNetworkManager() {
        if (instance == null) {
            throw new IllegalStateException("Uygulama henüz başlatılmadı.");
        }
        return instance.networkManager;
    }
}