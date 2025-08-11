package lib.net.util;

import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

import lib.net.NetResult;
import lib.net.parser.IResponseParser;

public class ResponseHandler {

    private final IResponseParser parser;

    public ResponseHandler(IResponseParser parser) {
        this.parser = parser;
    }

    // Metot artık jenerik ve doğru dönüş türünü alıyor.
    @SuppressWarnings("unchecked")
    public <T> NetResult<T> handle(NetResult<String> rawResult, Type responseType) {
        if (rawResult instanceof NetResult.Success) {
            String jsonContent = ((NetResult.Success<String>) rawResult).Data();
            try {
                if (responseType == String.class) {
                    return (NetResult<T>) rawResult;
                }

                T parsedObject = parser.parse(jsonContent, responseType);
                return new NetResult.Success<>(parsedObject);
            } catch (JsonSyntaxException e) {
                return new NetResult.Error<>(e, -1, "JSON ayrıştırma hatası.");
            }
        } else if (rawResult instanceof NetResult.Error) {
            return (NetResult<T>) rawResult;
        }
        return new NetResult.Error<>(new Exception("Bilinmeyen yanıt türü."), -1, "Bilinmeyen hata.");
    }
}

/*
package lib.net.util;

import java.lang.reflect.Type;
import lib.net.NetResult;
import lib.net.parser.IResponseParser;

public class ResponseHandler {

    private final IResponseParser responseParser;

    public ResponseHandler(IResponseParser responseParser) {
        this.responseParser = responseParser;
    }

    public <T> NetResult<T> handle(NetResult<String> rawResult, Type type) {
        if (rawResult.isSuccess()) {
            NetResult.Success<String> success = (NetResult.Success<String>) rawResult;
            try {
                T data = responseParser.parse(success.Data(), type);
                return new NetResult.Success<>(data);
            } catch (Exception e) {
                return new NetResult.Error<>(e, -1, "JSON parsing error");
            }
        } else {
            NetResult.Error<String> error = (NetResult.Error<String>) rawResult;
            return new NetResult.Error<>(error.getException(), error.getResponseCode(), error.getErrorBody());
        }
    }
}
*/