package app.api;

import com.google.gson.reflect.TypeToken;



import lib.net.ABaseApi;
import app.model.Todo;
import lib.net.NetworkCallback;

import java.lang.reflect.Type;
import java.util.List;

public class TodoApi extends ABaseApi {

    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";

    public TodoApi() {
        super(BASE_URL);
    }

    public void getTodos(NetworkCallback<List<Todo>> callback) {
        // Geri dönüş tipini tanımlıyoruz
        Type todoListType = new TypeToken<List<Todo>>() {}.getType();

        // ABaseApi'den gelen 'get' metodunu kullanarak API çağrısını yapıyoruz
        // Parametreleri doğru sırayla iletiyoruz:
        // relativePath, queryParams, headers, responseType, callback
        get("/todos", null, null, todoListType, callback);
    }
}