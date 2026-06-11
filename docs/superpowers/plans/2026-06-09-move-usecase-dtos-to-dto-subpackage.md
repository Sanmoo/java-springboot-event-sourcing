# Move Input/Output DTOs to `dto` Subpackage — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor-move all usecase classes whose names end with `Input` or `Output` from `com.sanmoo.eventsourcing.creditaccount.core.usecase` to a new subpackage `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`, updating all references.

**Architecture:** Pure structural refactor — 18 record/DTO classes change package declaration and physical location. 18 consumer files (use cases, tests, controller) gain explicit `import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.X` statements. No behavioral changes. No new tests required — existing tests validate correctness.

**Tech Stack:** Java 21+ records, Maven, JUnit 5 + AssertJ + Mockito

---

### Task 1: Create `dto` subpackage directory and move all Input/Output files with updated package declarations

**Files:**
- Create: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/` (directory)
- Move and modify: 18 files (8 Input + 10 Output) — see list below
- Modify: None (import updates deferred to subsequent tasks)

**Step 1: Create the target directory**

```bash
mkdir -p src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto
```

**Step 2: Move and rewrite all 18 files with updated package declaration**

For each file below, move it from `usecase/` to `usecase/dto/` and change its package line.

**Input files (8):**

- [ ] **Step 2a: `AssignCreditLimitInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/AssignCreditLimitInput.java
```

Edit `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/AssignCreditLimitInput.java`:

Old:
```
package com.sanmoo.eventsourcing.creditaccount.core.usecase;
```
New:
```
package com.sanmoo.eventsourcing.creditaccount.core.usecase.dto;
```

- [ ] **Step 2b: `AuthorizePurchaseInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/AuthorizePurchaseInput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2c: `CapturePurchaseInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/CapturePurchaseInput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2d: `ChangeCreditLimitInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ChangeCreditLimitInput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2e: `GetCreditAccountInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/GetCreditAccountInput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2f: `OpenCreditAccountInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/OpenCreditAccountInput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2g: `ReceivePaymentInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ReceivePaymentInput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2h: `ReleasePurchaseAuthorizationInput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationInput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ReleasePurchaseAuthorizationInput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

**Output files (10):**

- [ ] **Step 2i: `AssignCreditLimitOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/AssignCreditLimitOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2j: `AuthorizePurchaseOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/AuthorizePurchaseOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2k: `CapturePurchaseOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/CapturePurchaseOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2l: `ChangeCreditLimitOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ChangeCreditLimitOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2m: `CreditAccountOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/CreditAccountOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2n: `GetCreditAccountOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/GetCreditAccountOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2o: `OpenCreditAccountOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/OpenCreditAccountOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2p: `PurchaseAuthorizationOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/PurchaseAuthorizationOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/PurchaseAuthorizationOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2q: `ReceivePaymentOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ReceivePaymentOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

- [ ] **Step 2r: `ReleasePurchaseAuthorizationOutput.java`**

```bash
mv src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationOutput.java \
   src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/ReleasePurchaseAuthorizationOutput.java
```

Edit package from `com.sanmoo.eventsourcing.creditaccount.core.usecase` → `com.sanmoo.eventsourcing.creditaccount.core.usecase.dto`.

**Step 3: Verify all 18 files exist in the dto subdirectory**

```bash
ls src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/
```
Expected: 18 `.java` files listed, and zero Input/Output files in the parent `usecase/` directory.

**Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/dto/
git add -u src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/
git commit -m "refactor: move Input/Output DTOs to usecase.dto subpackage"
```

---

### Task 2: Add imports to usecase files — batch 1 (AssignCreditLimit, AuthorizePurchase, CapturePurchase)

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java`

- [ ] **Step 1: `AssignCreditLimitUseCase.java` — add imports for Input and Output**

Add two import lines after the existing `package` line and any existing imports:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
```

File should compile; no other changes.

- [ ] **Step 2: `AuthorizePurchaseUseCase.java` — add imports for Input and Output**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
```

- [ ] **Step 3: `CapturePurchaseUseCase.java` — add imports for Input and Output**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCase.java
git commit -m "refactor: add dto imports to AssignCreditLimit, AuthorizePurchase, CapturePurchase usecases"
```

---

### Task 3: Add imports to usecase files — batch 2 (ChangeCreditLimit, GetCreditAccount, OpenCreditAccount)

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java`

- [ ] **Step 1: `ChangeCreditLimitUseCase.java` — add imports**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ChangeCreditLimitInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ChangeCreditLimitOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
```

- [ ] **Step 2: `GetCreditAccountUseCase.java` — add imports**

This file also uses `CreditAccountOutput` (from `CreditAccountUseCaseSupport.loadAccountOutput()`), so it needs three imports:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
```

- [ ] **Step 3: `OpenCreditAccountUseCase.java` — add imports**

This file imports `CreditAccountId` and `UniqueIdGenerator` from other packages; add dto imports:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.port.UniqueIdGenerator;
import com.sanmoo.eventsourcing.creditaccount.domain.model.CreditAccountId;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCase.java
git commit -m "refactor: add dto imports to ChangeCreditLimit, GetCreditAccount, OpenCreditAccount usecases"
```

---

### Task 4: Add imports to usecase files — batch 3 (ReceivePayment, ReleasePurchaseAuthorization, CreditAccountUseCaseSupport)

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java`
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java`

- [ ] **Step 1: `ReceivePaymentUseCase.java` — add imports**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReceivePaymentInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReceivePaymentOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
```

- [ ] **Step 2: `ReleasePurchaseAuthorizationUseCase.java` — add imports**

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReleasePurchaseAuthorizationInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReleasePurchaseAuthorizationOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
```

- [ ] **Step 3: `CreditAccountUseCaseSupport.java` — add imports**

This file uses `CreditAccountOutput` and `PurchaseAuthorizationOutput` internally. Add these two imports after the existing `package` line. The existing file starts with:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.sanmoo.eventsourcing.creditaccount.core.error.IdempotencyConflictException;
...
```

Add the two dto imports right after the package line, before `lombok`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
...
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCase.java \
        src/main/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CreditAccountUseCaseSupport.java
git commit -m "refactor: add dto imports to ReceivePayment, ReleasePurchaseAuthorization usecases and support"
```

---

### Task 5: Update controller import

**Files:**
- Modify: `src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java`

- [ ] **Step 1: Add dto wildcard import**

The controller currently has `import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;` which does NOT cover the `dto` subpackage. Add a new import line for `usecase.dto.*` right after the existing `usecase.*` import.

Old:
```java
import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;
```
New:
```java
import com.sanmoo.eventsourcing.creditaccount.core.usecase.*;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.*;
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/sanmoo/eventsourcing/creditaccount/adapter/in/rest/CreditAccountController.java
git commit -m "refactor: add dto wildcard import to CreditAccountController"
```

---

### Task 6: Add imports to test files — batch 1 (AssignCreditLimit, AuthorizePurchase, CapturePurchase, ChangeCreditLimit)

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCaseTest.java`

- [ ] **Step 1: `AssignCreditLimitUseCaseTest.java` — add dto imports**

This test uses `AssignCreditLimitInput` and `AssignCreditLimitOutput`. Add after the package line, before existing imports:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AssignCreditLimitOutput;
import com.sanmoo.eventsourcing.creditaccount.core.port.AppendResult;
...
```

- [ ] **Step 2: `AuthorizePurchaseUseCaseTest.java` — add dto imports**

This test uses `AuthorizePurchaseInput` and `PurchaseAuthorizationOutput`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.AuthorizePurchaseInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;
import ...
```

- [ ] **Step 3: `CapturePurchaseUseCaseTest.java` — add dto import**

This test uses `CapturePurchaseInput`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CapturePurchaseInput;
import ...
```

- [ ] **Step 4: `ChangeCreditLimitUseCaseTest.java` — add dto import**

This test uses `ChangeCreditLimitInput`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ChangeCreditLimitInput;
import ...
```

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AssignCreditLimitUseCaseTest.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/AuthorizePurchaseUseCaseTest.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/CapturePurchaseUseCaseTest.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ChangeCreditLimitUseCaseTest.java
git commit -m "refactor: add dto imports to tests batch 1"
```

---

### Task 7: Add imports to test files — batch 2 (GetCreditAccount, OpenCreditAccount, ReceivePayment, ReleasePurchaseAuthorization)

**Files:**
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCaseTest.java`
- Modify: `src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCaseTest.java`

- [ ] **Step 1: `GetCreditAccountUseCaseTest.java` — add dto imports**

This test uses `GetCreditAccountInput`, `CreditAccountOutput`, and `PurchaseAuthorizationOutput`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.CreditAccountOutput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.GetCreditAccountInput;
import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.PurchaseAuthorizationOutput;
import ...
```

- [ ] **Step 2: `OpenCreditAccountUseCaseTest.java` — add dto import**

This test uses `OpenCreditAccountInput`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.OpenCreditAccountInput;
import ...
```

- [ ] **Step 3: `ReceivePaymentUseCaseTest.java` — add dto import**

This test uses `ReceivePaymentInput`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReceivePaymentInput;
import ...
```

- [ ] **Step 4: `ReleasePurchaseAuthorizationUseCaseTest.java` — add dto import**

This test uses `ReleasePurchaseAuthorizationInput`:

```java
package com.sanmoo.eventsourcing.creditaccount.core.usecase;

import com.sanmoo.eventsourcing.creditaccount.core.usecase.dto.ReleasePurchaseAuthorizationInput;
import ...
```

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/GetCreditAccountUseCaseTest.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/OpenCreditAccountUseCaseTest.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReceivePaymentUseCaseTest.java \
        src/test/java/com/sanmoo/eventsourcing/creditaccount/core/usecase/ReleasePurchaseAuthorizationUseCaseTest.java
git commit -m "refactor: add dto imports to tests batch 2"
```

---

### Task 8: Build and run all tests to verify refactor

- [ ] **Step 1: Compile the project**

```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS, no compilation errors.

- [ ] **Step 2: Run all tests**

```bash
./mvnw test
```
Expected: All existing tests pass with no failures or errors.

- [ ] **Step 3: Commit (if needed) or verify clean status**

```bash
git status
```
Expected: clean working tree.

---
