package com.codeiary.global.logging;

import com.codeiary.global.exception.RestApiException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Slf4j
@Component
public class ApiTrace {

	@Around("@within(com.codeiary.global.logging.LogExecution) || "
			+ "@annotation(com.codeiary.global.logging.LogExecution)")
	public Object trace(ProceedingJoinPoint invocation) throws Throwable {
		long startedAt = System.nanoTime();
		Class<?> type = invocation.getSignature().getDeclaringType();
		String target = type.getSimpleName() + "." + invocation.getSignature().getName();

		log.atInfo()
				.addKeyValue("event", "method_start")
				.addKeyValue("target", target)
				.log("Method execution started");
		try {
			Object result = invocation.proceed();
			log.atInfo()
					.addKeyValue("event", "method_end")
					.addKeyValue("target", target)
					.addKeyValue("durationMs", elapsedMillis(startedAt))
					.log("Method execution completed");
			return result;
		} catch (Throwable exception) {
			HttpStatusCode status = statusOf(exception);
			String errorMessage = exception instanceof RestApiException restApiException
					? restApiException.getErrorCode().getMessage()
					: status.is4xxClientError() ? "Request failed" : "Unexpected server error";
			var event = status.is4xxClientError() ? log.atWarn() : log.atError();
			event.addKeyValue("event", "method_failure")
					.addKeyValue("target", target)
					.addKeyValue("status", status.value())
					.addKeyValue("durationMs", elapsedMillis(startedAt))
					.addKeyValue("error_type", exception.getClass().getSimpleName())
					.addKeyValue("error_message", errorMessage);
			if (status.is5xxServerError()) {
				event.setCause(exception);
			}
			event.log("Method execution failed");
			throw exception;
		}
	}

	private HttpStatusCode statusOf(Throwable exception) {
		if (exception instanceof RestApiException restApiException) {
			return restApiException.getErrorCode().getHttpStatus();
		}
		if (exception instanceof ErrorResponse errorResponse) {
			return errorResponse.getStatusCode();
		}
		return HttpStatusCode.valueOf(500);
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
