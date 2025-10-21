package com.project.assets.integration;

public class AssetSourceMissingException extends RuntimeException {

    public AssetSourceMissingException(String message) {
        super(message);
    }

    public AssetSourceMissingException(String message, Throwable cause) {
        super(message, cause);
    }
}
