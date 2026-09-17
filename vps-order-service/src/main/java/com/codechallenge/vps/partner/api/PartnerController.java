package com.codechallenge.vps.partner.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.codechallenge.vps.partner.application.PartnerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/partners")
@Tag(name = "Partners")
public class PartnerController {

	private final PartnerService partners;

	public PartnerController(PartnerService partners) {
		this.partners = partners;
	}

	@PostMapping
	@Operation(summary = "Create partner")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "Invalid body")
	public ResponseEntity<PartnerResponse> create(@Valid @RequestBody CreatePartnerRequest request) {
		PartnerResponse body = PartnerResponse.from(partners.create(request.name(), request.creditLimit()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(body.id())
				.toUri();
		return ResponseEntity.created(location).body(body);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get partner")
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "404", description = "Not found")
	public PartnerResponse get(@PathVariable UUID id) {
		return PartnerResponse.from(partners.get(id));
	}

	@PatchMapping("/{id}/credit-limit")
	@Operation(summary = "Change credit limit")
	@ApiResponse(responseCode = "200", description = "OK")
	@ApiResponse(responseCode = "400", description = "Invalid body")
	@ApiResponse(responseCode = "404", description = "Not found")
	public PartnerResponse changeCreditLimit(
			@PathVariable UUID id,
			@Valid @RequestBody ChangeCreditLimitRequest request) {
		return PartnerResponse.from(partners.changeCreditLimit(id, request.creditLimit()));
	}
}
