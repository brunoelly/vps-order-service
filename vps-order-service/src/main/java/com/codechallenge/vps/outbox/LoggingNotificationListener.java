package com.codechallenge.vps.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationListener {

	private static final Logger log = LoggerFactory.getLogger(LoggingNotificationListener.class);

	@EventListener
	public void onNotification(OrderNotification notification) {
		log.info("order event type={} orderId={}", notification.eventType(), notification.orderId());
	}
}
