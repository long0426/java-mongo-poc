package com.poc.svc.assets.service.impl;

import com.poc.svc.assets.config.CurrencyConversionProperties;
import com.poc.svc.assets.service.CurrencyConversionService;
import com.poc.svc.assets.service.CurrencyConversionService.ConversionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DefaultCurrencyConversionService implements CurrencyConversionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCurrencyConversionService.class);

    private final CurrencyConversionProperties properties;

    public DefaultCurrencyConversionService(CurrencyConversionProperties properties) {
        this.properties = properties;
    }

    @Override
    public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("Currency codes must not be null");
        }
        if (fromCurrency.equalsIgnoreCase(toCurrency)) {
            return new ConversionResult(
                    amount.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP)
            );
        }
        BigDecimal rate = properties.findRate(fromCurrency, toCurrency);
        if (rate == null) {
            BigDecimal inverse = properties.findRate(toCurrency, fromCurrency);
            if (inverse != null && inverse.compareTo(BigDecimal.ZERO) != 0) {
                rate = BigDecimal.ONE.divide(inverse, 8, RoundingMode.HALF_UP);
            }
        }
        if (rate == null) {
            if (log.isWarnEnabled()) {
                log.warn("Missing conversion rate for {} -> {}. Known rates={}", fromCurrency, toCurrency, properties.getRates().keySet());
            }
            throw new IllegalArgumentException("Missing conversion rate for " + fromCurrency + " -> " + toCurrency);
        }
        BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return new ConversionResult(converted, rate.setScale(4, RoundingMode.HALF_UP));
    }

}
