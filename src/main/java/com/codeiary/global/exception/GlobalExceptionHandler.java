package com.codeiary.global.exception;

import com.codeiary.global.exception.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RestApiException.class)
    public ResponseEntity<ErrorResponse> handleRestApiException(RestApiException exception) {
        if (exception.getErrorCode().getHttpStatus().is5xxServerError()) {
            log.error("API 처리 오류: {}", exception.getErrorCode().name(), exception);
        }
        return createResponse(exception.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("예상하지 못한 서버 오류", exception);
        return createResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String message = exception.getBindingResult().getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(CommonErrorCode.INVALID_PARAMETER.getMessage());

        return handleExceptionInternal(exception,
                new ErrorResponse(message, CommonErrorCode.INVALID_PARAMETER.name()),
                headers, status, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        if (status.is5xxServerError()) {
            return handleExceptionInternal(exception, null, headers, status, request);
        }

        String message = exception.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(CommonErrorCode.INVALID_PARAMETER.getMessage());

        return handleExceptionInternal(exception,
                new ErrorResponse(message, CommonErrorCode.INVALID_PARAMETER.name()),
                headers, status, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ErrorCode errorCode = CommonErrorCode.TYPE_MISMATCH;
        String message = errorCode.getMessage();
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            message = "%s: %s".formatted(mismatch.getName(), message);
        }

        return handleExceptionInternal(exception, new ErrorResponse(message, errorCode.name()),
                headers, status, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ErrorCode errorCode = CommonErrorCode.MISSING_REQUEST_PARAMETER;
        String message = "%s: %s".formatted(exception.getParameterName(), errorCode.getMessage());

        return handleExceptionInternal(exception, new ErrorResponse(message, errorCode.name()),
                headers, status, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        if (exception instanceof MissingRequestHeaderException missingHeader) {
            ErrorCode errorCode = CommonErrorCode.MISSING_REQUEST_HEADER;
            String message = "%s: %s".formatted(missingHeader.getHeaderName(), errorCode.getMessage());

            return handleExceptionInternal(exception, new ErrorResponse(message, errorCode.name()),
                    headers, status, request);
        }

        return handleExceptionInternal(exception, null, headers, status, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        if (status.is5xxServerError()) {
            log.error("Spring MVC 처리 오류", exception);
        }

        ErrorCode errorCode = resolveErrorCode(status);
        ErrorResponse response = body instanceof ErrorResponse errorResponse
                ? errorResponse
                : new ErrorResponse(errorCode.getMessage(), errorCode.name());

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);

        return super.handleExceptionInternal(exception, response, responseHeaders, status, request);
    }

    private ResponseEntity<ErrorResponse> createResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(errorCode.getMessage(), errorCode.name()));
    }

    private ErrorCode resolveErrorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
            case 413 -> CommonErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError()
                    ? CommonErrorCode.INVALID_PARAMETER
                    : CommonErrorCode.INTERNAL_SERVER_ERROR;
        };
    }
}
