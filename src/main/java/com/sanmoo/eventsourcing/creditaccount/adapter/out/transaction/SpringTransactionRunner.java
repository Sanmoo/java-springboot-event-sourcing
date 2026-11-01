package com.sanmoo.eventsourcing.creditaccount.adapter.out.transaction;

import com.sanmoo.eventsourcing.creditaccount.core.port.TransactionRunner;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
public class SpringTransactionRunner implements TransactionRunner {

  private final TransactionTemplate transactionTemplate;

  @Override
  public <T> T runInTransaction(Supplier<T> action) {
    return transactionTemplate.execute(status -> action.get());
  }
}
