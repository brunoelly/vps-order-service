package com.codechallenge.vps.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

	PENDING,
	APPROVED,
	PROCESSING,
	SHIPPED,
	DELIVERED,
	CANCELLED;

	public boolean canTransitionTo(OrderStatus target) {
		if (target == null || target == this) {
			return false;
		}
		return allowedTargets().contains(target);
	}

	private Set<OrderStatus> allowedTargets() {
		return switch (this) {
			case PENDING -> EnumSet.of(APPROVED, CANCELLED);
			case APPROVED -> EnumSet.of(PROCESSING, CANCELLED);
			case PROCESSING -> EnumSet.of(SHIPPED);
			case SHIPPED -> EnumSet.of(DELIVERED);
			case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
		};
	}
}
