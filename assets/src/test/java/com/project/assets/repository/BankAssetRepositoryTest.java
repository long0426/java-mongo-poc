package com.project.assets.repository;

import com.project.assets.config.MongoWriteRetryProperties;
import com.project.assets.repository.document.BankAssetRawDocument;
import com.project.assets.service.BankAssetWriter;
import com.project.assets.service.BankAssetWriter.BankAssetWriteRequest;
import com.project.assets.service.support.MongoWriteRetrier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankAssetRepositoryTest {

    @Mock
    private BankAssetRawRepository repository;

    private BankAssetWriter bankAssetWriter;

    @BeforeEach
    void setUp() {
        bankAssetWriter = new BankAssetWriter(repository, new MongoWriteRetrier(MongoWriteRetryProperties.defaults()));
    }

    @Test
    void writeBankAssets_persistsRawDocumentWithPayloadAndTotals() {
        when(repository.save(any(BankAssetRawDocument.class))).thenAnswer(invocation -> {
            BankAssetRawDocument document = invocation.getArgument(0);
            return new BankAssetRawDocument(
                    "generated-id",
                    document.customerId(),
                    document.payload(),
                    document.totalBalance(),
                    document.currencySummary(),
                    document.fetchedAt(),
                    document.traceId()
            );
        });

        BankAssetWriteRequest request = new BankAssetWriteRequest(
                "customer-123",
                Map.of(
                        "accounts", List.of(
                                Map.of("accountId", "001", "balance", 500)
                        )
                ),
                BigDecimal.valueOf(500),
                List.of(new BankAssetWriteRequest.CurrencyAmount("TWD", BigDecimal.valueOf(500))),
                Instant.parse("2025-01-01T00:00:00Z"),
                "trace-abc"
        );

        BankAssetRawDocument result = bankAssetWriter.write(request);

        ArgumentCaptor<BankAssetRawDocument> documentCaptor = ArgumentCaptor.forClass(BankAssetRawDocument.class);
        verify(repository).save(documentCaptor.capture());

        BankAssetRawDocument saved = documentCaptor.getValue();
        assertThat(saved.customerId()).isEqualTo("customer-123");
        assertThat(saved.totalBalance()).isEqualByComparingTo("500");
        assertThat(saved.currencySummary()).hasSize(1);
        assertThat(saved.currencySummary().get(0).currency()).isEqualTo("TWD");
        assertThat(saved.payload()).containsKey("accounts");
        assertThat(saved.traceId()).isEqualTo("trace-abc");
        assertThat(saved.fetchedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));

        assertThat(result.id()).isEqualTo("generated-id");
    }
}
