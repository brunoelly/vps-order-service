package com.codechallenge.vps.partner.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePartnerRequest(
		@NotBlank @Size(max = 160) String name,
		@NotNull @DecimalMin("0.00") BigDecimal creditLimit) {
}
