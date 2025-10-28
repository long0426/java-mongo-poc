package com.poc.svc.assets.exception;

import com.poc.svc.assets.dto.AssetSourceType;

import java.util.List;

public class AssetAggregationException extends RuntimeException {

    private final List<AssetSourceType> failedSources;

    public AssetAggregationException(String message, List<AssetSourceType> failedSources) {
        super(message);
        this.failedSources = List.copyOf(failedSources);
    }

    public AssetAggregationException(String message, List<AssetSourceType> failedSources, Throwable cause) {
        super(message, cause);
        this.failedSources = List.copyOf(failedSources);
    }

    public List<AssetSourceType> failedSources() {
        return failedSources;
    }
}
