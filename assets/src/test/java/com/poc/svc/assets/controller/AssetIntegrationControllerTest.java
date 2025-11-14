package com.poc.svc.assets.controller;

import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.exception.AssetAggregationException;
import com.poc.svc.assets.service.AssetAggregationService;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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

    @MockBean
    private AssetAggregationService assetAggregationService;

    @Test
    @DisplayName("should return aggregated response with trace header")
    void aggregateCustomerAssets_success() throws Exception {
        List<Document> response = List.of(new Document()
                .append("customerId", "c-001")
                .append("traceId", "agg-trace")
                .append("components", List.of(new Document("source", "BANK"))));

        Mockito.when(assetAggregationService.aggregateCustomerAssets(eq("c-001"))).thenReturn(response);

        mockMvc.perform(get("/assets/customers/{customerId}", "c-001")
                        .header(TRACE_HEADER, "client-trace")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(TRACE_HEADER, equalTo("client-trace")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].customerId").value("c-001"))
                .andExpect(jsonPath("$[0].components", hasSize(1)));
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
