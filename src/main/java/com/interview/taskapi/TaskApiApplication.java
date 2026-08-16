package com.interview.taskapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * INTERVIEW: "What does @SpringBootApplication actually do?"
 * It is a meta-annotation combining three things:
 *   1. @SpringBootConfiguration  -> a @Configuration class; the source of bean definitions.
 *   2. @ComponentScan            -> scans THIS package and everything below it.
 *                                   (This is why the main class lives at the root package.)
 *   3. @EnableAutoConfiguration  -> imports auto-configuration classes listed in
 *                                   META-INF/spring/...AutoConfiguration.imports of every
 *                                   jar on the classpath, each guarded by @Conditional*
 *                                   (@ConditionalOnClass, @ConditionalOnMissingBean,
 *                                    @ConditionalOnProperty ...).
 *
 * HOMEWORK (do this, it is the single highest-ROI 10 minutes of your prep):
 *   ./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug
 * Read the "Positive matches" / "Negative matches" report. Then you can say in the
 * interview: "auto-config isn't magic, it's conditional @Bean definitions that back off
 * the moment I define my own bean."
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TaskApiApplication {

    public static void main(String[] args) {
        // SpringApplication.run(): creates the ApplicationContext, registers bean
        // definitions, runs BeanFactoryPostProcessors, instantiates singletons eagerly,
        // starts the embedded Tomcat, then fires ApplicationReadyEvent.
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
