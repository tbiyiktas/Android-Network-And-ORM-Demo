package app;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import lib.persistence.ADbContext;
import lib.persistence.command.definition.CreateTableCommand;
import lib.persistence.command.definition.DropTableCommand;
import app.model.Todo;

public class DbContext extends ADbContext {
    private static final String dbName = "local.db";
    private static final int version = 4;

    public DbContext(Context context) {
        super(context, dbName, null, version);
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
        // SQLiteOpenHelper zaten thread-safe olduğu için ekstra synchronized bloğuna gerek yoktur.
        // Bu metod, veritabanı ilk kez oluşturulduğunda çalışır.

        sqLiteDatabase.execSQL(CreateTableCommand.build(Todo.class).getQuery());
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
        // SQLiteOpenHelper zaten thread-safe olduğu için ekstra synchronized bloğuna gerek yoktur.
        // Bu metod, veritabanı versiyonu yükseltildiğinde çalışır.

        // Bu basit bir upgrade stratejisidir (tüm tabloları silip yeniden oluşturur).
        // Gerçek projelerde veri kaybını önlemek için daha gelişmiş migration adımları uygulanmalıdır.
        sqLiteDatabase.execSQL(DropTableCommand.build(Todo.class).getQuery());

        onCreate(sqLiteDatabase);
    }
}