package lib.persistence;

import android.content.Context;

import app.DbContext;
import app.repositories.BtDeviceRepository;
import app.repositories.TodoRepository;

public class RepositoryFactory {

    // DbContext sınıfınızın AppDbContext olduğunu varsayıyoruz
    private static ADbContext adbContextInstance;

    // Uygulama Context'ini sadece bir kez alıp kullanıyoruz
    private static synchronized ADbContext getDbContextInstance(Context context) {
        if (adbContextInstance == null) {
            // AppDbContext, ADbContext'i miras alıyor
            adbContextInstance = new DbContext(context);
        }
        return adbContextInstance;
    }

    // Repository'ler için fabrika metotları
    public static TodoRepository getTodoRepository(Context context) {
        return new TodoRepository(getDbContextInstance(context.getApplicationContext()));
    }

    public static BtDeviceRepository getBtDeviceRepository(Context context) {
        return new BtDeviceRepository(getDbContextInstance(context.getApplicationContext()));
    }
}