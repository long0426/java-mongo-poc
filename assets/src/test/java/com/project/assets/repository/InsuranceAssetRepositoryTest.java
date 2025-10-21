package com.project.assets.repository;

import com.project.assets.config.MongoWriteRetryProperties;
import com.project.assets.repository.document.InsuranceAssetRawDocument;
import com.project.assets.service.InsuranceAssetWriter;
import com.project.assets.service.InsuranceAssetWriter.InsuranceAssetWriteRequest;
import com.project.assets.service.support.MongoWriteRetrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsuranceAssetRepositoryTest {

    @Mock
    private InsuranceAssetRawRepository repository;

    private InsuranceAssetWriter insuranceAssetWriter;

    @BeforeEach
    void setUp() {
        insuranceAssetWriter = new InsuranceAssetWriter(repository, new MongoWriteRetrier(MongoWriteRetryProperties.defaults()));
    }

    @Test
    void writeInsuranceAssets_persistsCoverageAndPoliciesCount() {
        when(repository.save(any(InsuranceAssetRawDocument.class))).thenAnswer(invocation -> {
            InsuranceAssetRawDocument document = invocation.getArgument(0);
            return new InsuranceAssetRawDocument(
                    "ins-id",
                    document.customerId(),
                    document.payload(),
                    document.totalCoverage(),
                    document.policiesCount(),
                    document.fetchedAt(),
                    document.traceId()
            );
        });

        InsuranceAssetWriteRequest request = new InsuranceAssetWriteRequest(
                "customer-555",
                Map.of("policies", Map.of("primary", "LIFE-001")),
                BigDecimal.valueOf(250_000),
                3,
                Instant.parse("2025-03-01T12:30:00Z"),
                "trace-insurance"
        );

        InsuranceAssetRawDocument result = insuranceAssetWriter.write(request);

        ArgumentCaptor<InsuranceAssetRawDocument> documentCaptor = ArgumentCaptor.forClass(InsuranceAssetRawDocument.class);
        verify(repository).save(documentCaptor.capture());

        InsuranceAssetRawDocument saved = documentCaptor.getValue();
        assertThat(saved.customerId()).isEqualTo("customer-555");
        assertThat(saved.totalCoverage()).isEqualByComparingTo("250000");
        assertThat(saved.policiesCount()).isEqualTo(3);
        assertThat(saved.payload()).containsKey("policies");
        assertThat(saved.traceId()).isEqualTo("trace-insurance");
        assertThat(saved.fetchedAt()).isEqualTo(Instant.parse("2025-03-01T12:30:00Z"));
        assertThat(result.id()).isEqualTo("ins-id");
    }
}
