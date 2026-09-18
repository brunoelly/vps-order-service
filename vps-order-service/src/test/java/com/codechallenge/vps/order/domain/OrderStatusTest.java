package com.codechallenge.vps.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusTest {

	@ParameterizedTest
	@CsvSource({
			"PENDING, APPROVED, true",
			"PENDING, CANCELLED, true",
			"PENDING, PROCESSING, false",
			"PENDING, SHIPPED, false",
			"PENDING, DELIVERED, false",
			"APPROVED, PROCESSING, true",
			"APPROVED, CANCELLED, true",
			"APPROVED, PENDING, false",
			"APPROVED, DELIVERED, false",
			"APPROVED, SHIPPED, false",
			"PROCESSING, SHIPPED, true",
			"PROCESSING, CANCELLED, false",
			"PROCESSING, APPROVED, false",
			"SHIPPED, DELIVERED, true",
			"SHIPPED, CANCELLED, false",
			"DELIVERED, CANCELLED, false",
			"DELIVERED, SHIPPED, false",
			"CANCELLED, PENDING, false",
			"CANCELLED, DELIVERED, false"
	})
	void transitionRules(OrderStatus from, OrderStatus to, boolean allowed) {
		assertEquals(allowed, from.canTransitionTo(to));
	}

	@Test
	void sameStatusIsNotAllowed() {
		assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.PENDING));
	}

	@Test
	void nullTargetIsNotAllowed() {
		assertFalse(OrderStatus.PENDING.canTransitionTo(null));
	}

	@Test
	void terminalsHaveNoOutgoingTransitions() {
		assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PENDING));
		assertFalse(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.APPROVED));
	}
}
