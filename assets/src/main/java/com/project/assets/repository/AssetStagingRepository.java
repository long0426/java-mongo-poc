package com.project.assets.repository;

import com.project.assets.repository.document.AssetStagingDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AssetStagingRepository extends MongoRepository<AssetStagingDocument, String> {

    Optional<AssetStagingDocument> findByCustomerId(String customerId);
}
