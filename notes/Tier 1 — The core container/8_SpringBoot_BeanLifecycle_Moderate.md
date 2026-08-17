# Spring Boot Bean Lifecycle

## Overview
The Bean Lifecycle represents the journey of a Spring Bean from its creation to its destruction. The Spring Inversion of Control (IoC) container completely manages this process. Understanding this lifecycle is crucial when you need to execute custom logic right after a bean is created or clean up resources right before it is destroyed.

---

## The Lifecycle Stages (In Order)

### 1. Instantiation
*   **What happens:** Spring takes the blueprint (your class) and creates a new object instance in memory.
*   **Mechanism:** It calls the constructor (either the default no-args constructor or a constructor annotated with `@Autowired` for constructor injection).

### 2. Populate Properties (Dependency Injection)
*   **What happens:** Once the object exists, Spring injects its required dependencies.
*   **Mechanism:** Field injection (`@Autowired` on variables) and Setter injection happen at this stage.

### 3. Aware Callbacks
*   **What happens:** Sometimes a bean needs to know something about the Spring container it lives in. Spring provides "Aware" interfaces to pass this internal framework context directly to the bean.
*   **Common Examples:** 
    *   `BeanNameAware`: Passes the bean's configured name.
    *   `ApplicationContextAware`: Passes the actual IoC container context to the bean.

### 4. BeanPostProcessor (Before Initialization)
*   **What happens:** Spring allows special components called `BeanPostProcessor`s to intercept the bean and modify it *before* any custom startup logic runs. 
*   **Everyday Use:** Mostly used internally by framework developers, but you can implement this interface to alter beans globally across your application.

### 5. Initialization (`@PostConstruct`)
*   **What happens:** Now that the bean is created and all dependencies are injected, you can run custom startup or validation logic. If you try to use an `@Autowired` dependency in the constructor, it will be null—this is the safe place to use them.
*   **Mechanism:** 
    *   Method annotated with `@PostConstruct` (Modern & Recommended).
    *   Implementing `InitializingBean` and overriding `afterPropertiesSet()`.
    *   Custom `initMethod` defined in an `@Bean` configuration.

### 6. BeanPostProcessor (After Initialization)
*   **What happens:** Similar to Step 4, but runs *after* initialization. 
*   **Everyday Use:** This is where Spring does its magic for Aspect-Oriented Programming (AOP). If your bean has annotations like `@Transactional` or `@Async`, Spring intercepts the bean here and wraps it in a CGLIB or JDK proxy before returning it to the container.

### 7. Destruction (`@PreDestroy`)
*   **What happens:** When the application context is gracefully shutting down, Spring allows beans to clean up resources.
*   **Mechanism:** 
    *   Method annotated with `@PreDestroy` (Modern & Recommended).
    *   Implementing `DisposableBean` and overriding `destroy()`.
    *   Custom `destroyMethod` defined in an `@Bean` configuration.

> **Important Note:** Destruction callbacks are only executed for **Singleton** beans. Spring does not track Prototype beans for destruction; it is up to the garbage collector and your custom code to clean those up.

---

## Code Example: The Lifecycle in Action

Here is a practical example showing a bean going through the standard phases you will encounter in day-to-day development.

```java
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LifecycleDemoBean implements BeanNameAware {

    private SomeDependency dependency;
    private String beanName;

    // 1. Instantiation
    public LifecycleDemoBean() {
        System.out.println("1. Instantiation: Constructor called.");
    }

    // 2. Populate Properties
    @Autowired
    public void setDependency(SomeDependency dependency) {
        this.dependency = dependency;
        System.out.println("2. Populate Properties: Dependency injected.");
    }

    // 3. Aware Callbacks
    @Override
    public void setBeanName(String name) {
        this.beanName = name;
        System.out.println("3. Aware Callbacks: Bean name set to '" + name + "'.");
    }

    // 4 & 6. BeanPostProcessors run behind the scenes here by Spring

    // 5. Initialization
    @PostConstruct
    public void init() {
        System.out.println("5. Initialization: @PostConstruct called.");
        // Good place to load caches, validate properties, or open external connections.
        // It is safe to use 'this.dependency' here.
    }

    // 7. Destruction
    @PreDestroy
    public void cleanup() {
        System.out.println("7. Destruction: @PreDestroy called.");
        // Good place to close connections, flush streams, or clear caches before shutdown.
    }
}
```
