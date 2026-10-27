package io.github.filipopov.gateway.adapters.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.filipopov.gateway.application.port.out.CoreBankingProvider;
import io.github.filipopov.gateway.application.port.out.ProviderUnavailableException;
import io.github.filipopov.gateway.domain.account.Account;
import io.github.filipopov.gateway.domain.account.AccountId;
import io.github.filipopov.gateway.domain.account.Balance;
import io.github.filipopov.gateway.domain.error.AccountNotFoundException;
import io.github.filipopov.gateway.domain.money.Money;
import io.github.filipopov.gateway.domain.transaction.Page;
import io.github.filipopov.gateway.domain.transaction.Transaction;
import io.github.filipopov.gateway.domain.transaction.TransactionQuery;
import io.github.filipopov.gateway.domain.transfer.TransferRequest;
import io.github.filipopov.gateway.domain.transfer.TransferResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ResilientCoreBankingProviderTest {

    private static final AccountId ACCOUNT = AccountId.of("A1");
    private static final ResilienceSettings FAST = new ResilienceSettings(3, Duration.ofMillis(1), 50f, 4, 4, Duration.ofMinutes(1));

    private final ScriptedProvider delegate = new ScriptedProvider();
    private final ResilientCoreBankingProvider resilient = ResilientCoreBankingProvider.decorate("test-bank", delegate, FAST);

    @Test
    void retriesReadsWhenProviderIsUnavailable() {
        delegate.then(this::unavailable).then(this::unavailable).then(this::balance);

        assertThat(resilient.getBalance(ACCOUNT).booked()).isEqualTo(Money.of("1.00", "CHF"));
        assertThat(delegate.calls.get()).isEqualTo(3);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        delegate.then(this::unavailable).then(this::unavailable).then(this::unavailable).then(this::balance);

        assertThatThrownBy(() -> resilient.getBalance(ACCOUNT)).isInstanceOf(ProviderUnavailableException.class);
        assertThat(delegate.calls.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryBusinessErrors() {
        delegate.then(() -> {
            throw new AccountNotFoundException(ACCOUNT);
        });

        assertThatThrownBy(() -> resilient.getBalance(ACCOUNT)).isInstanceOf(AccountNotFoundException.class);
        assertThat(delegate.calls.get()).isEqualTo(1);
    }

    @Test
    void neverRetriesTransfersBecauseTheyAreNotIdempotent() {
        delegate.then(this::unavailable);
        var request = new TransferRequest(ACCOUNT, AccountId.of("A2"), Money.of("1.00", "CHF"), null);

        assertThatThrownBy(() -> resilient.transfer(request)).isInstanceOf(ProviderUnavailableException.class);
        assertThat(delegate.calls.get()).isEqualTo(1);
    }

    @Test
    void opensCircuitAfterRepeatedOutagesAndFailsFast() {
        for (int i = 0; i < 12; i++) {
            delegate.then(this::unavailable);
        }
        assertThatThrownBy(() -> resilient.getBalance(ACCOUNT)).isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> resilient.getBalance(ACCOUNT)).isInstanceOf(ProviderUnavailableException.class);
        int callsWhenOpened = delegate.calls.get();

        assertThat(resilient.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> resilient.getBalance(ACCOUNT))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class)
                .hasMessageContaining("open");
        assertThat(delegate.calls.get()).isEqualTo(callsWhenOpened);
    }

    @Test
    void businessErrorsDoNotOpenTheCircuit() {
        for (int i = 0; i < 10; i++) {
            delegate.then(() -> {
                throw new AccountNotFoundException(ACCOUNT);
            });
            assertThatThrownBy(() -> resilient.getBalance(ACCOUNT)).isInstanceOf(AccountNotFoundException.class);
        }

        assertThat(resilient.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private Balance unavailable() {
        throw new ProviderUnavailableException("test", "down");
    }

    private Balance balance() {
        return new Balance(ACCOUNT, Money.of("1.00", "CHF"), Money.of("1.00", "CHF"));
    }

    /** Returns or throws the scripted outcomes in order. */
    private static final class ScriptedProvider implements CoreBankingProvider {

        final AtomicInteger calls = new AtomicInteger();
        private final Deque<Supplier<Balance>> script = new ArrayDeque<>();

        ScriptedProvider then(Supplier<Balance> outcome) {
            script.add(outcome);
            return this;
        }

        private Balance next() {
            calls.incrementAndGet();
            return script.removeFirst().get();
        }

        @Override
        public Balance getBalance(AccountId accountId) {
            return next();
        }

        @Override
        public TransferResult transfer(TransferRequest request) {
            next();
            throw new AssertionError("unreachable in these tests");
        }

        @Override
        public List<Account> listAccounts() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Account getAccount(AccountId accountId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Transaction> listTransactions(AccountId accountId, TransactionQuery query) {
            throw new UnsupportedOperationException();
        }
    }
}
