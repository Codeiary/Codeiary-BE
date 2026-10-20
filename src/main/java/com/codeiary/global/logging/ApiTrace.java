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

		log.info("[START] {}", target);
		try {
			Object result = invocation.proceed();
			log.info("[END] {} in {} ms", target, elapsedMillis(startedAt));
			return result;
		} catch (Throwable exception) {
			HttpStatusCode status = statusOf(exception);
			if (status.is4xxClientError()) {
				log.warn("[END] {} failed with HTTP {} in {} ms ({})", target, status.value(),
						elapsedMillis(startedAt), exception.getClass().getSimpleName());
			} else {
				log.error("[END] {} failed with HTTP {} in {} ms", target, status.value(),
						elapsedMillis(startedAt), exception);
			}
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
