package com.poc.svc.insurance.controller;

import com.poc.svc.insurance.service.InsuranceAssetService;
import com.poc.svc.insurance.service.InsuranceAssetService.InsuranceAssetResponse;
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

@WebMvcTest(controllers = InsuranceAssetController.class)
class InsuranceAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InsuranceAssetService insuranceAssetService;

    @Test
    void getCustomerAssets_returnsPayloadAndTraceId() throws Exception {
        InsuranceAssetResponse response = new InsuranceAssetResponse(
                "customer-321",
                List.of(
                        new InsuranceAssetResponse.InsuranceAssetItem("LIF-1000", "life", BigDecimal.valueOf(100000), "paid", "TWD", Instant.parse("2025-01-01T00:00:00Z")),
                        new InsuranceAssetResponse.InsuranceAssetItem("HLT-1001", "health", BigDecimal.valueOf(50000), "due", "TWD", Instant.parse("2025-01-01T00:05:00Z"))
                ),
                BigDecimal.valueOf(150000),
                "TWD",
                "trace-insurance"
        );
        Mockito.when(insuranceAssetService.getCustomerAssets("customer-321"))
                .thenReturn(response);

        mockMvc.perform(get("/insurance/customers/{customerId}/assets", "customer-321")
                        .header("X-Trace-Id", "trace-insurance")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", equalTo("trace-insurance")))
                .andExpect(jsonPath("$.customerId").value("customer-321"))
                .andExpect(jsonPath("$.insuranceAssets", hasSize(2)))
                .andExpect(jsonPath("$.totalCoverage").value(150000))
                .andExpect(jsonPath("$.currency").value("TWD"))
                .andExpect(jsonPath("$.traceId").value("trace-insurance"));
    }

    @Test
    void getCustomerAssets_generatesTraceIdWhenMissingHeader() throws Exception {
        InsuranceAssetResponse response = new InsuranceAssetResponse(
                "customer-654",
                List.of(new InsuranceAssetResponse.InsuranceAssetItem("LIF-2000", "life", BigDecimal.valueOf(500000), "paid", "TWD", Instant.parse("2025-02-01T12:00:00Z"))),
                BigDecimal.valueOf(500000),
                "TWD",
                null
        );
        Mockito.when(insuranceAssetService.getCustomerAssets("customer-654"))
                .thenReturn(response);

        mockMvc.perform(get("/insurance/customers/{customerId}/assets", "customer-654")
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
