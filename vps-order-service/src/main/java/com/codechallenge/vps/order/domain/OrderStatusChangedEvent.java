package com.codechallenge.vps.order.domain;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusChangedEvent(
		UUID orderId,
		UUID partnerId,
		OrderStatus oldStatus,
		OrderStatus newStatus,
		Instant occurredAt) {
}
