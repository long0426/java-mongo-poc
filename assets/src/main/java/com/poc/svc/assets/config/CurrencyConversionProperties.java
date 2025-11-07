package com.poc.svc.assets.config;

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
        String normalizedFrom = normalize(fromCurrency);
        String normalizedTo = normalize(toCurrency);
        return rates.entrySet().stream()
                .filter(entry -> keyMatches(entry.getKey(), normalizedFrom, normalizedTo))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean keyMatches(String rawKey, String expectedFrom, String expectedTo) {
        if (rawKey == null) {
            return false;
        }
        String trimmed = rawKey.trim().toUpperCase();
        String sanitized = trimmed.replaceAll("[^A-Z0-9]", "");
        if (sanitized.equals(expectedFrom + expectedTo)) {
            return true;
        }
        int delimiterIndex = trimmed.indexOf(':');
        if (delimiterIndex < 0) {
            return false;
        }

        String keyFrom = normalize(trimmed.substring(0, delimiterIndex));
        String keyTo = normalize(trimmed.substring(delimiterIndex + 1));
        return keyFrom.equals(expectedFrom) && keyTo.equals(expectedTo);
    }
}
