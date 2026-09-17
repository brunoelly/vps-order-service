package com.codechallenge.vps.order.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {

	PENDENTE,
	APROVADO,
	EM_PROCESSAMENTO,
	ENVIADO,
	ENTREGUE,
	CANCELADO;

	public boolean canTransitionTo(OrderStatus target) {
		if (target == null || target == this) {
			return false;
		}
		return allowedTargets().contains(target);
	}

	private Set<OrderStatus> allowedTargets() {
		return switch (this) {
			case PENDENTE -> EnumSet.of(APROVADO, CANCELADO);
			case APROVADO -> EnumSet.of(EM_PROCESSAMENTO, CANCELADO);
			case EM_PROCESSAMENTO -> EnumSet.of(ENVIADO);
			case ENVIADO -> EnumSet.of(ENTREGUE);
			case ENTREGUE, CANCELADO -> EnumSet.noneOf(OrderStatus.class);
		};
	}
}
