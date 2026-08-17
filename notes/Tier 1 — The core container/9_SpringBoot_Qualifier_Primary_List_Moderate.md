# Resolving Dependency Conflicts in Spring Boot

## The Problem: Multiple Beans of the Same Type
In Spring, you often code to interfaces rather than concrete classes. This is great for decoupling, but it creates a problem for Spring's dependency injection (DI). 

If you have an interface `PaymentProcessor` and two implementations (`StripeProcessor` and `PayPalProcessor`), both will be registered as beans. When you try to `@Autowired PaymentProcessor`, Spring will throw a `NoUniqueBeanDefinitionException` because it doesn't know *which* implementation you want.

Spring provides three main ways to handle this: `@Primary`, `@Qualifier`, and injecting collections.

---

## 1. `@Primary` (The Default Fallback)
*   **What it does:** Marks a specific bean as the default choice when multiple beans of the same type exist.
*   **Best For:** Establishing a "standard" or "most common" implementation while still allowing other implementations to exist in the context.
*   **How it works:** If Spring sees multiple candidates for injection, it will look for one marked with `@Primary`. If exactly one is found, it injects that one.

```java
public interface PaymentProcessor {
    void process();
}

@Component
@Primary // <--- This makes Stripe the default choice
public class StripeProcessor implements PaymentProcessor {
    public void process() { System.out.println("Processing with Stripe"); }
}

@Component
public class PayPalProcessor implements PaymentProcessor {
    public void process() { System.out.println("Processing with PayPal"); }
}

@Service
public class CheckoutService {
    private final PaymentProcessor processor;

    // Spring injects StripeProcessor here because of @Primary
    @Autowired
    public CheckoutService(PaymentProcessor processor) {
        this.processor = processor;
    }
}
```

---

## 2. `@Qualifier` (The Specific Choice)
*   **What it does:** Explicitly tells Spring *exactly* which bean to inject by specifying the bean's name.
*   **Best For:** Overriding `@Primary`, or when you have multiple implementations and no clear "default" exists.
*   **How it works:** You place it alongside `@Autowired` (or constructor parameters) and give it the name of the bean. By default, a bean's name is its class name in camelCase (e.g., `paypalProcessor`), though you can customize it via `@Component("myCustomName")`.

> **Note:** `@Qualifier` has higher precedence than `@Primary`. If you use a `@Qualifier`, Spring ignores the `@Primary` annotation.

```java
@Service
public class InternationalCheckoutService {
    private final PaymentProcessor processor;

    @Autowired
    public InternationalCheckoutService(@Qualifier("payPalProcessor") PaymentProcessor processor) {
        this.processor = processor; // Injects PayPal, ignoring the @Primary on Stripe
    }
}
```

---

## 3. Injecting All Implementations (`List<T>` or `Map<String, T>`)
*   **What it does:** Instead of choosing *one* implementation, you tell Spring to inject *all* of them into a collection.
*   **Best For:** Implementing the **Strategy Pattern**, Chain of Responsibility, or when you need to iterate over all available handlers based on some runtime condition.
*   **How it works:** Spring automatically detects that you are asking for a `List` or `Map` of an interface. It finds every bean that implements the interface and populates the collection.

### Injecting as a `List`
```java
@Service
public class PaymentGatewayManager {
    
    // Spring puts both StripeProcessor and PayPalProcessor into this list
    private final List<PaymentProcessor> allProcessors;

    @Autowired
    public PaymentGatewayManager(List<PaymentProcessor> allProcessors) {
        this.allProcessors = allProcessors;
    }

    public void testAll() {
        for (PaymentProcessor processor : allProcessors) {
            processor.process();
        }
    }
}
```

### Injecting as a `Map` (Pro-Tip!)
If you inject a `Map<String, T>`, Spring will use the **Bean Name as the key** and the **Bean Instance as the value**. This is incredibly powerful for routing logic dynamically without using massive `switch` statements.

```java
@Service
public class DynamicPaymentService {

    // Keys will be "stripeProcessor" and "payPalProcessor"
    private final Map<String, PaymentProcessor> processorMap;

    @Autowired
    public DynamicPaymentService(Map<String, PaymentProcessor> processorMap) {
        this.processorMap = processorMap;
    }

    public void checkout(String method) {
        // 'method' could be passed from a REST API payload (e.g., "payPalProcessor")
        PaymentProcessor selectedProcessor = processorMap.get(method);
        
        if (selectedProcessor != null) {
            selectedProcessor.process();
        } else {
            throw new IllegalArgumentException("Unknown payment method");
        }
    }
}
```
