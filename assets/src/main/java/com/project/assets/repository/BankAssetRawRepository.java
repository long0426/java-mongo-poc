package com.project.assets.repository;

import com.project.assets.repository.document.BankAssetRawDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface BankAssetRawRepository extends MongoRepository<BankAssetRawDocument, String> {

    Optional<BankAssetRawDocument> findFirstByCustomerIdOrderByFetchedAtDesc(String customerId);
}
