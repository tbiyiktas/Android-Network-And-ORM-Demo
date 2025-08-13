package lib.bt.model.errors;

public class PermissionDeniedException extends BtException {
    public PermissionDeniedException(String message) {
        super(message);
    }
}