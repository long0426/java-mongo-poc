package com.poc.svc.assets.service;

import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AggregationExecutorIntegrationTest {

    @Container
    static final MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getConnectionString);
        registry.add("spring.data.mongodb.database", () -> "aggregation_it");
    }

    @Autowired
    private AggregationExecutor aggregationExecutor;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void cleanCollections() {
        mongoTemplate.getDb().drop();
    }

    @Test
    @DisplayName("should wrap merge pipeline with facet and return merged documents")
    void executesFacetWrappedPipeline() {
        seedPipelineDefinition();
        insertRawBankDocument();
        insertExistingStagingDocument();

        ensureStagingIndex();

        List<Document> results = aggregationExecutor.execute("assets_aggregation", "trace-it");

        assertThat(results).hasSize(1);
        Document aggregated = results.get(0);
        assertThat(aggregated.getString("traceId")).isEqualTo("trace-it");
        assertThat(aggregated.getString("customerId")).isEqualTo("customer-it");
        assertThat(aggregated.getString("aggregationStatus")).isEqualTo("COMPLETED");

        List<Document> stagingDocuments = mongoTemplate.getDb()
                .getCollection("asset_staging")
                .find()
                .into(new java.util.ArrayList<>());
        assertThat(stagingDocuments).hasSize(2);
        long traceMatches = stagingDocuments.stream()
                .filter(doc -> "trace-it".equals(doc.getString("traceId")))
                .count();
        assertThat(traceMatches).isEqualTo(1);
    }

    private void seedPipelineDefinition() {
        List<Document> pipeline = List.of(
                new Document("$match", new Document("customerId", "customer-it")),
                new Document("$addFields", new Document("aggregationStatus", "COMPLETED")),
                new Document("$merge", new Document()
                        .append("into", "asset_staging")
                        .append("on", "traceId")
                        .append("whenMatched", "replace")
                        .append("whenNotMatched", "insert"))
        );

        Document pipelineDefinition = new Document()
                .append("name", "assets_aggregation")
                .append("description", "integration-test pipeline")
                .append("sourceCollection", "bank_raw")
                .append("pipeline", pipeline);

        mongoTemplate.getDb()
                .getCollection("pipeline_store")
                .insertOne(pipelineDefinition);
    }

    private void insertRawBankDocument() {
        Document bankRaw = new Document()
                .append("traceId", "trace-it")
                .append("customerId", "customer-it")
                .append("balance", 900_000)
                .append("payload", new Document("bankAssets", List.of()));

        mongoTemplate.getDb()
                .getCollection("bank_raw")
                .insertOne(bankRaw);
    }

    private void insertExistingStagingDocument() {
        Document other = new Document()
                .append("traceId", "trace-old")
                .append("customerId", "legacy")
                .append("aggregationStatus", "COMPLETED");
        mongoTemplate.getDb()
                .getCollection("asset_staging")
                .insertOne(other);
    }

    private void ensureStagingIndex() {
        mongoTemplate.getDb()
                .getCollection("asset_staging")
                .createIndex(new Document("traceId", 1), new IndexOptions().unique(true));
    }
}
