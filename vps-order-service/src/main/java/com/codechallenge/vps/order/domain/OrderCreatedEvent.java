package com.codechallenge.vps.order.domain;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, UUID partnerId, Instant occurredAt) {
}
