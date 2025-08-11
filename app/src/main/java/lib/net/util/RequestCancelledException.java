package lib.net.util;

import java.io.IOException;

public class RequestCancelledException extends IOException {
    public RequestCancelledException(String message) {
        super(message);
    }
}