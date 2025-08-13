package lib.bt.model.errors;

public class BtException extends Exception {
    public BtException(String message) {
        super(message);
    }

    public BtException(String message, Throwable cause) {
        super(message, cause);
    }
}