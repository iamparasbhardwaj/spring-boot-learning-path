package com.interview.taskapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TIER 4: the full context. Slow, but if this fails your wiring is broken -
 * a missing bean, a bad property, an ambiguous @Autowired candidate.
 * Keep exactly a few of these, not hundreds.
 */
@SpringBootTest
class SmokeTest {

    @Test
    void contextLoads() { }
}
