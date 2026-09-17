package com.codechallenge.vps.partner.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.codechallenge.vps.partner.domain.Partner;

import jakarta.persistence.LockModeType;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Partner p where p.id = :id")
	Optional<Partner> findByIdForUpdate(@Param("id") UUID id);
}
