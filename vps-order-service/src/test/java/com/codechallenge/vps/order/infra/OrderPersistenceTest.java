package com.codechallenge.vps.order.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.codechallenge.vps.order.domain.Order;
import com.codechallenge.vps.order.domain.OrderItem;
import com.codechallenge.vps.order.domain.OrderStatus;
import com.codechallenge.vps.partner.domain.Partner;
import com.codechallenge.vps.partner.infra.PartnerRepository;

@SpringBootTest
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class OrderPersistenceTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	PartnerRepository partners;

	@Autowired
	OrderRepository orders;

	@Test
	void roundTripAndLockQuery() {
		Partner partner = partners.save(Partner.create("Acme", new BigDecimal("500.00")));
		partner.reserve(new BigDecimal("25.00"));
		partners.save(partner);

		Order order = Order.create(
				partner.getId(),
				"idem-1",
				List.of(new OrderItem("SKU-1", "Gasket", 5, new BigDecimal("5.00"))));
		orders.save(order);

		Order loaded = orders.findByPartnerIdAndIdempotencyKey(partner.getId(), "idem-1").orElseThrow();
		assertEquals(new BigDecimal("25.00"), loaded.getTotal());
		assertEquals(1, loaded.getItems().size());
		assertEquals(OrderStatus.PENDENTE, loaded.getStatus());

		assertTrue(partners.findByIdForUpdate(partner.getId()).isPresent());
		assertTrue(orders.findByIdForUpdate(order.getId()).isPresent());

		Specification<Order> spec = OrderSpecs.partnerId(partner.getId())
				.and(OrderSpecs.status(OrderStatus.PENDENTE));
		Page<Order> page = orders.findAll(spec, PageRequest.of(0, 20));
		assertEquals(1, page.getTotalElements());
	}
}
