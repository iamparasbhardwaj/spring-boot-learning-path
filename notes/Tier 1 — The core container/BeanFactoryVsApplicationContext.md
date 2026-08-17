**ApplicationContext vs BeanFactory**  
**Eager vs Lazy Initialization**  
*(Low-depth explanation)*

---

### BeanFactory vs ApplicationContext

| Feature                  | BeanFactory                          | ApplicationContext                          |
|--------------------------|--------------------------------------|---------------------------------------------|
| What it is               | Basic Spring container               | Advanced Spring container                   |
| Inheritance              | The root interface                   | Extends BeanFactory                         |
| Initialization           | Lazy by default                      | Eager by default (singletons)               |
| Extra features           | Just dependency injection            | + Events, i18n, AOP, resources, etc.        |
| Usage today              | Rarely used directly                 | Used in almost every Spring/Spring Boot app |

**Simple mental model**  
- `BeanFactory` = basic bean container  
- `ApplicationContext` = BeanFactory + many useful extra features  

In real projects (especially Spring Boot) you almost always work with **ApplicationContext**.

---

### Eager vs Lazy Initialization

| Type     | When is the bean created?       | Default in ApplicationContext? | How to enable |
|----------|---------------------------------|--------------------------------|---------------|
| **Eager**    | At application startup          | Yes (for singleton beans)      | Default behavior |
| **Lazy**     | Only when first requested       | No                             | Use `@Lazy` annotation |

**Example of Lazy**
```java
@Service
@Lazy
public class HeavyService {
    // This bean will be created only when something first uses it
}
```

---

### Quick Summary

- **BeanFactory** → simple + lazy  
- **ApplicationContext** → powerful + eager (default)  
- Most developers only deal with **ApplicationContext**  
- Use `@Lazy` when you want a bean to be created later instead of at startup
