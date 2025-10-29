package com.poc.svc.assets.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poc.svc.assets.dto.AggregatedAssetResponse;
import com.poc.svc.assets.dto.AssetComponentStatus;
import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.exception.AssetAggregationException;
import com.poc.svc.assets.service.AssetAggregationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AssetIntegrationController.class)
@Import(ErrorHandlingAdvice.class)
class AssetIntegrationControllerTest {

    private static final String TRACE_HEADER = "X-Trace-Id";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AssetAggregationService assetAggregationService;

    @Test
    @DisplayName("should return aggregated response with trace header")
    void aggregateCustomerAssets_success() throws Exception {
        AggregatedAssetResponse response = new AggregatedAssetResponse(
                "c-001",
                "TWD",
                BigDecimal.valueOf(1000),
                List.of(new AggregatedAssetResponse.CurrencyAmount("TWD", BigDecimal.valueOf(1000))),
                List.of(
                        new AggregatedAssetResponse.Component(
                                AssetSourceType.BANK,
                                AssetComponentStatus.SUCCESS,
                                BigDecimal.valueOf(500),
                                "TWD",
                                BigDecimal.ONE,
                                "bank-trace",
                                Instant.parse("2025-10-20T02:34:56Z"),
                                List.of(Map.of("assetName", "Bank Account")),
                                "doc-bank"
                        ),
                        new AggregatedAssetResponse.Component(
                                AssetSourceType.SECURITIES,
                                AssetComponentStatus.SUCCESS,
                                BigDecimal.valueOf(300),
                                "USD",
                                BigDecimal.valueOf(32),
                                "sec-trace",
                                Instant.parse("2025-10-20T02:35:56Z"),
                                List.of(Map.of("assetName", "Security Holding")),
                                "doc-sec"
                        ),
                        new AggregatedAssetResponse.Component(
                                AssetSourceType.INSURANCE,
                                AssetComponentStatus.SUCCESS,
                                BigDecimal.valueOf(200),
                                "TWD",
                                BigDecimal.ONE,
                                "ins-trace",
                                Instant.parse("2025-10-20T02:36:56Z"),
                                List.of(Map.of("assetName", "Insurance Policy")),
                                "doc-ins"
                        )
                ),
                AggregatedAssetResponse.AggregationStatus.COMPLETED,
                Instant.parse("2025-10-20T02:37:56Z"),
                "agg-trace"
        );

        Mockito.when(assetAggregationService.aggregateCustomerAssets(eq("c-001"))).thenReturn(response);

        mockMvc.perform(get("/assets/customers/{customerId}", "c-001")
                        .header(TRACE_HEADER, "client-trace")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(TRACE_HEADER, equalTo("client-trace")))
                .andExpect(jsonPath("$.customerId").value("c-001"))
                .andExpect(jsonPath("$.components", hasSize(3)))
                .andExpect(jsonPath("$.components[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("should return gateway timeout when aggregation fails")
    void aggregateCustomerAssets_failure() throws Exception {
        AssetAggregationException exception = new AssetAggregationException(
                "Aggregation failed",
                List.of(AssetSourceType.SECURITIES, AssetSourceType.INSURANCE),
                null
        );

        Mockito.when(assetAggregationService.aggregateCustomerAssets(eq("c-999"))).thenThrow(exception);

        mockMvc.perform(get("/assets/customers/{customerId}", "c-999")
                        .header(TRACE_HEADER, "trace-fail")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("ASSET_AGGREGATION_FAILED"))
                .andExpect(jsonPath("$.details.failedSources", hasSize(2)));
    }
}
