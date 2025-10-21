package com.project.assets.service;

import com.project.assets.model.AssetSourceType;

import java.util.List;

public class AssetAggregationException extends RuntimeException {

    private final List<AssetSourceType> failedSources;

    public AssetAggregationException(String message, List<AssetSourceType> failedSources, Throwable cause) {
        super(message, cause);
        this.failedSources = List.copyOf(failedSources);
    }

    public List<AssetSourceType> getFailedSources() {
        return failedSources;
    }
}
