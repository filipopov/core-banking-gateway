/**
 * Outbound adapters. Everything that talks to the outside world lives here: one sub-package per core banking provider,
 * the shared HTTP and resilience plumbing, and the configuration-backed tenant and feature-flag adapters.
 *
 * <p>Rules enforced by ArchUnit: adapters never depend on the REST API layer, and provider packages never depend on
 * each other.
 */
package io.github.filipopov.gateway.adapters;
