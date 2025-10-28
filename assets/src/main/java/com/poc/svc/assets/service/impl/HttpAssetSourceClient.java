package com.poc.svc.assets.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.svc.assets.exception.AssetSourceMissingException;
import com.poc.svc.assets.service.AssetSourceClient;
import com.poc.svc.assets.service.AssetSourceClient.BankAssetResult;
import com.poc.svc.assets.service.AssetSourceClient.InsuranceAssetResult;
import com.poc.svc.assets.service.AssetSourceClient.SecuritiesAssetResult;
import com.poc.svc.assets.service.BankAssetWriter;
import com.poc.svc.assets.util.TraceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Component
public class HttpAssetSourceClient implements AssetSourceClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RestTemplate restTemplate;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final String bankBaseUrl;
    private final String securitiesBaseUrl;
    private final String insuranceBaseUrl;

    public HttpAssetSourceClient(RestTemplate restTemplate,
                                 @Qualifier("assetAsyncExecutor") Executor executor,
                                 ObjectMapper objectMapper,
                                 @Value("${assets.bank.base-url}") String bankBaseUrl,
                                 @Value("${assets.securities.base-url}") String securitiesBaseUrl,
                                 @Value("${assets.insurance.base-url}") String insuranceBaseUrl) {
        this.restTemplate = restTemplate;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.bankBaseUrl = bankBaseUrl;
        this.securitiesBaseUrl = securitiesBaseUrl;
        this.insuranceBaseUrl = insuranceBaseUrl;
    }

    @Override
    public CompletableFuture<BankAssetResult> fetchBankAssets(String customerId, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            BankAssetApiResponse body = exchangeForEntity(
                    bankBaseUrl + "/bank/customers/{customerId}/assets",
                    customerId,
                    traceId,
                    BankAssetApiResponse.class
            );

            Map<String, BigDecimal> summary = body.bankAssets().stream()
                    .collect(Collectors.groupingBy(
                            BankAssetItem::currency,
                            LinkedHashMap::new,
                            Collectors.reducing(BigDecimal.ZERO, BankAssetItem::balance, BigDecimal::add)));

            List<BankAssetWriter.BankAssetWriteRequest.CurrencyAmount> currencySummary = summary.entrySet().stream()
                    .map(entry -> new BankAssetWriter.BankAssetWriteRequest.CurrencyAmount(entry.getKey(), entry.getValue()))
                    .toList();

            Map<String, Object> payload = objectMapper.convertValue(body, MAP_TYPE);

            return new BankAssetResult(
                    body.customerId(),
                    payload,
                    body.totalBalance(),
                    body.currency(),
                    currencySummary,
                    Instant.now(),
                    ensureTraceId(body.traceId(), traceId)
            );
        }, executor);
    }

    @Override
    public CompletableFuture<SecuritiesAssetResult> fetchSecuritiesAssets(String customerId, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            SecuritiesAssetApiResponse body = exchangeForEntity(
                    securitiesBaseUrl + "/securities/customers/{customerId}/assets",
                    customerId,
                    traceId,
                    SecuritiesAssetApiResponse.class
            );

            Map<String, Object> payload = objectMapper.convertValue(body, MAP_TYPE);

            return new SecuritiesAssetResult(
                    body.customerId(),
                    payload,
                    body.totalMarketValue(),
                    body.currency(),
                    body.securitiesAssets().size(),
                    Instant.now(),
                    ensureTraceId(body.traceId(), traceId)
            );
        }, executor);
    }

    @Override
    public CompletableFuture<InsuranceAssetResult> fetchInsuranceAssets(String customerId, String traceId) {
        return CompletableFuture.supplyAsync(() -> {
            InsuranceAssetApiResponse body = exchangeForEntity(
                    insuranceBaseUrl + "/insurance/customers/{customerId}/assets",
                    customerId,
                    traceId,
                    InsuranceAssetApiResponse.class
            );

            Map<String, Object> payload = objectMapper.convertValue(body, MAP_TYPE);

            return new InsuranceAssetResult(
                    body.customerId(),
                    payload,
                    body.totalCoverage(),
                    body.currency(),
                    body.insuranceAssets().size(),
                    Instant.now(),
                    ensureTraceId(body.traceId(), traceId)
            );
        }, executor);
    }

    private HttpHeaders buildHeaders(String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(TraceContext.TRACE_ID_HEADER, traceId);
        return headers;
    }

    private String ensureTraceId(String responseTraceId, String fallbackTraceId) {
        if (responseTraceId != null && !responseTraceId.isBlank()) {
            return responseTraceId;
        }
        TraceContext.setTraceId(fallbackTraceId);
        return fallbackTraceId;
    }

    private <T> T exchangeForEntity(String urlTemplate, String customerId, String traceId, Class<T> clazz) {
        try {
            ResponseEntity<T> response = restTemplate.exchange(
                    urlTemplate,
                    HttpMethod.GET,
                    new HttpEntity<>(buildHeaders(traceId)),
                    clazz,
                    customerId
            );
            T body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("Empty response body from asset source");
            }
            return body;
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new AssetSourceMissingException("Asset source returned 404 for customer %s".formatted(customerId), notFound);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BankAssetApiResponse(
            String customerId,
            List<BankAssetItem> bankAssets,
            BigDecimal totalBalance,
            String currency,
            @JsonProperty("traceId") String traceId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BankAssetItem(
            String accountId,
            BigDecimal balance,
            String currency
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SecuritiesAssetApiResponse(
            String customerId,
            List<SecuritiesHolding> securitiesAssets,
            BigDecimal totalMarketValue,
            String currency,
            @JsonProperty("traceId") String traceId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SecuritiesHolding(
            String securityType,
            String symbol,
            Integer holdings,
            BigDecimal marketValue,
            String currency,
            String riskLevel,
            Instant lastUpdated
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InsuranceAssetApiResponse(
            String customerId,
            List<InsuranceAssetItem> insuranceAssets,
            BigDecimal totalCoverage,
            String currency,
            @JsonProperty("traceId") String traceId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InsuranceAssetItem(
            String policyNumber,
            String policyType,
            BigDecimal coverage,
            String premiumStatus,
            String currency,
            Instant lastUpdated
    ) {
    }
}
