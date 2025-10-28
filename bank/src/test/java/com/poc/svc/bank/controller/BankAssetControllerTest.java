package com.poc.svc.bank.controller;

import com.poc.svc.bank.service.BankAssetService;
import com.poc.svc.bank.service.BankAssetService.BankAssetResponse;
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
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BankAssetController.class)
class BankAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BankAssetService bankAssetService;

    @Test
    void getCustomerAssets_returnsPayloadAndTraceId() throws Exception {
        BankAssetResponse response = new BankAssetResponse(
                "customer-123",
                List.of(
                        new BankAssetResponse.BankAssetItem("001", BigDecimal.valueOf(500), "TWD"),
                        new BankAssetResponse.BankAssetItem("002", BigDecimal.valueOf(1500), "USD")
                ),
                BigDecimal.valueOf(2000),
                "TWD",
                "trace-123"
        );
        Mockito.when(bankAssetService.getCustomerAssets("customer-123"))
                .thenReturn(response);

        mockMvc.perform(get("/bank/customers/{customerId}/assets", "customer-123")
                        .header("X-Trace-Id", "trace-123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", equalTo("trace-123")))
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.bankAssets", hasSize(2)))
                .andExpect(jsonPath("$.totalBalance").value(2000))
                .andExpect(jsonPath("$.currency").value("TWD"))
                .andExpect(jsonPath("$.traceId").value("trace-123"));
    }

    @Test
    void getCustomerAssets_generatesTraceIdWhenMissingHeader() throws Exception {
        BankAssetResponse response = new BankAssetResponse(
                "customer-789",
                List.of(new BankAssetResponse.BankAssetItem("ACC-123", BigDecimal.valueOf(5000), "TWD")),
                BigDecimal.valueOf(5000),
                "TWD",
                null
        );
        Mockito.when(bankAssetService.getCustomerAssets("customer-789"))
                .thenReturn(response);

        mockMvc.perform(get("/bank/customers/{customerId}/assets", "customer-789")
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
