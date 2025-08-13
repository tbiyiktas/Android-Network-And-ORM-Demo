package lib.bt.model;

public abstract class BtResult<T> {
    public static final class Success<T> extends BtResult<T> {
        private final T data;

        public Success(T data) {
            this.data = data;
        }

        public T getData() {
            return data;
        }
    }

    public static final class Failure<T> extends BtResult<T> {
        private final Exception exception;

        public Failure(Exception exception) {
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }
    }

    public static final class Cancelled<T> extends BtResult<T> {
        public Cancelled() {}
    }
}