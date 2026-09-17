package com.codechallenge.vps.outbox;

import java.util.UUID;

public record OrderNotification(String eventType, UUID orderId, String payload) {
}
