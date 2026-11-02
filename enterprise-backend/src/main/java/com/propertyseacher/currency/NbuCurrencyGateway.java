package com.propertyseacher.currency;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade resilience gateway for National Bank of Ukraine (NBU) currency exchange rates.
 * Employs Resilience4j Circuit Breaker, Exponential Backoff Retry, and cached fallback degradation.
 */
@Slf4j
@Service
public class NbuCurrencyGateway {

    private static final String NBU_API_URL = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?valcode={valcode}&json";
    private static final Map<String, BigDecimal> STATIC_DEFAULTS = Map.of(
            "USD", new BigDecimal("41.50"),
            "EUR", new BigDecimal("45.20"),
            "PLN", new BigDecimal("10.50"),
            "GBP", new BigDecimal("54.00")
    );

    private final RestTemplate restTemplate;
    private final Map<String, BigDecimal> cachedRates = new ConcurrentHashMap<>();
    private final Counter fallbackCounter;
    private final Counter successCounter;

    public NbuCurrencyGateway(RestTemplateBuilder restTemplateBuilder, MeterRegistry meterRegistry) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.fallbackCounter = Counter.builder("currency.nbu.fallback.total")
                .description("Total number of times currency exchange fell back to degraded cache/default")
                .register(meterRegistry);
        this.successCounter = Counter.builder("currency.nbu.fetch.success.total")
                .description("Total number of successful currency exchange rates fetched from NBU")
                .register(meterRegistry);
        // Seed initial safe baselines
        cachedRates.putAll(STATIC_DEFAULTS);
    }

    /**
     * Protected remote invocation guarded by Resilience4j Circuit Breaker & Retry.
     */
    @CircuitBreaker(name = "nbuCurrency", fallbackMethod = "fallbackExchangeRate")
    @Retry(name = "nbuCurrency")
    public BigDecimal getLiveRate(String currencyCode) {
        String code = currencyCode.toUpperCase();
        log.debug("Fetching live exchange rate from NBU for currency: {}", code);

        NbuRateResponse[] response = restTemplate.getForObject(NBU_API_URL, NbuRateResponse[].class, code);
        if (response != null && response.length > 0 && response[0].getRate() != null) {
            BigDecimal rate = BigDecimal.valueOf(response[0].getRate()).setScale(4, RoundingMode.HALF_UP);
            cachedRates.put(code, rate);
            successCounter.increment();
            log.info("Successfully refreshed NBU rate for {}: {}", code, rate);
            return rate;
        }

        throw new IllegalStateException("Empty or malformed payload from NBU exchange rate service for: " + code);
    }

    /**
     * Fallback handler triggered when Circuit Breaker is OPEN or Retries are exhausted.
     * Guarantees zero unhandled downtime for pricing conversions.
     */
    public BigDecimal fallbackExchangeRate(String currencyCode, Throwable ex) {
        String code = currencyCode.toUpperCase();
        fallbackCounter.increment();
        BigDecimal fallback = cachedRates.getOrDefault(code, STATIC_DEFAULTS.getOrDefault(code, BigDecimal.ONE));
        log.warn("CircuitBreaker/Retry active on NBU gateway for {}. Degraded fallback to rate {}. Cause: {}",
                code, fallback, ex.getMessage());
        return fallback;
    }

    public BigDecimal convertToUah(BigDecimal amount, String currencyCode) {
        if ("UAH".equalsIgnoreCase(currencyCode) || amount == null) {
            return amount != null ? amount : BigDecimal.ZERO;
        }
        BigDecimal rate = getLiveRate(currencyCode);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public static class NbuRateResponse {
        private String cc;
        private Double rate;

        public String getCc() { return cc; }
        public void setCc(String cc) { this.cc = cc; }
        public Double getRate() { return rate; }
        public void setRate(Double rate) { this.rate = rate; }
    }
}
