package com.codechallenge.vps.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

	List<OutboxMessage> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
