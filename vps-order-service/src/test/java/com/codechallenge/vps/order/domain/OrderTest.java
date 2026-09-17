package com.codechallenge.vps.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrderTest {

	@Test
	void createComputesTotalAndReservesIt() {
		Order order = sample("10.00", 2, "3.50", 1);

		assertEquals(OrderStatus.PENDENTE, order.getStatus());
		assertEquals(new BigDecimal("23.50"), order.getTotal());
		assertEquals(new BigDecimal("23.50"), order.getReservedAmount());
		assertEquals(2, order.getItems().size());
	}

	@Test
	void rejectsEmptyItems() {
		assertThrows(IllegalArgumentException.class,
				() -> Order.create(UUID.randomUUID(), "key-1", List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> Order.create(UUID.randomUUID(), "key-1", null));
	}

	@Test
	void rejectsMissingPartnerOrKey() {
		List<OrderItem> items = List.of(new OrderItem("SKU-1", "Widget", 1, new BigDecimal("1.00")));
		assertThrows(IllegalArgumentException.class, () -> Order.create(null, "key-1", items));
		assertThrows(IllegalArgumentException.class, () -> Order.create(UUID.randomUUID(), "  ", items));
	}

	@Test
	void followsHappyPathUntilDelivered() {
		Order order = sample("10.00", 1, "5.00", 1);

		order.transitionTo(OrderStatus.APROVADO);
		order.transitionTo(OrderStatus.EM_PROCESSAMENTO);
		order.transitionTo(OrderStatus.ENVIADO);
		order.transitionTo(OrderStatus.ENTREGUE);

		assertEquals(OrderStatus.ENTREGUE, order.getStatus());
		assertEquals(new BigDecimal("15.00"), order.getReservedAmount());
	}

	@Test
	void cancelFromPendenteClearsReservation() {
		Order order = sample("20.00", 1, "5.00", 2);

		BigDecimal released = order.cancel();

		assertEquals(new BigDecimal("30.00"), released);
		assertEquals(new BigDecimal("0.00"), order.getReservedAmount());
		assertEquals(OrderStatus.CANCELADO, order.getStatus());
	}

	@Test
	void transitionToCanceladoUsesCancel() {
		Order order = sample("10.00", 1, "5.00", 1);
		order.transitionTo(OrderStatus.APROVADO);
		order.transitionTo(OrderStatus.CANCELADO);
		assertEquals(OrderStatus.CANCELADO, order.getStatus());
		assertEquals(new BigDecimal("0.00"), order.getReservedAmount());
	}

	@Test
	void cannotSkipFromPendenteToEnviado() {
		Order order = sample("1.00", 1, "1.00", 1);

		assertThrows(IllegalOrderTransitionException.class,
				() -> order.transitionTo(OrderStatus.ENVIADO));
	}

	@Test
	void cannotCancelAfterProcessingStarted() {
		Order order = sample("1.00", 1, "1.00", 1);
		order.transitionTo(OrderStatus.APROVADO);
		order.transitionTo(OrderStatus.EM_PROCESSAMENTO);

		assertThrows(IllegalOrderTransitionException.class, order::cancel);
	}

	@Test
	void itemRejectsNonPositiveQuantity() {
		assertThrows(IllegalArgumentException.class,
				() -> new OrderItem("SKU-1", "Bolt", 0, new BigDecimal("1.00")));
	}

	@Test
	void itemRejectsBlankSkuAndNegativePrice() {
		assertThrows(IllegalArgumentException.class,
				() -> new OrderItem("  ", "Bolt", 1, new BigDecimal("1.00")));
		assertThrows(IllegalArgumentException.class,
				() -> new OrderItem("SKU-1", "Bolt", 1, new BigDecimal("-0.01")));
	}

	@Test
	void lineTotalScalesToTwoPlaces() {
		OrderItem item = new OrderItem("SKU-1", "Bolt", 3, new BigDecimal("1.255"));
		assertEquals(new BigDecimal("3.78"), item.lineTotal());
	}

	@Test
	void hasSameLinesRejectsNull() {
		Order order = sample("10.00", 2, "3.50", 1);
		assertFalse(order.hasSameLines(null));
	}

	@Test
	void unknownIdExceptionKeepsMessage() {
		UUID id = UUID.randomUUID();
		OrderNotFoundException ex = new OrderNotFoundException(id);
		assertEquals("Order not found: " + id, ex.getMessage());
	}

	@Test
	void sameLinesIgnoreOrder() {
		Order order = sample("10.00", 2, "3.50", 1);
		List<OrderItem> reversed = List.of(
				new OrderItem("SKU-B", "Item B", 1, new BigDecimal("3.50")),
				new OrderItem("SKU-A", "Item A", 2, new BigDecimal("10.00")));
		assertTrue(order.hasSameLines(reversed));
		assertFalse(order.hasSameLines(List.of(new OrderItem("SKU-A", "Item A", 2, new BigDecimal("10.00")))));
	}

	private static Order sample(String price1, int qty1, String price2, int qty2) {
		return Order.create(
				UUID.randomUUID(),
				"idem-" + UUID.randomUUID(),
				List.of(
						new OrderItem("SKU-A", "Item A", qty1, new BigDecimal(price1)),
						new OrderItem("SKU-B", "Item B", qty2, new BigDecimal(price2))));
	}
}
