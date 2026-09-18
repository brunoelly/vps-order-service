package com.codechallenge.vps.order.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrderItemRequest(
		@NotBlank @Size(max = 64) String sku,
		@NotBlank @Size(max = 255) String productName,
		@NotNull @Positive Integer quantity,
		@NotNull @DecimalMin("0.00") BigDecimal unitPrice) {
}
