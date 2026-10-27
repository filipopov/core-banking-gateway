package io.github.filipopov.gateway.adapters.resilience;

import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import io.github.filipopov.gateway.application.error.ProviderUnavailableException;
import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.util.List;
import java.util.function.Supplier;

/**
 * Decorates a provider with a circuit breaker and retries.
 *
 * <ul>
 *   <li>Only {@link ProviderUnavailableException} counts as a failure. Business errors such as "account not found"
 *       neither open the circuit nor trigger retries.</li>
 *   <li>Reads are retried; transfers are not, because they are not idempotent upstream.</li>
 *   <li>An open circuit is reported as {@link ProviderUnavailableException}, so callers see a single error type.</li>
 * </ul>
 */
public final class ResilientCoreBankingProvider implements CoreBankingProvider {

    private final String name;
    private final CoreBankingProvider delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientCoreBankingProvider(String name, CoreBankingProvider delegate, CircuitBreaker circuitBreaker, Retry retry) {
        this.name = name;
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    /** Creates a decorator with circuit breaker and retry configured from the given settings. */
    public static ResilientCoreBankingProvider decorate(String name, CoreBankingProvider delegate, ResilienceSettings settings) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .slidingWindowSize(settings.slidingWindowSize())
                .minimumNumberOfCalls(settings.minimumNumberOfCalls())
                .waitDurationInOpenState(settings.waitInOpenState())
                .recordExceptions(ProviderUnavailableException.class)
                .build();
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(settings.retryMaxAttempts())
                .waitDuration(settings.retryWait())
                .retryExceptions(ProviderUnavailableException.class)
                .build();
        return new ResilientCoreBankingProvider(name, delegate,
                CircuitBreaker.of(name, circuitBreakerConfig), Retry.of(name, retryConfig));
    }

    public CircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    @Override
    public List<Account> listAccounts() {
        return read(delegate::listAccounts);
    }

    @Override
    public Account getAccount(AccountId accountId) {
        return read(() -> delegate.getAccount(accountId));
    }

    @Override
    public Balance getBalance(AccountId accountId) {
        return read(() -> delegate.getBalance(accountId));
    }

    @Override
    public Page<Transaction> listTransactions(AccountId accountId, TransactionQuery query) {
        return read(() -> delegate.listTransactions(accountId, query));
    }

    @Override
    public TransferResult transfer(TransferRequest request) {
        return guarded(CircuitBreaker.decorateSupplier(circuitBreaker, () -> delegate.transfer(request)));
    }

    private <T> T read(Supplier<T> call) {
        // Retry wraps the circuit breaker: every attempt is recorded, and an open circuit stops retrying immediately.
        return guarded(Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call)));
    }

    private <T> T guarded(Supplier<T> call) {
        try {
            return call.get();
        } catch (CallNotPermittedException e) {
            throw new ProviderUnavailableException(name, "Circuit breaker for '" + name + "' is open", e);
        }
    }
}
