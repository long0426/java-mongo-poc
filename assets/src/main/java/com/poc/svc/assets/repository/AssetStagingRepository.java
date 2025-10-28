package com.poc.svc.assets.repository;

import com.poc.svc.assets.entity.AssetStagingDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AssetStagingRepository extends MongoRepository<AssetStagingDocument, String> {

    Optional<AssetStagingDocument> findByCustomerId(String customerId);
}
