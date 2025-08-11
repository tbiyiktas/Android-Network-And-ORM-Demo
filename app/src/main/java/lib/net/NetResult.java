package lib.net;

public abstract class NetResult<T> {
    private NetResult() {}

    public abstract T Data();

    public abstract boolean isSuccess();
    public abstract boolean isError();

    public static final class Success<T> extends NetResult<T> {
        private final T data;

        public Success(T data) {
            this.data = data;
        }

        @Override
        public T Data() {
            return data;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }
    }

    public static final class Error<T> extends NetResult<T> {
        private Exception exception;
        private int responseCode;
        private String errorBody;

        public Error(Exception exception, int responseCode, String errorBody) {
            this.exception = exception;
            this.responseCode = responseCode;
            this.errorBody = errorBody;
        }

        @Override
        public T Data() {
            return null;
        }

        public Exception getException() {
            return exception;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getErrorBody() {
            return errorBody;
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isError() {
            return true;
        }
    }
}