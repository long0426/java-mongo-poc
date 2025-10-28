package com.poc.svc.assets.service.impl.support;

import com.mongodb.MongoException;
import com.poc.svc.assets.config.MongoWriteRetryProperties;
import com.poc.svc.assets.exception.MongoRawWriteException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class MongoWriteRetrier {

    private final MongoWriteRetryProperties properties;

    public MongoWriteRetrier(MongoWriteRetryProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public <T> T execute(String failureMessage, Supplier<T> action) {
        Objects.requireNonNull(failureMessage, "failureMessage must not be null");
        Objects.requireNonNull(action, "action must not be null");

        int maxAttempts = properties.getMaxAttempts();
        RuntimeException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.get();
            } catch (RuntimeException ex) {
                if (!isRetryable(ex)) {
                    throw ex;
                }
                lastException = ex;
                if (attempt == maxAttempts) {
                    throw new MongoRawWriteException(failureMessage + " after %d attempts".formatted(attempt), lastException);
                }
                sleep(properties.getBackoff());
            }
        }

        throw new MongoRawWriteException(failureMessage + " after %d attempts".formatted(maxAttempts), lastException);
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable root = unwrap(throwable);
        return root instanceof DataAccessException || root instanceof MongoException;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private void sleep(Duration backoff) {
        if (backoff.isZero() || backoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new MongoRawWriteException("Mongo write retry interrupted", interruptedException);
        }
    }
}
