package com.codechallenge.vps.partner.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record ChangeCreditLimitRequest(@NotNull @DecimalMin("0.00") BigDecimal creditLimit) {
}
