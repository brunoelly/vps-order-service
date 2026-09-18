package com.codechallenge.vps.shared.api;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.codechallenge.vps.order.domain.IdempotencyConflictException;
import com.codechallenge.vps.order.domain.IllegalOrderTransitionException;
import com.codechallenge.vps.order.domain.OrderNotFoundException;
import com.codechallenge.vps.partner.domain.InsufficientCreditException;
import com.codechallenge.vps.partner.domain.PartnerNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class RestExceptionHandler {

	@ExceptionHandler({ OrderNotFoundException.class, PartnerNotFoundException.class })
	public ProblemDetail notFound(RuntimeException ex, HttpServletRequest request) {
		return problem(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler({ IllegalOrderTransitionException.class, IdempotencyConflictException.class })
	public ProblemDetail conflict(RuntimeException ex, HttpServletRequest request) {
		return problem(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler({ InsufficientCreditException.class, IllegalArgumentException.class })
	public ProblemDetail unprocessable(RuntimeException ex, HttpServletRequest request) {
		return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail invalid(MethodArgumentNotValidException ex, HttpServletRequest request) {
		ProblemDetail body = problem(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed", request);
		body.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.toList());
		return body;
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ProblemDetail missingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "Missing header " + ex.getHeaderName(), request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName(), request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail malformed(HttpServletRequest request) {
		return problem(HttpStatus.BAD_REQUEST, "Malformed JSON", request);
	}

	private static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setInstance(URI.create(request.getRequestURI()));
		body.setProperty("timestamp", Instant.now().toString());
		String traceId = request.getHeader("X-Request-Id");
		body.setProperty("traceId", traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId);
		return body;
	}
}
