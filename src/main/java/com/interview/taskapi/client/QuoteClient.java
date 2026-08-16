package com.interview.taskapi.client;

import org.springframework.web.service.annotation.GetExchange;

/**
 * HTTP INTERFACE CLIENT - declare the contract, Spring generates the implementation.
 *
 * INTERVIEW: "How do you call another service?"
 *   RestTemplate  - legacy, blocking, in maintenance mode. Do not pick it for new code.
 *   WebClient     - reactive, non-blocking; works fine in a blocking app too.
 *   RestClient    - modern synchronous fluent client (Spring 6.1+). The sane default.
 *   HTTP Interface- declarative, Feign-style, built on any of the above. Spring Boot 4
 *                   promotes these to first-class beans with auto-registration.
 *
 * Say this and you sound current: "Boot 4 gives declarative HTTP service clients
 * natively, so we dropped the OpenFeign dependency."
 */
public interface QuoteClient {

    @GetExchange("/quotes/random")
    Quote randomQuote();

    record Quote(Long id, String quote, String author) { }
}
