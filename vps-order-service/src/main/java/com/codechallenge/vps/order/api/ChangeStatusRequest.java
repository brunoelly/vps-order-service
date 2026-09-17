package com.codechallenge.vps.order.api;

import com.codechallenge.vps.order.domain.OrderStatus;

import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull OrderStatus status) {
}
