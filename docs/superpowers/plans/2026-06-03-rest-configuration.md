# RestConfiguration Bean Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the manual `RestConfiguration` bean wiring and let Spring discover credit-account use cases through `@Service` annotations.

**Architecture:** The `core.usecase` classes become Spring-managed service beans. `CreditAccountController` keeps constructor injection unchanged, while `CreditAccountUseCaseSupport` receives existing port adapter beans and Jackson `ObjectMapper` through Spring constructor injection.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Gradle Kotlin DSL, JUnit 5, AssertJ, Testcontainers.

---

## File Structure

- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountEventSourcingApplicationTests.java`
  - Responsibility: verify the Spring application context provides required beans without registering `RestConfiguration`.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`
  - Responsibility: shared service used by all credit-account use cases.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`
  - Responsibility: service for opening credit accounts.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java`
  - Responsibility: service for assigning the first credit limit.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java`
  - Responsibility: service for changing an existing credit limit.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`
  - Responsibility: service for authorizing a purchase.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java`
  - Responsibility: service for capturing an authorization.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java`
  - Responsibility: service for releasing an authorization.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java`
  - Responsibility: service for receiving a payment.
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java`
  - Responsibility: service for reading current account state.
- Delete: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java`
  - Responsibility removed: manual bean factory for use cases.

---

### Task 1: Add Spring wiring regression test

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountEventSourcingApplicationTests.java`

- [ ] **Step 1: Replace the test class with explicit bean wiring assertions**

Write this complete file:

```java
package com.sanmoo.eventsourcing.creditaccount;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.AssignCreditLimitUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.AuthorizePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.CapturePurchaseUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ChangeCreditLimitUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.CreditAccountUseCaseSupport;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.GetCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.OpenCreditAccountUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReceivePaymentUseCase;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.ReleasePurchaseAuthorizationUseCase;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CreditAccountEventSourcingApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void useCasesAreDiscoveredWithoutRestConfiguration() {
        assertThat(applicationContext.getBean(CreditAccountUseCaseSupport.class)).isNotNull();
        assertThat(applicationContext.getBean(OpenCreditAccountUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(AssignCreditLimitUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(ChangeCreditLimitUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(AuthorizePurchaseUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(CapturePurchaseUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(ReleasePurchaseAuthorizationUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(ReceivePaymentUseCase.class)).isNotNull();
        assertThat(applicationContext.getBean(GetCreditAccountUseCase.class)).isNotNull();
        assertThat(applicationContext.containsBean("restConfiguration")).isFalse();
    }
}
```

- [ ] **Step 2: Run the targeted context test and verify the desired failure**

Run:

```bash
./gradlew test --tests '*CreditAccountEventSourcingApplicationTests.useCasesAreDiscoveredWithoutRestConfiguration'
```

Expected before implementation: `useCasesAreDiscoveredWithoutRestConfiguration()` fails because `applicationContext.containsBean("restConfiguration")` is `true` while `RestConfiguration` still exists.

- [ ] **Step 3: Commit the failing test**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountEventSourcingApplicationTests.java
git commit -m "test: cover use case component discovery"
```

---

### Task 2: Convert core use cases to Spring services

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java`

- [ ] **Step 1: Annotate `CreditAccountUseCaseSupport` as a service**

In `CreditAccountUseCaseSupport.java`, add the import after the `ObjectMapper` import:

```java
import org.springframework.stereotype.Service;
```

Then change the class declaration from:

```java
public class CreditAccountUseCaseSupport {
```

to:

```java
@Service
public class CreditAccountUseCaseSupport {
```

- [ ] **Step 2: Annotate each use case with `@Service`**

For each of these files, add `import org.springframework.stereotype.Service;` after the package-specific imports and add `@Service` immediately above the `public class ...` declaration:

```text
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java
src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java
```

For example, `GetCreditAccountUseCase.java` should become:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import org.springframework.stereotype.Service;

@Service
public class GetCreditAccountUseCase {

    private final CreditAccountUseCaseSupport support;

    public GetCreditAccountUseCase(CreditAccountUseCaseSupport support) {
        this.support = support;
    }

    public GetCreditAccountOutput execute(GetCreditAccountInput input) {
        CreditAccountOutput output = support.loadAccountOutput(input.creditAccountId());
        return new GetCreditAccountOutput(output);
    }
}
```

For use cases that already import `java.time.Instant`, the top of the file should follow this shape:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AssignCreditLimitUseCase {
```

For `OpenCreditAccountUseCase.java`, the top of the file should follow this shape:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class OpenCreditAccountUseCase {
```

- [ ] **Step 3: Run compilation before deleting the configuration**

Run:

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL`. At this point duplicate bean definitions may not appear during compilation because compilation does not start the Spring context.

---

### Task 3: Delete manual configuration and verify wiring

**Files:**
- Delete: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java`

- [ ] **Step 1: Delete `RestConfiguration.java`**

Run:

```bash
rm src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java
```

- [ ] **Step 2: Run Java compilation**

Run:

```bash
./gradlew compileJava
```

Expected: `BUILD SUCCESSFUL` with no references to `RestConfiguration`.

- [ ] **Step 3: Run the targeted Spring context test**

Run:

```bash
./gradlew test --tests '*CreditAccountEventSourcingApplicationTests'
```

Expected: `BUILD SUCCESSFUL`. The `useCasesAreDiscoveredWithoutRestConfiguration()` test passes because all use case beans exist and `restConfiguration` is no longer registered.

- [ ] **Step 4: Run the REST integration wiring test when Docker is available**

Run:

```bash
./gradlew test --tests '*CreditAccountControllerIT'
```

Expected with Docker available: `BUILD SUCCESSFUL`.

Expected without Docker/Testcontainers available: Gradle fails during container startup. If that happens, capture the failure text in the final report and do not treat it as a code failure if `compileJava` and `CreditAccountEventSourcingApplicationTests` already passed.

- [ ] **Step 5: Commit implementation**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java src/test/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountEventSourcingApplicationTests.java
git commit -m "refactor: discover use cases as Spring services"
```

---

### Task 4: Final verification

**Files:**
- No source changes expected.

- [ ] **Step 1: Check workspace status**

Run:

```bash
git status --short
```

Expected: no source files modified by this plan remain unstaged or uncommitted. The pre-existing untracked `.factorypath` may still appear and should not be added unless the user explicitly asks.

- [ ] **Step 2: Run full non-selective test suite if Docker is available**

Run:

```bash
./gradlew test
```

Expected with Docker available: `BUILD SUCCESSFUL`.

Expected without Docker/Testcontainers available: container-dependent tests may fail during environment setup. Record the exact failure and report the targeted successful commands from Task 3.

- [ ] **Step 3: Report completion evidence**

Include these items in the final response:

```text
Changed files:
- src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java
- src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/*UseCase.java
- src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/RestConfiguration.java deleted
- src/test/java/com/sanmoo/eventsourcing/creditaccount/CreditAccountEventSourcingApplicationTests.java

Verification run:
- ./gradlew compileJava
- ./gradlew test --tests '*CreditAccountEventSourcingApplicationTests'
- ./gradlew test --tests '*CreditAccountControllerIT' or Docker limitation recorded
- ./gradlew test or Docker limitation recorded
```

---

## Self-Review Notes

- Spec coverage: the plan removes `RestConfiguration`, annotates all listed use case classes and `CreditAccountUseCaseSupport`, and verifies Spring wiring.
- Placeholder scan: all implementation steps are explicit and complete.
- Type consistency: all class names match the current `core.usecase` files and the Spring bean name check uses the default bean name `restConfiguration` for the deleted configuration class.
