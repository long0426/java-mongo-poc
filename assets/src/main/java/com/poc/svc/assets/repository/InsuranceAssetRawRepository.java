package com.poc.svc.assets.repository;

import com.poc.svc.assets.entity.InsuranceAssetRawDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InsuranceAssetRawRepository extends MongoRepository<InsuranceAssetRawDocument, String> {

    Optional<InsuranceAssetRawDocument> findFirstByCustomerIdOrderByFetchedAtDesc(String customerId);
}
