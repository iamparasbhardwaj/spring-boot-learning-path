# Managing Secrets in Spring Boot

## The Golden Rule: Never Commit Secrets
The most critical rule of configuration management is that **secrets (database passwords, API keys, JWT secrets, etc.) must never be hardcoded into your `application.yml` or `application.properties` files** if those files are committed to version control (like Git).

Automated bots constantly scrape public (and sometimes accidentally exposed private) repositories for committed credentials, which can lead to massive security breaches within minutes.

---

## 1. The Standard Approach: Environment Variables

The most common, cloud-native way to handle secrets is through OS-level environment variables.

### How it Works
You leave placeholders in your `application.yml` file. When Spring Boot starts up, it looks for an environment variable that matches the placeholder and injects it.

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:postgresql://db.production.com:5432/myapp
    username: admin_user
    # Use the ${...} syntax to reference an environment variable.
    # The part after the colon is an optional default value (useful for local dev).
    password: ${DB_PASSWORD:default_local_password}

jwt:
  # No default value here. If the env var is missing, the app crashes on startup.
  secret: ${JWT_SECRET}
```

### Passing Environment Variables
How you pass these variables depends on your deployment environment:
*   **Docker:** `docker run -e DB_PASSWORD=supersecret myapp`
*   **Linux/Mac CLI:** `DB_PASSWORD=supersecret java -jar app.jar`
*   **Kubernetes:** Mapped via Kubernetes `Secret` resources into the pod's environment.

---

## 2. The Enterprise Approach: HashiCorp Vault

For large organizations, manually managing environment variables across hundreds of services becomes a security risk. **HashiCorp Vault** is a centralized secrets management tool that securely stores and controls access to tokens, passwords, and API keys.

Spring Boot integrates beautifully with Vault via the **Spring Cloud Vault** project.

### Step 1: Add the Dependency
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

### Step 2: Configure the Connection (Spring Boot 2.4+)
Instead of putting your database password in your application file, you tell Spring Boot to import its configuration directly from your Vault server during startup.

```yaml
# application.yml
spring:
  cloud:
    vault:
      host: vault.mycompany.com
      port: 8200
      scheme: https
      # The token used to authenticate WITH Vault (usually injected via CI/CD or K8s)
      token: ${VAULT_TOKEN} 
  config:
    # Tell Spring Boot to pull configuration from Vault's 'secret/myapp' path
    import: vault://secret/myapp
```

When the app boots, Spring connects to Vault, downloads the secrets stored at `secret/myapp`, and silently adds them to the Spring `Environment` just like normal properties.

---

## 3. Local Development Best Practices

If you aren't using defaults in your `application.yml`, how do you run the app locally without Vault or hardcoding secrets?

### Option A: IDE Run Configurations
Both IntelliJ IDEA and Eclipse allow you to specify environment variables directly in your Run/Debug configuration. This keeps the secrets entirely out of the project files.

### Option B: `.env` Files (Spring Boot 2.4+)
You can use a local `.env` file (which you **must** add to your `.gitignore`) and tell Spring Boot to import it.

**1. Create `.env` in the project root:**
```text
DB_PASSWORD=my_local_secret
JWT_SECRET=local_jwt_key
```

**2. Import it in `application.yml`:**
```yaml
spring:
  config:
    # The 'optional:' prefix means the app won't crash in production if .env is missing
    import: optional:file:.env[.properties]
```