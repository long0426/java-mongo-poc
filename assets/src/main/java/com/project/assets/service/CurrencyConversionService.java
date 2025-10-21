package com.project.assets.service;

import java.math.BigDecimal;

public interface CurrencyConversionService {

    ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency);

    record ConversionResult(BigDecimal convertedAmount, BigDecimal exchangeRate) {
    }
}
