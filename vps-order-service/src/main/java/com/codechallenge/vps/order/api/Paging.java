package com.codechallenge.vps.order.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class Paging {

	static final int DEFAULT_SIZE = 20;
	static final int MAX_SIZE = 100;

	private Paging() {
	}

	static Pageable of(Integer page, Integer size) {
		int p = page == null || page < 0 ? 0 : page;
		int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
		return PageRequest.of(p, s, Sort.by("createdAt").descending());
	}
}
