package com.example.networkanddbcontextdemo;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import app.api.TodoApi;
import app.model.Todo;
import app.repositories.TodoRepository;
import lib.net.NetResult;
import lib.net.NetworkCallback;
import lib.persistence.DbCallback;
import lib.persistence.DbResult;
import lib.persistence.RepositoryFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TextView statusTextView;
    private ProgressBar progressBar;
    private Button startRequestsButton;

    private int totalRequests = 0;
    private int requestsCompleted = 0;

//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
//    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        progressBar = findViewById(R.id.progressBar);
        startRequestsButton = findViewById(R.id.startRequestsButton);
        startRequestsButton.setOnClickListener(v -> startRequests());
    }

    private void startRequests() {
        startRequestsButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        statusTextView.setText("İstekler başlatılıyor...");

        requestsCompleted = 0;
        totalRequests = 1;

        TodoApi api = new TodoApi();

        api.getTodos(new NetworkCallback<List<Todo>>() {
            @Override
            public void onResult(NetResult<List<Todo>> result) {
                handleResult(result);
            }
        });
    }


    private void handleResult(NetResult<?> result) {
        requestsCompleted++;

        if (result.isSuccess()) {
            List<Todo> todos = ((NetResult.Success<List<Todo>>) result).Data();
            statusTextView.setText("Başarılı: " + todos.size() + " adet todo alındı.");

            TodoRepository todoRepository =  RepositoryFactory.getTodoRepository(getApplicationContext());
            Todo todo = null;

            for (int i = 0; i < todos.size(); i++) {
                todo = todos.get(i);
                todoRepository.insert(todo, new DbCallback<Todo>() {
                    @Override
                    public void onResult(DbResult<Todo> result) {
                        if(result.isSuccess()) {
                            Todo createdTodo = createdTodo = result.getData();
                            Log.d(TAG, "CREATE - Başarılı: " + createdTodo);
                        }else {
                            Exception e = ((DbResult.Error<Todo>) result).getException();
                            Log.e(TAG, "CREATE - Hata: " + e.getMessage());
                        }
                    }
                });
            }

            todoRepository.deleteById(1, new DbCallback<Todo>(){
                @Override
                public void onResult(DbResult<Todo> result) {
                    if(result.isSuccess()) {
                        Log.d(TAG, "deleteById - Başarılı: " + result.getData());
                    }
                    else{
                        Exception e = ((DbResult.Error<Todo>) result).getException();
                        Log.e(TAG, "deleteById - Hata: " + e.getMessage());
                    }
                }
            });

            todoRepository.selectAll(new DbCallback<ArrayList<Todo>>() {
                @Override
                public void onResult(DbResult<ArrayList<Todo>> result) {
                    if(result.isSuccess()) {
                        Log.d(TAG, result.getData().size()+" adet Todo listelendi.");
                    }
                    else{
                        Exception e = ((DbResult.Error<ArrayList<Todo>>) result).getException();
                        Log.e(TAG, "SELECT - Hata: " + e.getMessage());
                    }
                }
            });

        } else {
            NetResult.Error<?> error = (NetResult.Error<?>) result;
            int responseCode = error.getResponseCode();
            String errorBody = error.getErrorBody();

            if (responseCode == 404) {
                Log.e(TAG, "Hata oluştu: Kaynak bulunamadı (404) - " + errorBody);
                statusTextView.setText("Hata: Kaynak bulunamadı!");
            } else {
                Log.e(TAG, "Hata oluştu: " + error.getException().getMessage() + " (Kod: " + responseCode + ")");
                statusTextView.setText("Hata: " + error.getException().getMessage());
            }
        }

        if (requestsCompleted == totalRequests) {
            progressBar.setVisibility(View.GONE);
            startRequestsButton.setVisibility(View.VISIBLE);
            statusTextView.setText("Tüm istekler tamamlandı!");
        }
    }
}