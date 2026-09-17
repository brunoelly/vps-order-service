package com.codechallenge.vps.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusTest {

	@ParameterizedTest
	@CsvSource({
			"PENDENTE, APROVADO, true",
			"PENDENTE, CANCELADO, true",
			"PENDENTE, EM_PROCESSAMENTO, false",
			"PENDENTE, ENVIADO, false",
			"PENDENTE, ENTREGUE, false",
			"APROVADO, EM_PROCESSAMENTO, true",
			"APROVADO, CANCELADO, true",
			"APROVADO, PENDENTE, false",
			"APROVADO, ENVIADO, false",
			"EM_PROCESSAMENTO, ENVIADO, true",
			"EM_PROCESSAMENTO, CANCELADO, false",
			"EM_PROCESSAMENTO, APROVADO, false",
			"ENVIADO, ENTREGUE, true",
			"ENVIADO, CANCELADO, false",
			"ENTREGUE, CANCELADO, false",
			"ENTREGUE, ENVIADO, false",
			"CANCELADO, PENDENTE, false",
			"CANCELADO, ENTREGUE, false"
	})
	void transitionRules(OrderStatus from, OrderStatus to, boolean allowed) {
		assertEquals(allowed, from.canTransitionTo(to));
	}

	@Test
	void sameStatusIsNotAllowed() {
		assertFalse(OrderStatus.PENDENTE.canTransitionTo(OrderStatus.PENDENTE));
	}

	@Test
	void nullTargetIsNotAllowed() {
		assertFalse(OrderStatus.PENDENTE.canTransitionTo(null));
	}

	@Test
	void terminalsHaveNoOutgoingTransitions() {
		assertFalse(OrderStatus.ENTREGUE.canTransitionTo(OrderStatus.PENDENTE));
		assertFalse(OrderStatus.CANCELADO.canTransitionTo(OrderStatus.APROVADO));
	}
}
