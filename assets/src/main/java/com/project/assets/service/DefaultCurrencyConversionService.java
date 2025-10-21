package com.project.assets.service;

import com.project.assets.config.CurrencyConversionProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DefaultCurrencyConversionService implements CurrencyConversionService {

    private final CurrencyConversionProperties properties;

    public DefaultCurrencyConversionService(CurrencyConversionProperties properties) {
        this.properties = properties;
    }

    @Override
    public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return new ConversionResult(amount.setScale(2, RoundingMode.HALF_UP), BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal rate = properties.findRate(fromCurrency, toCurrency);
        if (rate == null) {
            throw new IllegalArgumentException("Missing conversion rate for " + fromCurrency + " -> " + toCurrency);
        }
        BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new ConversionResult(converted, rate.setScale(4, RoundingMode.HALF_UP));
    }
}
