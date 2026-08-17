# Spring Boot Bean Scopes & The Singleton-Prototype Trap

## What is a Bean Scope?
In Spring Boot, a "Bean" is simply an object that is instantiated, assembled, and managed by the Spring IoC (Inversion of Control) container. The "Scope" of a bean determines its lifecycle and visibility—essentially, how many instances of that bean Spring will create and when it will create them.

---

## The Core Scopes

### 1. Singleton (Default)
*   **Behavior:** Spring creates exactly **one** instance of the bean per IoC container. Every time this bean is requested or injected, Spring provides the exact same instance.
*   **Annotation:** `@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)` or simply rely on the default behavior without adding an annotation.
*   **Best For:** Stateless beans. Services, Repositories, and Configuration classes should almost always be singletons. They don't hold conversational state, so sharing them is thread-safe and memory-efficient.

```java
@Service
public class UserService {
    // One instance shared across the entire application
}
```

### 2. Prototype
*   **Behavior:** Spring creates a **new** instance of the bean *every single time* it is requested from the container.
*   **Annotation:** `@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)` or `@Scope("prototype")`.
*   **Best For:** Stateful beans, or objects where you need a fresh, independent instance for every operation (e.g., a task processor that holds intermediate state).
*   *Caveat:* Spring does not manage the complete lifecycle of a prototype bean. It instantiates, configures, and hands it over to you. Destruction callbacks (like `@PreDestroy`) are **not** automatically called for prototype beans.

```java
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ReportGenerator {
    // New instance created every time it is injected or requested
}
```

## Web-Aware Scopes (Web Applications Only)
These scopes are only available if you are running a web-aware Spring ApplicationContext (like a standard Spring Boot web app).

### 3. Request
*   **Behavior:** A new bean instance is created for every single HTTP request. Once the HTTP request finishes and the response is sent, the bean is destroyed.
*   **Annotation:** `@RequestScope`
*   **Best For:** Storing request-specific data, like tracking request IDs, parsing specific headers, or holding authentication details just for the duration of that specific API call.

### 4. Session
*   **Behavior:** A new bean instance is created for every HTTP Session. It lives as long as the user's session lives (usually determined by a timeout).
*   **Annotation:** `@SessionScope`
*   **Best For:** Storing user preferences, shopping cart data, or user-specific conversational state in a traditional stateful web application.

---

## The Trap: Singleton Injecting Prototype

### The Problem
Because Singletons are the default, developers often try to inject a Prototype bean into a Singleton bean, expecting the Singleton to use a fresh Prototype instance every time it does something. **This does not work as expected.**

**Why?** 
The Singleton bean is initialized only *once* during application startup. When Spring wires the Singleton, it resolves its dependencies (including the Prototype bean) *once*. It requests a Prototype instance, injects it into the Singleton, and then the Singleton holds onto that exact same instance forever. The prototype scope is effectively nullified.

#### Example of the Trap:
```java
@Component
@Scope("prototype")
public class PrototypeWorker {
    private String timestamp = String.valueOf(System.currentTimeMillis());
    
    public void doWork() {
        System.out.println("Working... " + timestamp);
    }
}

@Service
public class SingletonManager {
    @Autowired
    private PrototypeWorker worker; // INJECTED ONCE AT STARTUP

    public void execute() {
        // Will print the EXACT SAME timestamp every time you call execute()!
        worker.doWork(); 
    }
}
```

### The Solutions
To fix this, the Singleton needs a way to ask the Spring container for a fresh instance at runtime, right when it needs it.

#### Solution 1: Use `ObjectProvider<T>` (Recommended & Modern)
Instead of injecting the Prototype bean directly, inject an `ObjectProvider` wrapper. This acts as a factory, allowing you to request a bean on demand.

```java
@Service
public class SingletonManager {
    @Autowired
    private ObjectProvider<PrototypeWorker> workerProvider;

    public void execute() {
        // Asks Spring for a new instance right now
        PrototypeWorker worker = workerProvider.getObject(); 
        worker.doWork(); 
    }
}
```

#### Solution 2: The `@Lookup` Annotation
Spring uses CGLIB (bytecode generation) to override the method at runtime and fetch a new bean directly from the application context. This keeps your code clean from Spring framework API imports like `ObjectProvider`.

```java
@Service
public class SingletonManager { 
    
    @Lookup
    public PrototypeWorker getWorker() {
        // Spring overrides this method at runtime. 
        // Returning null is fine; Spring replaces the implementation.
        return null; 
    }

    public void execute() {
        PrototypeWorker worker = getWorker(); // Gets a fresh instance!
        worker.doWork();
    }
}
```

#### Solution 3: Proxy Mode on the Prototype
You can tell Spring to inject a proxy of the Prototype bean instead of the actual bean. Whenever a method is called on the proxy, the proxy internally fetches a new instance of the real bean and delegates the call to it.

```java
// Notice the proxyMode attribute!
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class PrototypeWorker {
    // ...
}

@Service
public class SingletonManager {
    @Autowired
    private PrototypeWorker worker; // This is now a CGLIB Proxy!

    public void execute() {
        // The proxy fetches a new instance internally every time a method is called
        worker.doWork(); 
    }
}
```
