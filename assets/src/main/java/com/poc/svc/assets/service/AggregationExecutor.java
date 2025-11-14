package com.poc.svc.assets.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.poc.svc.assets.config.MongoSettingsProperties;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AggregationExecutor {

    private static final Logger log = LoggerFactory.getLogger(AggregationExecutor.class);
    private static final String PIPELINE_STORE_COLLECTION = "pipeline_store";
    private static final String MERGE_STAGE_KEY = "$merge";

    private final MongoClient mongoClient;
    private final MongoSettingsProperties mongoSettings;

    public AggregationExecutor(MongoClient mongoClient, MongoSettingsProperties mongoSettings) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "mongoClient must not be null");
        this.mongoSettings = Objects.requireNonNull(mongoSettings, "mongoSettings must not be null");
    }

    public List<Document> execute(String pipelineName, String traceId) {
        if (!StringUtils.hasText(pipelineName)) {
            throw new IllegalArgumentException("pipelineName must not be blank");
        }
        if (!StringUtils.hasText(traceId)) {
            throw new IllegalArgumentException("traceId must not be blank");
        }

        MongoDatabase database = mongoClient.getDatabase(mongoSettings.database());
        Document definition = loadPipelineDefinition(database, pipelineName);
        List<Document> pipelineStages = getPipelineStages(pipelineName, definition);
        Document mergeSpec = extractMergeSpec(pipelineName, pipelineStages);
        String targetCollection = resolveTargetCollection(pipelineName, mergeSpec);
        String sourceCollection = resolveSourceCollection(definition, targetCollection);

        MongoCollection<Document> source = database.getCollection(sourceCollection);
        MongoCollection<Document> target = database.getCollection(targetCollection);

        source.aggregate(pipelineStages).into(new ArrayList<>());

        List<Document> results = target.find(Filters.eq("traceId", traceId))
                .into(new ArrayList<>());
        List<Document> cloned = new ArrayList<>(results.size());
        for (Document document : results) {
            cloned.add(cloneDocument(document));
        }

        log.info("Executed pipeline='{}' sourceCollection='{}' targetCollection='{}' traceId={} resultCount={}",
                pipelineName, sourceCollection, targetCollection, traceId, cloned.size());
        return cloned;
    }

    private Document loadPipelineDefinition(MongoDatabase database, String pipelineName) {
        Document definition = database.getCollection(PIPELINE_STORE_COLLECTION)
                .find(Filters.eq("name", pipelineName))
                .first();
        if (definition == null) {
            throw new IllegalStateException("Pipeline definition not found for name=" + pipelineName);
        }
        return definition;
    }

    private List<Document> getPipelineStages(String pipelineName, Document definition) {
        List<Document> stages = definition.getList("pipeline", Document.class);
        if (CollectionUtils.isEmpty(stages)) {
            throw new IllegalStateException("Pipeline definition '" + pipelineName + "' does not contain any stages");
        }
        List<Document> cloned = new ArrayList<>(stages.size());
        for (Document stage : stages) {
            cloned.add(cloneDocument(stage));
        }
        return cloned;
    }

    private Document extractMergeSpec(String pipelineName, List<Document> stages) {
        Document lastStage = stages.get(stages.size() - 1);
        Document mergeSpec = lastStage.get(MERGE_STAGE_KEY, Document.class);
        if (mergeSpec == null) {
            throw new IllegalStateException("Pipeline '" + pipelineName + "' must terminate with a $merge stage");
        }
        return cloneDocument(mergeSpec);
    }

    private String resolveTargetCollection(String pipelineName, Document mergeSpec) {
        Object into = mergeSpec.get("into");
        if (into instanceof String target && StringUtils.hasText(target)) {
            return target;
        }
        if (into instanceof Document intoDoc) {
            String collection = intoDoc.getString("coll");
            if (!StringUtils.hasText(collection)) {
                collection = intoDoc.getString("collection");
            }
            if (StringUtils.hasText(collection)) {
                return collection;
            }
        }
        throw new IllegalStateException("Pipeline '" + pipelineName + "' $merge stage does not specify a valid target collection");
    }

    private String resolveSourceCollection(Document definition, String fallback) {
        List<String> keys = List.of("sourceCollection", "source_collection", "source");
        for (String key : keys) {
            String candidate = definition.getString(key);
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        log.warn("Pipeline definition does not specify a source collection, fallback to target '{}'", fallback);
        if (!StringUtils.hasText(fallback)) {
            throw new IllegalStateException("Unable to determine source collection for aggregation pipeline");
        }
        return fallback;
    }

    private Document cloneDocument(Document source) {
        if (source == null) {
            return null;
        }
        return Document.parse(source.toJson());
    }
}
