package lib.net.parser;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

public class GsonResponseParser implements IResponseParser {
    private final Gson gson = new Gson();

    @Override
    public <T> T parse(String json, Type type) {
        try {
            return gson.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("JSON ayrıştırma hatası", e);
        }
    }
}