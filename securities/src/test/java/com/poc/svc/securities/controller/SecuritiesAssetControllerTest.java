package com.poc.svc.securities.controller;

import com.poc.svc.securities.service.SecuritiesAssetService;
import com.poc.svc.securities.service.SecuritiesAssetService.SecuritiesAssetResponse;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SecuritiesAssetController.class)
class SecuritiesAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SecuritiesAssetService securitiesAssetService;

    @Test
    void getCustomerAssets_returnsPayloadAndTraceId() throws Exception {
        SecuritiesAssetResponse response = new SecuritiesAssetResponse(
                "customer-456",
                List.of(
                        new SecuritiesAssetResponse.SecurityHolding("stock", "XYZ", 100, BigDecimal.valueOf(56000), "TWD", "MEDIUM", Instant.parse("2025-01-01T00:00:00Z")),
                        new SecuritiesAssetResponse.SecurityHolding("bond", "US10Y", 50000, BigDecimal.valueOf(1500000), "USD", "LOW", Instant.parse("2025-01-01T00:05:00Z"))
                ),
                BigDecimal.valueOf(1556000),
                "TWD",
                "trace-security"
        );
        Mockito.when(securitiesAssetService.getCustomerAssets("customer-456"))
                .thenReturn(response);

        mockMvc.perform(get("/securities/customers/{customerId}/assets", "customer-456")
                        .header("X-Trace-Id", "trace-security")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", equalTo("trace-security")))
                .andExpect(jsonPath("$.customerId").value("customer-456"))
                .andExpect(jsonPath("$.securitiesAssets", hasSize(2)))
                .andExpect(jsonPath("$.totalMarketValue").value(1556000))
                .andExpect(jsonPath("$.currency").value("TWD"))
                .andExpect(jsonPath("$.traceId").value("trace-security"));
    }

    @Test
    void getCustomerAssets_generatesTraceIdWhenMissingHeader() throws Exception {
        SecuritiesAssetResponse response = new SecuritiesAssetResponse(
                "customer-789",
                List.of(new SecuritiesAssetResponse.SecurityHolding("fund", "FND-001", 20, BigDecimal.valueOf(120000), "TWD", "HIGH", Instant.parse("2025-01-02T12:00:00Z"))),
                BigDecimal.valueOf(120000),
                "TWD",
                null
        );
        Mockito.when(securitiesAssetService.getCustomerAssets("customer-789"))
                .thenReturn(response);

        mockMvc.perform(get("/securities/customers/{customerId}/assets", "customer-789")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", hasLengthGreaterThan(0)))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private Matcher<String> hasLengthGreaterThan(int length) {
        return new org.hamcrest.TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(String item) {
                return item != null && item.length() > length;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a string with length greater than " + length);
            }
        };
    }
}
