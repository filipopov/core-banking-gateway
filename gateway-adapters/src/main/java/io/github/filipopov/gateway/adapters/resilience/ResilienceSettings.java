package io.github.filipopov.gateway.adapters.resilience;

import java.time.Duration;

/**
 * Resilience settings applied to every tenant's provider, bound from {@code gateway.resilience.*}. Each tenant gets
 * its own circuit breaker and retry instance, so one failing bank does not affect the others.
 *
 * @param retryMaxAttempts       total attempts for read operations, including the first (default 3)
 * @param retryWait              pause between attempts (default 200ms)
 * @param failureRateThreshold   percentage of failed calls that opens the circuit (default 50)
 * @param slidingWindowSize      number of calls the failure rate is computed over (default 20)
 * @param minimumNumberOfCalls   calls required before the failure rate is evaluated (default 10)
 * @param waitInOpenState        how long the circuit stays open before probing again (default 30s)
 */
public record ResilienceSettings(Integer retryMaxAttempts, Duration retryWait, Float failureRateThreshold,
                                 Integer slidingWindowSize, Integer minimumNumberOfCalls, Duration waitInOpenState) {

    public ResilienceSettings {
        retryMaxAttempts = retryMaxAttempts == null ? 3 : retryMaxAttempts;
        retryWait = retryWait == null ? Duration.ofMillis(200) : retryWait;
        failureRateThreshold = failureRateThreshold == null ? 50f : failureRateThreshold;
        slidingWindowSize = slidingWindowSize == null ? 20 : slidingWindowSize;
        minimumNumberOfCalls = minimumNumberOfCalls == null ? 10 : minimumNumberOfCalls;
        waitInOpenState = waitInOpenState == null ? Duration.ofSeconds(30) : waitInOpenState;
    }

    public static ResilienceSettings defaults() {
        return new ResilienceSettings(null, null, null, null, null, null);
    }
}
