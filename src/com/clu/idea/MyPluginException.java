package com.clu.idea;

public class MyPluginException extends RuntimeException {

    public MyPluginException() {
    }

    public MyPluginException(String message) {
        super(message);
    }

    public MyPluginException(String message, Throwable cause) {
        super(message, cause);
    }

    public MyPluginException(Throwable cause) {
        super(cause);
    }

    public MyPluginException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
