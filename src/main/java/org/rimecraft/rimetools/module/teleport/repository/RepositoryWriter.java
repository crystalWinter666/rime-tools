package org.rimecraft.rimetools.module.teleport.repository;

import org.slf4j.Logger;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class RepositoryWriter implements Executor, AutoCloseable {
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final ExecutorService executor;
    private final Logger logger;

    public RepositoryWriter(Logger logger) {
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "rime-tools-storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    public void flush() {
        try {
            executor.submit(() -> {
            }).get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Timed out while flushing RIME Tools storage", exception);
        }
    }

    @Override
    public void close() {
        try {
            flush();
        } catch (Exception exception) {
            logger.error("Failed to flush RIME Tools storage", exception);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.error("RIME Tools storage writer did not stop within {} seconds", SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while stopping RIME Tools storage writer", exception);
        }
    }
}
