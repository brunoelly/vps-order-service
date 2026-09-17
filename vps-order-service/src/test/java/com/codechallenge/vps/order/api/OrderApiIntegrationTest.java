package com.codechallenge.vps.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.codechallenge.vps.order.domain.Order;
import com.codechallenge.vps.order.domain.OrderStatus;
import com.codechallenge.vps.order.infra.OrderRepository;
import com.codechallenge.vps.partner.infra.PartnerRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers(disabledWithoutDocker = true)
class OrderApiIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	MockMvc mvc;

	@Autowired
	PartnerRepository partners;

	@Autowired
	OrderRepository orders;

	@Test
	void createGetSearchApproveAndCancel() throws Exception {
		String partnerId = createPartner("Acme", "200.00");

		MvcResult created = mvc.perform(post("/api/v1/orders")
						.header("Idempotency-Key", "idem-happy")
						.contentType(APPLICATION_JSON)
						.content(orderJson(partnerId, "10.00")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDENTE"))
				.andExpect(jsonPath("$.total").value(10.00))
				.andReturn();
		String orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/api/v1/orders/{id}", orderId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.partnerId").value(partnerId));

		mvc.perform(get("/api/v1/orders").param("partnerId", partnerId).param("status", "PENDENTE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));

		mvc.perform(patch("/api/v1/orders/{id}/status", orderId)
						.contentType(APPLICATION_JSON)
						.content("{\"status\":\"APROVADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APROVADO"));

		mvc.perform(get("/api/v1/partners/{id}", partnerId))
				.andExpect(jsonPath("$.availableCredit").value(190.00));

		mvc.perform(post("/api/v1/orders/{id}/cancel", orderId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELADO"));

		mvc.perform(get("/api/v1/partners/{id}", partnerId))
				.andExpect(jsonPath("$.availableCredit").value(200.00));
	}

	@Test
	void unknownOrderIs404() throws Exception {
		mvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void createWithoutIdempotencyKeyIs400() throws Exception {
		mvc.perform(post("/api/v1/orders")
						.contentType(APPLICATION_JSON)
						.content(orderJson(UUID.randomUUID().toString(), "1.00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void insufficientCreditIs422() throws Exception {
		String partnerId = createPartner("Thin", "5.00");
		mvc.perform(post("/api/v1/orders")
						.header("Idempotency-Key", "too-big")
						.contentType(APPLICATION_JSON)
						.content(orderJson(partnerId, "10.00")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void illegalJumpIs409() throws Exception {
		String partnerId = createPartner("Jump", "50.00");
		String orderId = createOrder(partnerId, "jump-1", "10.00");
		mvc.perform(patch("/api/v1/orders/{id}/status", orderId)
						.contentType(APPLICATION_JSON)
						.content("{\"status\":\"ENVIADO\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void idempotentReplayReturnsSameOrder() throws Exception {
		String partnerId = createPartner("Replay", "100.00");
		String first = createOrder(partnerId, "same-key", "8.00");
		MvcResult again = mvc.perform(post("/api/v1/orders")
						.header("Idempotency-Key", "same-key")
						.contentType(APPLICATION_JSON)
						.content(orderJson(partnerId, "8.00")))
				.andExpect(status().isCreated())
				.andReturn();
		assertEquals(first, JsonPath.read(again.getResponse().getContentAsString(), "$.id"));
		mvc.perform(get("/api/v1/partners/{id}", partnerId))
				.andExpect(jsonPath("$.availableCredit").value(92.00));
	}

	@Test
	void idempotentConflictWhenPayloadDiffers() throws Exception {
		String partnerId = createPartner("Clash", "100.00");
		createOrder(partnerId, "clash-key", "8.00");
		mvc.perform(post("/api/v1/orders")
						.header("Idempotency-Key", "clash-key")
						.contentType(APPLICATION_JSON)
						.content(orderJson(partnerId, "9.00")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void concurrentCreatesDoNotOverdrawCredit() throws Exception {
		String partnerId = createPartner("Busy", "50.00");
		int n = 20;
		ExecutorService pool = Executors.newFixedThreadPool(n);
		CountDownLatch ready = new CountDownLatch(n);
		CountDownLatch go = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(n);
		AtomicInteger created = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		AtomicInteger other = new AtomicInteger();

		for (int i = 0; i < n; i++) {
			String key = "c-" + i;
			pool.submit(() -> {
				ready.countDown();
				try {
					go.await();
					int status = mvc.perform(post("/api/v1/orders")
									.header("Idempotency-Key", key)
									.contentType(APPLICATION_JSON)
									.content(orderJson(partnerId, "10.00")))
							.andReturn()
							.getResponse()
							.getStatus();
					if (status == 201) {
						created.incrementAndGet();
					} else if (status == 422) {
						rejected.incrementAndGet();
					} else {
						other.incrementAndGet();
					}
				} catch (Exception e) {
					other.incrementAndGet();
				} finally {
					done.countDown();
				}
			});
		}

		assertTrue(ready.await(15, TimeUnit.SECONDS));
		go.countDown();
		assertTrue(done.await(60, TimeUnit.SECONDS));
		pool.shutdown();

		assertEquals(0, other.get());
		assertEquals(n, created.get() + rejected.get());
		assertEquals(5, created.get());
		assertEquals(15, rejected.get());

		var partner = partners.findById(UUID.fromString(partnerId)).orElseThrow();
		assertTrue(partner.getAvailableCredit().compareTo(BigDecimal.ZERO) >= 0);
		BigDecimal held = orders.findAll().stream()
				.filter(o -> o.getPartnerId().equals(partner.getId()))
				.filter(o -> o.getStatus() != OrderStatus.CANCELADO)
				.map(Order::getReservedAmount)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		assertEquals(0, partner.getCreditLimit().subtract(partner.getAvailableCredit()).compareTo(held));
		assertEquals(0, partner.getAvailableCredit().compareTo(BigDecimal.ZERO));
	}

	private String createPartner(String name, String limit) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/partners")
						.contentType(APPLICATION_JSON)
						.content("{\"name\":\"" + name + "\",\"creditLimit\":" + limit + "}"))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private String createOrder(String partnerId, String key, String price) throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/orders")
						.header("Idempotency-Key", key)
						.contentType(APPLICATION_JSON)
						.content(orderJson(partnerId, price)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private static String orderJson(String partnerId, String price) {
		return "{\"partnerId\":\"" + partnerId + "\",\"items\":[{\"sku\":\"SKU-1\",\"productName\":\"Widget\",\"quantity\":1,\"unitPrice\":" + price + "}]}";
	}
}
