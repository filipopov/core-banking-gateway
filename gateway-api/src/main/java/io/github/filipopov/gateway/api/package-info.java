/**
 * Inbound REST adapter. {@code v1} and {@code v2} are independent packages with their own DTOs and controllers that
 * call the same use cases; {@code common} holds what both share (tenant resolution, problem responses, OpenAPI).
 */
package io.github.filipopov.gateway.api;
