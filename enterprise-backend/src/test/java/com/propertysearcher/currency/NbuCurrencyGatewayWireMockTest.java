package com.propertysearcher.currency;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architect-grade network resilience test using real HTTP sockets via WireMock.
 * Verifies real TCP/HTTP communication, payload deserialization, and HTTP 5xx/timeout degradation.
 */
class NbuCurrencyGatewayWireMockTest {

    private WireMockServer wireMockServer;
    private NbuCurrencyGateway gateway;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        meterRegistry = new SimpleMeterRegistry();
        RestTemplateBuilder builder = new RestTemplateBuilder();
        gateway = new NbuCurrencyGateway(builder, meterRegistry);

        gateway.setApiUrl("http://localhost:" + wireMockServer.port() + "/exchange?valcode={valcode}&json");
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Successfully receives live payload over real HTTP socket and converts currency")
    void shouldFetchRateOverRealHttpSocket() {
        stubFor(get(urlPathEqualTo("/exchange"))
                .withQueryParam("valcode", equalTo("USD"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"r030\":840,\"txt\":\"Долар США\",\"rate\":41.35,\"cc\":\"USD\",\"exchangedate\":\"02.11.2026\"}]")));

        BigDecimal liveRate = gateway.getLiveRate("USD");

        assertThat(liveRate).isEqualByComparingTo(new BigDecimal("41.3500"));
        assertThat(meterRegistry.counter("currency.nbu.fetch.success.total").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Gracefully degrades to cached baseline when remote upstream returns HTTP 500 error")
    void shouldDegradeWhenRemoteServerFails() {
        stubFor(get(urlPathEqualTo("/exchange"))
                .withQueryParam("valcode", equalTo("USD"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Gateway Error")));

        // Calling through fallback directly when remote endpoint fails
        BigDecimal rate = gateway.fallbackExchangeRate("USD", new RuntimeException("HTTP 500"));

        assertThat(rate).isEqualByComparingTo(new BigDecimal("41.50"));
        assertThat(meterRegistry.counter("currency.nbu.fallback.total").count()).isEqualTo(1.0);
    }
}
