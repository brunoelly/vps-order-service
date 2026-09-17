package com.codechallenge.vps.order.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.codechallenge.vps.order.application.OrderService;
import com.codechallenge.vps.order.domain.OrderItem;
import com.codechallenge.vps.order.domain.OrderStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

	private final OrderService orders;

	public OrderController(OrderService orders) {
		this.orders = orders;
	}

	@PostMapping
	@Operation(summary = "Create order")
	@Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true)
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "Invalid body or missing key")
	@ApiResponse(responseCode = "409", description = "Idempotency conflict")
	@ApiResponse(responseCode = "422", description = "Insufficient credit")
	public ResponseEntity<OrderResponse> create(
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request) {
		List<OrderItem> items = request.items().stream()
				.map(line -> new OrderItem(line.sku(), line.productName(), line.quantity(), line.unitPrice()))
				.toList();
		OrderResponse body = OrderResponse.from(orders.place(request.partnerId(), idempotencyKey, items));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(body.id())
				.toUri();
		return ResponseEntity.created(location).body(body);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get order")
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "404", description = "Not found")
	public OrderResponse get(@PathVariable UUID id) {
		return OrderResponse.from(orders.get(id));
	}

	@GetMapping
	@Operation(summary = "Search orders")
	@ApiResponse(responseCode = "200", description = "OK")
	public PageResponse<OrderResponse> search(
			@RequestParam(required = false) UUID partnerId,
			@RequestParam(required = false) OrderStatus status,
			@RequestParam(required = false) Instant createdFrom,
			@RequestParam(required = false) Instant createdTo,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return PageResponse.from(
				orders.search(partnerId, status, createdFrom, createdTo, Paging.of(page, size))
						.map(OrderResponse::from));
	}

	@PatchMapping("/{id}/status")
	@Operation(summary = "Update order status")
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "404", description = "Not found")
	@ApiResponse(responseCode = "409", description = "Illegal transition")
	public OrderResponse changeStatus(
			@PathVariable UUID id,
			@Valid @RequestBody ChangeStatusRequest request) {
		return OrderResponse.from(orders.changeStatus(id, request.status()));
	}

	@PostMapping("/{id}/cancel")
	@Operation(summary = "Cancel order")
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "404", description = "Not found")
	@ApiResponse(responseCode = "409", description = "Illegal transition")
	public OrderResponse cancel(@PathVariable UUID id) {
		return OrderResponse.from(orders.cancel(id));
	}
}
