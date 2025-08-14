package lib.bt.model;

import lib.bt.model.exceptions.BtException;

/**
 * Operasyon sonuç zarfı: Success / Failure / Cancelled.
 * Java 11 uyumlu sürüm (instanceof pattern matching yok).
 */
public abstract class BtResult<T> {

    private BtResult() { }

    // ---------- Varyantlar ----------
    public static final class Success<T> extends BtResult<T> {
        private final T data;
        public Success(T data) { this.data = data; }
        public T getData() { return data; }
    }

    public static final class Failure<T> extends BtResult<T> {
        private final BtException exception;
        public Failure(BtException exception) { this.exception = exception; }
        public BtException getException() { return exception; }
    }

    public static final class Cancelled<T> extends BtResult<T> {
        public Cancelled() { }
    }

    // ---------- Fabrika yardımcıları ----------
    public static <T> BtResult<T> ok(T data) { return new Success<T>(data); }
    public static <T> BtResult<T> fail(BtException ex) { return new Failure<T>(ex); }
    public static <T> BtResult<T> cancel() { return new Cancelled<T>(); }

    // ---------- Ergonomi yardımcıları ----------
    public boolean isSuccess()   { return this instanceof Success; }
    public boolean isFailure()   { return this instanceof Failure; }
    public boolean isCancelled() { return this instanceof Cancelled; }

    @SuppressWarnings("unchecked")
    public T getOrNull() {
        if (this instanceof Success) {
            return ((Success<T>) this).getData();
        }
        return null;
    }

    public BtException errorOrNull() {
        if (this instanceof Failure) {
            return ((Failure<?>) this).getException();
        }
        return null;
    }
}
