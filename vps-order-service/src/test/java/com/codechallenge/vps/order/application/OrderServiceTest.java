package com.codechallenge.vps.order.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.codechallenge.vps.order.domain.IdempotencyConflictException;
import com.codechallenge.vps.order.domain.IllegalOrderTransitionException;
import com.codechallenge.vps.order.domain.Order;
import com.codechallenge.vps.order.domain.OrderCreatedEvent;
import com.codechallenge.vps.order.domain.OrderItem;
import com.codechallenge.vps.order.domain.OrderNotFoundException;
import com.codechallenge.vps.order.domain.OrderStatus;
import com.codechallenge.vps.order.domain.OrderStatusChangedEvent;
import com.codechallenge.vps.order.infra.OrderRepository;
import com.codechallenge.vps.outbox.OutboxWriter;
import com.codechallenge.vps.partner.domain.InsufficientCreditException;
import com.codechallenge.vps.partner.domain.Partner;
import com.codechallenge.vps.partner.domain.PartnerNotFoundException;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

	@Mock
	PartnerRepository partners;

	@Mock
	OrderRepository orders;

	@Mock
	OutboxWriter outbox;

	OrderService service;

	@BeforeEach
	void setUp() {
		service = new OrderService(partners, orders, outbox);
		lenient().when(orders.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(partners.save(any(Partner.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void placeReservesCreditAndPersistsPendingOrder() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByPartnerIdAndIdempotencyKey(partner.getId(), "k1")).thenReturn(Optional.empty());

		Order order = service.place(partner.getId(), "k1", lines("10.00", 2));

		assertEquals(OrderStatus.PENDENTE, order.getStatus());
		assertEquals(new BigDecimal("20.00"), order.getTotal());
		assertEquals(new BigDecimal("80.00"), partner.getAvailableCredit());
		verify(orders).save(order);
		verify(outbox).append(eq("Order"), eq(order.getId()), eq("OrderCreated"), any(OrderCreatedEvent.class));
	}

	@Test
	void placeFailsWhenPartnerIsMissing() {
		UUID partnerId = UUID.randomUUID();
		when(partners.findByIdForUpdate(partnerId)).thenReturn(Optional.empty());

		assertThrows(PartnerNotFoundException.class,
				() -> service.place(partnerId, "k1", lines("1.00", 1)));
		verify(orders, never()).save(any());
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void placeRejectsEmptyItems() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByPartnerIdAndIdempotencyKey(partner.getId(), "k1")).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class,
				() -> service.place(partner.getId(), "k1", List.of()));
		verify(orders, never()).save(any());
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void placeFailsWhenCreditIsShort() {
		Partner partner = Partner.create("Acme", new BigDecimal("10.00"));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByPartnerIdAndIdempotencyKey(partner.getId(), "k1")).thenReturn(Optional.empty());

		assertThrows(InsufficientCreditException.class,
				() -> service.place(partner.getId(), "k1", lines("10.01", 1)));
		assertEquals(new BigDecimal("10.00"), partner.getAvailableCredit());
		verify(orders, never()).save(any());
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void sameIdempotencyKeyAndLinesReturnsOriginalOrder() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		Order first = Order.create(partner.getId(), "k1", lines("5.00", 1));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByPartnerIdAndIdempotencyKey(partner.getId(), "k1")).thenReturn(Optional.of(first));

		Order again = service.place(partner.getId(), "k1", lines("5.00", 1));

		assertSame(first, again);
		assertEquals(new BigDecimal("100.00"), partner.getAvailableCredit());
		verify(orders, never()).save(any());
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void sameIdempotencyKeyDifferentLinesConflicts() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		Order first = Order.create(partner.getId(), "k1", lines("5.00", 1));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByPartnerIdAndIdempotencyKey(partner.getId(), "k1")).thenReturn(Optional.of(first));

		assertThrows(IdempotencyConflictException.class,
				() -> service.place(partner.getId(), "k1", lines("9.00", 1)));
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void cancelReleasesHeldCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("20.00"));
		Order order = Order.create(partner.getId(), "k1", lines("10.00", 2));
		when(orders.findById(order.getId())).thenReturn(Optional.of(order));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

		Order cancelled = service.cancel(order.getId());

		assertEquals(OrderStatus.CANCELADO, cancelled.getStatus());
		assertEquals(new BigDecimal("0.00"), cancelled.getReservedAmount());
		assertEquals(new BigDecimal("100.00"), partner.getAvailableCredit());
		verify(outbox).append(eq("Order"), eq(order.getId()), eq("OrderStatusChanged"), any(OrderStatusChangedEvent.class));
	}

	@Test
	void cancelUnknownOrder() {
		UUID id = UUID.randomUUID();
		when(orders.findById(id)).thenReturn(Optional.empty());

		assertThrows(OrderNotFoundException.class, () -> service.cancel(id));
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void cancelAfterProcessingDoesNotTouchCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("20.00"));
		Order order = Order.create(partner.getId(), "k1", lines("10.00", 2));
		order.transitionTo(OrderStatus.APROVADO);
		order.transitionTo(OrderStatus.EM_PROCESSAMENTO);
		when(orders.findById(order.getId())).thenReturn(Optional.of(order));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

		assertThrows(IllegalOrderTransitionException.class, () -> service.cancel(order.getId()));
		assertEquals(new BigDecimal("80.00"), partner.getAvailableCredit());
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void changeStatusWalksTheHappyPathWithoutTouchingCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("20.00"));
		Order order = Order.create(partner.getId(), "k1", lines("10.00", 2));
		when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

		service.changeStatus(order.getId(), OrderStatus.APROVADO);
		service.changeStatus(order.getId(), OrderStatus.EM_PROCESSAMENTO);
		service.changeStatus(order.getId(), OrderStatus.ENVIADO);
		Order delivered = service.changeStatus(order.getId(), OrderStatus.ENTREGUE);

		assertEquals(OrderStatus.ENTREGUE, delivered.getStatus());
		assertEquals(new BigDecimal("20.00"), delivered.getReservedAmount());
		assertEquals(new BigDecimal("80.00"), partner.getAvailableCredit());
		verify(partners, never()).findByIdForUpdate(any());
		verify(outbox, times(4)).append(eq("Order"), eq(order.getId()), eq("OrderStatusChanged"), any());
	}

	@Test
	void changeStatusRejectsIllegalJump() {
		Order order = Order.create(UUID.randomUUID(), "k1", lines("1.00", 1));
		when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

		assertThrows(IllegalOrderTransitionException.class,
				() -> service.changeStatus(order.getId(), OrderStatus.ENVIADO));
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void changeStatusUnknownOrder() {
		UUID id = UUID.randomUUID();
		when(orders.findByIdForUpdate(id)).thenReturn(Optional.empty());

		assertThrows(OrderNotFoundException.class,
				() -> service.changeStatus(id, OrderStatus.APROVADO));
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void changeStatusToCanceladoReleasesCredit() {
		Partner partner = Partner.create("Acme", new BigDecimal("100.00"));
		partner.reserve(new BigDecimal("20.00"));
		Order order = Order.create(partner.getId(), "k1", lines("10.00", 2));
		when(orders.findById(order.getId())).thenReturn(Optional.of(order));
		when(partners.findByIdForUpdate(partner.getId())).thenReturn(Optional.of(partner));
		when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

		Order cancelled = service.changeStatus(order.getId(), OrderStatus.CANCELADO);

		assertEquals(OrderStatus.CANCELADO, cancelled.getStatus());
		assertEquals(new BigDecimal("100.00"), partner.getAvailableCredit());
		verify(outbox).append(eq("Order"), eq(order.getId()), eq("OrderStatusChanged"), any(OrderStatusChangedEvent.class));
	}

	@Test
	void changeStatusRequiresTarget() {
		assertThrows(IllegalArgumentException.class, () -> service.changeStatus(UUID.randomUUID(), null));
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void deliveredIsTerminal() {
		Order order = Order.create(UUID.randomUUID(), "k1", lines("1.00", 1));
		order.transitionTo(OrderStatus.APROVADO);
		order.transitionTo(OrderStatus.EM_PROCESSAMENTO);
		order.transitionTo(OrderStatus.ENVIADO);
		order.transitionTo(OrderStatus.ENTREGUE);
		when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

		assertThrows(IllegalOrderTransitionException.class,
				() -> service.changeStatus(order.getId(), OrderStatus.APROVADO));
		verify(outbox, never()).append(any(), any(), any(), any());
	}

	@Test
	void getReturnsExistingOrder() {
		Order order = Order.create(UUID.randomUUID(), "k1", lines("1.00", 1));
		when(orders.findById(order.getId())).thenReturn(Optional.of(order));

		assertSame(order, service.get(order.getId()));
	}

	@Test
	void getUnknownOrder() {
		UUID id = UUID.randomUUID();
		when(orders.findById(id)).thenReturn(Optional.empty());

		assertThrows(OrderNotFoundException.class, () -> service.get(id));
	}

	@Test
	void searchDelegatesToRepository() {
		Order order = Order.create(UUID.randomUUID(), "k1", lines("1.00", 1));
		Pageable page = PageRequest.of(0, 20);
		when(orders.findAll(any(Specification.class), eq(page))).thenReturn(new PageImpl<>(List.of(order)));

		Page<Order> result = service.search(order.getPartnerId(), OrderStatus.PENDENTE, Instant.EPOCH, Instant.now(), page);

		assertEquals(1, result.getTotalElements());
		assertSame(order, result.getContent().get(0));
	}

	private static List<OrderItem> lines(String price, int qty) {
		return List.of(new OrderItem("SKU-1", "Widget", qty, new BigDecimal(price)));
	}
}
