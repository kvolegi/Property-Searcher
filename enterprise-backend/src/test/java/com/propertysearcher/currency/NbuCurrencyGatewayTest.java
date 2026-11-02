package com.propertysearcher.currency;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class NbuCurrencyGatewayTest {

    private RestTemplate restTemplate;
    private SimpleMeterRegistry meterRegistry;
    private NbuCurrencyGateway gateway;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        RestTemplateBuilder builder = Mockito.mock(RestTemplateBuilder.class);
        when(builder.setConnectTimeout(any())).thenReturn(builder);
        when(builder.setReadTimeout(any())).thenReturn(builder);
        when(builder.build()).thenReturn(restTemplate);

        meterRegistry = new SimpleMeterRegistry();
        gateway = new NbuCurrencyGateway(builder, meterRegistry);
    }

    @Test
    @DisplayName("Successfully parses remote NBU rate and converts amount")
    void shouldFetchAndConvertLiveRate() {
        NbuCurrencyGateway.NbuRateResponse mockItem = new NbuCurrencyGateway.NbuRateResponse();
        mockItem.setCc("USD");
        mockItem.setRate(41.25);
        NbuCurrencyGateway.NbuRateResponse[] response = new NbuCurrencyGateway.NbuRateResponse[]{mockItem};

        when(restTemplate.getForObject(anyString(), eq(NbuCurrencyGateway.NbuRateResponse[].class), eq("USD")))
                .thenReturn(response);

        BigDecimal uah = gateway.convertToUah(new BigDecimal("100"), "USD");

        assertThat(uah).isEqualByComparingTo(new BigDecimal("4125.00"));
        assertThat(meterRegistry.counter("currency.nbu.fetch.success.total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Triggers degraded fallback and increments metric when remote NBU is unreachable")
    void shouldFallbackWhenNbuFails() {
        when(restTemplate.getForObject(anyString(), eq(NbuCurrencyGateway.NbuRateResponse[].class), eq("USD")))
                .thenThrow(new RestClientException("Connection timed out to NBU"));

        // Direct invocation of fallback logic (Resilience4j delegates here on failure)
        BigDecimal fallbackRate = gateway.fallbackExchangeRate("USD", new RestClientException("Connection timed out to NBU"));

        assertThat(fallbackRate).isGreaterThan(BigDecimal.ZERO);
        assertThat(fallbackRate).isEqualByComparingTo(new BigDecimal("41.50"));
        assertThat(meterRegistry.counter("currency.nbu.fallback.total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Bypasses remote network call for native UAH currency")
    void shouldReturnIdentityForUah() {
        BigDecimal uah = gateway.convertToUah(new BigDecimal("25000"), "UAH");
        assertThat(uah).isEqualByComparingTo(new BigDecimal("25000"));
    }
}
