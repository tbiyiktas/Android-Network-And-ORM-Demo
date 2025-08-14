package lib.bt.model.exceptions;

public class BtException extends Exception {
    public BtException(String message) { super(message); }
    public BtException(String message, Throwable cause) { super(message, cause); }
}
