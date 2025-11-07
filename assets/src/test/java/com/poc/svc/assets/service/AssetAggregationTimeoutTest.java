package com.poc.svc.assets.service;

import com.poc.svc.assets.config.MongoWriteRetryProperties;
import com.poc.svc.assets.service.AssetSourceClient;
import com.poc.svc.assets.service.AssetSourceClient.BankAssetResult;
import com.poc.svc.assets.service.AssetSourceClient.InsuranceAssetResult;
import com.poc.svc.assets.service.AssetSourceClient.SecuritiesAssetResult;
import com.poc.svc.assets.util.TraceContext;
import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.exception.AssetAggregationException;
import com.poc.svc.assets.repository.AssetStagingRepository;
import com.poc.svc.assets.repository.BankAssetRawRepository;
import com.poc.svc.assets.repository.InsuranceAssetRawRepository;
import com.poc.svc.assets.repository.SecuritiesAssetRawRepository;
import com.poc.svc.assets.service.impl.support.MongoWriteRetrier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class AssetAggregationTimeoutTest {

    @Container
    static final MongoDBContainer mongoContainer = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void overrideMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getConnectionString);
        registry.add("spring.data.mongodb.database", () -> "testdb");
    }

    @Autowired
    private BankAssetRawRepository bankAssetRawRepository;

    @Autowired
    private SecuritiesAssetRawRepository securitiesAssetRawRepository;

    @Autowired
    private InsuranceAssetRawRepository insuranceAssetRawRepository;

    @Autowired
    private AssetStagingRepository assetStagingRepository;

    private TimeoutAssetSourceClient assetSourceClient;
    private AssetAggregationService assetAggregationService;

    @BeforeEach
    void setUp() {
        bankAssetRawRepository.deleteAll();
        securitiesAssetRawRepository.deleteAll();
        insuranceAssetRawRepository.deleteAll();
        assetStagingRepository.deleteAll();

        assetSourceClient = new TimeoutAssetSourceClient();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MongoWriteRetrier writeRetrier = new MongoWriteRetrier(MongoWriteRetryProperties.defaults());
        AssetAggregationCoordinator coordinator = new AssetAggregationCoordinator(
                assetSourceClient,
                new BankAssetWriter(bankAssetRawRepository, writeRetrier),
                new SecuritiesAssetWriter(securitiesAssetRawRepository, writeRetrier),
                new InsuranceAssetWriter(insuranceAssetRawRepository, writeRetrier),
                meterRegistry
        );

        assetAggregationService = new AssetAggregationService(
                coordinator,
                assetStagingRepository,
                new LocalStaticConversionService(Map.of(
                        "USD:TWD", BigDecimal.valueOf(32.0)
                )),
                new ObjectMapper(),
                new AssetAggregationService.AggregationProperties("TWD", Duration.ofMillis(200)),
                meterRegistry
        );
    }

    @Test
    @DisplayName("should throw AssetAggregationException when a source times out")
    void aggregateAssets_timeout() {
        TraceContext.ensureTraceId("timeout-trace");

        assetSourceClient.bankResult = new BankAssetResult(
                "c-timeout",
                Map.of("bankAssets", List.of(Map.of("accountId", "A-1", "balance", 100, "currency", "TWD"))),
                BigDecimal.valueOf(100),
                "TWD",
                List.of(new BankAssetWriter.BankAssetWriteRequest.CurrencyAmount("TWD", BigDecimal.valueOf(100))),
                Instant.now(),
                "bank-trace"
        );

        assetSourceClient.insuranceResult = new InsuranceAssetResult(
                "c-timeout",
                Map.of("insuranceAssets", List.of(Map.of("policyNumber", "P-1", "coverage", 200, "currency", "TWD"))),
                BigDecimal.valueOf(200),
                "TWD",
                1,
                Instant.now(),
                "ins-trace"
        );

        assertThatThrownBy(() -> assetAggregationService.aggregateCustomerAssets("c-timeout"))
                .isInstanceOf(AssetAggregationException.class)
                .extracting("failedSources", InstanceOfAssertFactories.LIST)
                .asList()
                .containsExactly(AssetSourceType.SECURITIES);

        assertThat(assetStagingRepository.count()).isZero();
        assertThat(bankAssetRawRepository.count()).isEqualTo(1);
        assertThat(securitiesAssetRawRepository.count()).isZero();
        assertThat(insuranceAssetRawRepository.count()).isEqualTo(1);
    }

    private static class TimeoutAssetSourceClient implements AssetSourceClient {

        private final CompletableFuture<SecuritiesAssetResult> securitiesFuture = new CompletableFuture<>();
        private BankAssetResult bankResult;
        private InsuranceAssetResult insuranceResult;

        @Override
        public CompletableFuture<BankAssetResult> fetchBankAssets(String customerId, String traceId) {
            return CompletableFuture.completedFuture(bankResult);
        }

        @Override
        public CompletableFuture<SecuritiesAssetResult> fetchSecuritiesAssets(String customerId, String traceId) {
            return securitiesFuture;
        }

        @Override
        public CompletableFuture<InsuranceAssetResult> fetchInsuranceAssets(String customerId, String traceId) {
            return CompletableFuture.completedFuture(insuranceResult);
        }
    }

    private static class LocalStaticConversionService implements CurrencyConversionService {

        private final Map<String, BigDecimal> rates;

        private LocalStaticConversionService(Map<String, BigDecimal> rates) {
            this.rates = rates;
        }

        @Override
        public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
            if (fromCurrency.equalsIgnoreCase(toCurrency)) {
                return new ConversionResult(amount, BigDecimal.ONE);
            }
            BigDecimal rate = rates.get(fromCurrency.toUpperCase() + ":" + toCurrency.toUpperCase());
            if (rate == null) {
                throw new IllegalArgumentException("Missing conversion rate");
            }
            return new ConversionResult(amount.multiply(rate), rate);
        }
    }
}
