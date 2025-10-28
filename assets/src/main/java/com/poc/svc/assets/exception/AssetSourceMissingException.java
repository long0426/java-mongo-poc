package com.poc.svc.assets.exception;

public class AssetSourceMissingException extends RuntimeException {

    public AssetSourceMissingException(String message) {
        super(message);
    }

    public AssetSourceMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
