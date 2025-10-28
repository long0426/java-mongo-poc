package com.poc.svc.assets.repository;

import com.poc.svc.assets.config.MongoWriteRetryProperties;
import com.poc.svc.assets.entity.SecuritiesAssetRawDocument;
import com.poc.svc.assets.service.SecuritiesAssetWriter;
import com.poc.svc.assets.service.SecuritiesAssetWriter.SecuritiesAssetWriteRequest;
import com.poc.svc.assets.service.impl.support.MongoWriteRetrier;
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
class SecuritiesAssetRepositoryTest {

    @Mock
    private SecuritiesAssetRawRepository repository;

    private SecuritiesAssetWriter securitiesAssetWriter;

    @BeforeEach
    void setUp() {
        securitiesAssetWriter = new SecuritiesAssetWriter(repository, new MongoWriteRetrier(MongoWriteRetryProperties.defaults()));
    }

    @Test
    void writeSecuritiesAssets_persistsRawDocumentWithTotalsAndHoldingsCount() {
        when(repository.save(any(SecuritiesAssetRawDocument.class))).thenAnswer(invocation -> {
            SecuritiesAssetRawDocument document = invocation.getArgument(0);
            return new SecuritiesAssetRawDocument(
                    "sec-id",
                    document.customerId(),
                    document.payload(),
                    document.totalMarketValue(),
                    document.holdingsCount(),
                    document.fetchedAt(),
                    document.traceId()
            );
        });

        SecuritiesAssetWriteRequest request = new SecuritiesAssetWriteRequest(
                "customer-789",
                Map.of("mock", Map.of("field", "value")),
                BigDecimal.valueOf(155_600),
                5,
                Instant.parse("2025-02-18T08:00:00Z"),
                "trace-securities"
        );

        SecuritiesAssetRawDocument result = securitiesAssetWriter.write(request);

        ArgumentCaptor<SecuritiesAssetRawDocument> documentCaptor = ArgumentCaptor.forClass(SecuritiesAssetRawDocument.class);
        verify(repository).save(documentCaptor.capture());

        SecuritiesAssetRawDocument saved = documentCaptor.getValue();
        assertThat(saved.customerId()).isEqualTo("customer-789");
        assertThat(saved.totalMarketValue()).isEqualByComparingTo("155600");
        assertThat(saved.holdingsCount()).isEqualTo(5);
        assertThat(saved.payload()).containsKey("mock");
        assertThat(saved.traceId()).isEqualTo("trace-securities");
        assertThat(saved.fetchedAt()).isEqualTo(Instant.parse("2025-02-18T08:00:00Z"));
        assertThat(result.id()).isEqualTo("sec-id");
    }
}
