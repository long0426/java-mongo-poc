package com.project.assets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "assets.currency")
public class CurrencyConversionProperties {

    private Map<String, BigDecimal> rates = new HashMap<>();

    public Map<String, BigDecimal> getRates() {
        return rates;
    }

    public void setRates(Map<String, BigDecimal> rates) {
        this.rates = rates;
    }

    public BigDecimal findRate(String fromCurrency, String toCurrency) {
        String key = normalize(fromCurrency) + ":" + normalize(toCurrency);
        return rates.get(key);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
