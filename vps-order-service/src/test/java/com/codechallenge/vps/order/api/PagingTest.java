package com.codechallenge.vps.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class PagingTest {

	@Test
	void defaultsWhenPageAndSizeMissing() {
		Pageable pageable = Paging.of(null, null);
		assertEquals(0, pageable.getPageNumber());
		assertEquals(20, pageable.getPageSize());
	}

	@Test
	void negativePageBecomesZero() {
		assertEquals(0, Paging.of(-2, 10).getPageNumber());
		assertEquals(10, Paging.of(-2, 10).getPageSize());
	}

	@Test
	void sizeBelowOneUsesDefault() {
		assertEquals(20, Paging.of(1, 0).getPageSize());
		assertEquals(20, Paging.of(1, -5).getPageSize());
		assertEquals(1, Paging.of(1, 0).getPageNumber());
	}

	@Test
	void sizeIsCappedAtOneHundred() {
		assertEquals(100, Paging.of(0, 500).getPageSize());
	}
}
