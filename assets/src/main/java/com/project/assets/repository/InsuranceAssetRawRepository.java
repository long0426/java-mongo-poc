package com.project.assets.repository;

import com.project.assets.repository.document.InsuranceAssetRawDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InsuranceAssetRawRepository extends MongoRepository<InsuranceAssetRawDocument, String> {

    Optional<InsuranceAssetRawDocument> findFirstByCustomerIdOrderByFetchedAtDesc(String customerId);
}
