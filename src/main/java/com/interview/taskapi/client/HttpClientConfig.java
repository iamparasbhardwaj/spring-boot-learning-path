package com.interview.taskapi.client;

import com.interview.taskapi.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

/**
 * The explicit wiring, so you can SEE what the framework does for you.
 * Boot 4 can register these automatically via @ImportHttpServices, but building
 * the proxy by hand once is what lets you explain it in an interview.
 *
 * INTERVIEW: "What resilience would you add here?"
 * Timeouts (below - always, an unbounded HTTP call is an outage waiting to happen),
 * retries with jitter for idempotent calls only, a circuit breaker, and a bulkhead
 * so one slow dependency cannot exhaust your thread pool. Spring Framework 7 ships
 * @Retryable and @ConcurrencyLimit in core, so Resilience4j is no longer mandatory.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    QuoteClient quoteClient(RestClient.Builder builder, AppProperties properties) {
        RestClient restClient = builder
                .baseUrl(properties.quotesBaseUrl())
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(QuoteClient.class);
    }

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
                    setReadTimeout((int) Duration.ofSeconds(3).toMillis());
                }});
    }
}
