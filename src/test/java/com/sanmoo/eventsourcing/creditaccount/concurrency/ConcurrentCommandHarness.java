package com.sanmoo.eventsourcing.creditaccount.concurrency;

import com.sanmoo.eventsourcing.creditaccount.core.error.ConcurrencyConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConcurrentCommandHarness implements AutoCloseable {

    private final ExecutorService executor;

    public ConcurrentCommandHarness(int threadCount) {
        this.executor = Executors.newFixedThreadPool(threadCount);
    }

    public <T> ConcurrentCommandResult<T> runConcurrently(List<Callable<T>> commands, int maxRetries)
            throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();

        for (Callable<T> command : commands) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return command.call();
            }));
        }

        startLatch.countDown();

        List<T> successfulResults = new ArrayList<>();
        int conflictCount = 0;
        int failureCount = 0;
        int retryCount = 0;
        List<Callable<T>> toRetry = new ArrayList<>();

        for (int i = 0; i < futures.size(); i++) {
            Future<T> future = futures.get(i);
            Callable<T> command = commands.get(i);
            try {
                successfulResults.add(future.get());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof ConcurrencyConflictException) {
                    conflictCount++;
                    toRetry.add(command);
                } else {
                    failureCount++;
                }
            }
        }

        for (Callable<T> command : toRetry) {
            T result = retry(command, maxRetries);
            if (result != null) {
                successfulResults.add(result);
                retryCount++;
            } else {
                failureCount++;
            }
        }

        return new ConcurrentCommandResult<>(successfulResults, conflictCount, failureCount, retryCount);
    }

    private <T> T retry(Callable<T> command, int maxRetries) throws InterruptedException {
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return command.call();
            } catch (ConcurrencyConflictException e) {
                Thread.sleep(10L);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
