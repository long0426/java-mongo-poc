package com.poc.svc.assets.repository;

import com.poc.svc.assets.entity.BankAssetRawDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BankAssetRawRepository extends MongoRepository<BankAssetRawDocument, String> {

    Optional<BankAssetRawDocument> findFirstByCustomerIdOrderByFetchedAtDesc(String customerId);
}
