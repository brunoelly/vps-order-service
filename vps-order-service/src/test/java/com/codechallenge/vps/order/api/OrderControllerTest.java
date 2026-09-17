package com.codechallenge.vps.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codechallenge.vps.order.application.OrderService;
import com.codechallenge.vps.order.domain.IdempotencyConflictException;
import com.codechallenge.vps.order.domain.IllegalOrderTransitionException;
import com.codechallenge.vps.order.domain.Order;
import com.codechallenge.vps.order.domain.OrderItem;
import com.codechallenge.vps.order.domain.OrderNotFoundException;
import com.codechallenge.vps.order.domain.OrderStatus;
import com.codechallenge.vps.partner.domain.InsufficientCreditException;
import com.codechallenge.vps.shared.api.RestExceptionHandler;

@WebMvcTest(OrderController.class)
@Import(RestExceptionHandler.class)
class OrderControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	OrderService orders;

	@Test
	void createReturns201() throws Exception {
		UUID partnerId = UUID.randomUUID();
		Order order = Order.create(partnerId, "idem-1", List.of(new OrderItem("SKU-1", "Widget", 2, new BigDecimal("10.00"))));
		when(orders.place(eq(partnerId), eq("idem-1"), any())).thenReturn(order);

		mvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", "idem-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"partnerId\":\"" + partnerId + "\",\"items\":[{\"sku\":\"SKU-1\",\"productName\":\"Widget\",\"quantity\":2,\"unitPrice\":10.00}]}"))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/v1/orders/" + order.getId()))
				.andExpect(jsonPath("$.status").value("PENDENTE"))
				.andExpect(jsonPath("$.total").value(20.00))
				.andExpect(jsonPath("$.items[0].sku").value("SKU-1"))
				.andExpect(jsonPath("$.items[0].lineTotal").value(20.00));
	}

	@Test
	void createRequiresIdempotencyKey() throws Exception {
		mvc.perform(post("/api/v1/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"partnerId\":\"" + UUID.randomUUID() + "\",\"items\":[{\"sku\":\"SKU-1\",\"productName\":\"Widget\",\"quantity\":1,\"unitPrice\":1.00}]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createRejectsEmptyItems() throws Exception {
		mvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"partnerId\":\"" + UUID.randomUUID() + "\",\"items\":[]}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.errors[0]").exists());
	}

	@Test
	void createRejectsMissingPartner() throws Exception {
		mvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"sku\":\"SKU-1\",\"productName\":\"Widget\",\"quantity\":1,\"unitPrice\":1.00}]}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void createRejectsNonPositiveQuantity() throws Exception {
		mvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"partnerId\":\"" + UUID.randomUUID() + "\",\"items\":[{\"sku\":\"SKU-1\",\"productName\":\"Widget\",\"quantity\":0,\"unitPrice\":1.00}]}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void getReturnsOrder() throws Exception {
		Order order = Order.create(UUID.randomUUID(), "k1", List.of(new OrderItem("SKU-1", "Widget", 1, new BigDecimal("5.00"))));
		when(orders.get(order.getId())).thenReturn(order);

		mvc.perform(get("/api/v1/orders/{id}", order.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(order.getId().toString()))
				.andExpect(jsonPath("$.partnerId").value(order.getPartnerId().toString()));
	}

	@Test
	void searchUsesDefaultPaging() throws Exception {
		when(orders.search(any(), any(), any(), any(), any(Pageable.class)))
				.thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(4), 0));

		mvc.perform(get("/api/v1/orders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isArray())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(0));

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(orders).search(any(), any(), any(), any(), captor.capture());
		assertEquals(0, captor.getValue().getPageNumber());
		assertEquals(20, captor.getValue().getPageSize());
	}

	@Test
	void searchCapsPageSize() throws Exception {
		UUID partnerId = UUID.randomUUID();
		Order order = Order.create(partnerId, "k1", List.of(new OrderItem("SKU-1", "Widget", 1, new BigDecimal("1.00"))));
		when(orders.search(eq(partnerId), eq(OrderStatus.PENDENTE), any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(order)));

		mvc.perform(get("/api/v1/orders")
				.param("partnerId", partnerId.toString())
				.param("status", "PENDENTE")
				.param("page", "2")
				.param("size", "500"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(order.getId().toString()));

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(orders).search(eq(partnerId), eq(OrderStatus.PENDENTE), any(), any(), captor.capture());
		assertEquals(2, captor.getValue().getPageNumber());
		assertEquals(100, captor.getValue().getPageSize());
	}

	@Test
	void searchRejectsUnknownStatus() throws Exception {
		mvc.perform(get("/api/v1/orders").param("status", "NOPE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Invalid value for status"));
	}

	@Test
	void changeStatus() throws Exception {
		Order order = Order.create(UUID.randomUUID(), "k1", List.of(new OrderItem("SKU-1", "Widget", 1, new BigDecimal("1.00"))));
		order.transitionTo(OrderStatus.APROVADO);
		when(orders.changeStatus(order.getId(), OrderStatus.APROVADO)).thenReturn(order);

		mvc.perform(patch("/api/v1/orders/{id}/status", order.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"APROVADO\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APROVADO"));
	}

	@Test
	void changeStatusRequiresValue() throws Exception {
		mvc.perform(patch("/api/v1/orders/{id}/status", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void cancel() throws Exception {
		Order order = Order.create(UUID.randomUUID(), "k1", List.of(new OrderItem("SKU-1", "Widget", 1, new BigDecimal("1.00"))));
		order.cancel();
		when(orders.cancel(order.getId())).thenReturn(order);

		mvc.perform(post("/api/v1/orders/{id}/cancel", order.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELADO"));
	}

	@Test
	void getUnknownOrderReturns404() throws Exception {
		UUID id = UUID.randomUUID();
		when(orders.get(id)).thenThrow(new OrderNotFoundException(id));

		mvc.perform(get("/api/v1/orders/{id}", id).header("X-Request-Id", "req-1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Order not found: " + id))
				.andExpect(jsonPath("$.instance").value("/api/v1/orders/" + id))
				.andExpect(jsonPath("$.traceId").value("req-1"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void createInsufficientCreditReturns422() throws Exception {
		UUID partnerId = UUID.randomUUID();
		when(orders.place(eq(partnerId), eq("k1"), any()))
				.thenThrow(new InsufficientCreditException(partnerId, new BigDecimal("20.00"), new BigDecimal("5.00")));

		mvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"partnerId\":\"" + partnerId + "\",\"items\":[{\"sku\":\"SKU-1\",\"productName\":\"Widget\",\"quantity\":2,\"unitPrice\":10.00}]}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void createIdempotencyConflictReturns409() throws Exception {
		UUID partnerId = UUID.randomUUID();
		when(orders.place(eq(partnerId), eq("k1"), any())).thenThrow(new IdempotencyConflictException("k1"));

		mvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"partnerId\":\"" + partnerId + "\",\"items\":[{\"sku\":\"SKU-1\",\"productName\":\"Widget\",\"quantity\":1,\"unitPrice\":1.00}]}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void illegalTransitionReturns409() throws Exception {
		UUID id = UUID.randomUUID();
		when(orders.changeStatus(id, OrderStatus.ENVIADO))
				.thenThrow(new IllegalOrderTransitionException(OrderStatus.PENDENTE, OrderStatus.ENVIADO));

		mvc.perform(patch("/api/v1/orders/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ENVIADO\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Cannot change order from PENDENTE to ENVIADO"));
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		mvc.perform(post("/api/v1/orders")
				.header("Idempotency-Key", "k1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Malformed JSON"));
	}
}
