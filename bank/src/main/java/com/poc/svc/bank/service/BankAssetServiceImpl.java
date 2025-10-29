package com.poc.svc.bank.service;

import com.poc.svc.bank.logging.TraceContext;
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
    private static final List<String> ACCOUNT_NAMES = List.of(
            "日常支票戶",
            "高收益儲蓄",
            "旅遊備用金",
            "教育基金",
            "雨天備用金",
            "彈性投資"
    );

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
                .mapToObj(idx -> {
                    String accountId = "ACC-" + (1000 + idx) + "-" + Instant.now().toEpochMilli();
                    BigDecimal balance = randomBalance();
                    String currency = CURRENCIES.get(random.nextInt(CURRENCIES.size()));
                    String assetName = ACCOUNT_NAMES.get(random.nextInt(ACCOUNT_NAMES.size())) + " " + (idx + 1);
                    return new BankAssetResponse.BankAssetItem(accountId, assetName, balance, currency);
                })
                .toList();
    }

    private BigDecimal randomBalance() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double amount = random.nextDouble(1_000, 500_000);
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }
}
