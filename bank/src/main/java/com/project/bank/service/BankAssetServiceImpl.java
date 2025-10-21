package com.project.bank.service;

import com.project.bank.logging.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@Service
class BankAssetServiceImpl implements BankAssetService {

    private static final List<String> CURRENCIES = List.of("TWD", "USD", "JPY", "EUR");

    private final MeterRegistry meterRegistry;

    BankAssetServiceImpl(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public BankAssetResponse getCustomerAssets(String customerId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<BankAssetResponse.BankAssetItem> accounts = generateAccounts();
            BigDecimal total = accounts.stream()
                    .map(BankAssetResponse.BankAssetItem::balance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            String traceId = TraceContext.traceId();
            if (!StringUtils.hasText(traceId)) {
                traceId = TraceContext.ensureTraceId(null);
            }

            return new BankAssetResponse(customerId, accounts, total, "TWD", traceId);
        } finally {
            sample.stop(Timer.builder("bank.asset.fetch.latency")
                    .description("Time spent generating bank asset response")
                    .register(meterRegistry));
        }
    }

    private List<BankAssetResponse.BankAssetItem> generateAccounts() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int accountCount = random.nextInt(1, 4);
        return IntStream.range(0, accountCount)
                .mapToObj(idx -> new BankAssetResponse.BankAssetItem(
                        "ACC-" + (1000 + idx) + "-" + Instant.now().toEpochMilli(),
                        randomBalance(),
                        CURRENCIES.get(random.nextInt(CURRENCIES.size()))
                ))
                .toList();
    }

    private BigDecimal randomBalance() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double amount = random.nextDouble(1_000, 500_000);
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
}
