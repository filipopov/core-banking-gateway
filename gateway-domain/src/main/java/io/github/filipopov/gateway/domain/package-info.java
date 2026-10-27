/**
 * The domain core of the gateway: a provider-neutral model of accounts, balances, transactions and transfers.
 *
 * <p>This module is plain Java on purpose. It has no Spring, Jackson or HTTP dependencies, which is enforced by
 * ArchUnit tests in {@code gateway-app}.
 */
package io.github.filipopov.gateway.domain;
