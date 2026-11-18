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
  public ResponseEntity<ErrorResponse> handleRestApiException(RestApiException restApiException) {
    logFailure(restApiException, restApiException.getErrorCode().getHttpStatus());
    return createResponse(restApiException.getErrorCode());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
    logFailure(exception, CommonErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus());
    return createResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException methodArgumentNotValidException,
      HttpHeaders httpHeaders,
      HttpStatusCode httpStatusCode,
      WebRequest webRequest) {

    String message =
        methodArgumentNotValidException.getBindingResult().getAllErrors().stream()
            .map(MessageSourceResolvable::getDefaultMessage)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(CommonErrorCode.INVALID_PARAMETER.getMessage());

    return handleExceptionInternal(
        methodArgumentNotValidException,
        new ErrorResponse(message, CommonErrorCode.INVALID_PARAMETER.name()),
        httpHeaders,
        httpStatusCode,
        webRequest);
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException handlerMethodValidationException,
      HttpHeaders httpHeaders,
      HttpStatusCode httpStatusCode,
      WebRequest webRequest) {

    if (httpStatusCode.is5xxServerError()) {
      return handleExceptionInternal(
          handlerMethodValidationException, null, httpHeaders, httpStatusCode, webRequest);
    }

    String message =
        handlerMethodValidationException.getAllErrors().stream()
            .map(MessageSourceResolvable::getDefaultMessage)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(CommonErrorCode.INVALID_PARAMETER.getMessage());

    return handleExceptionInternal(
        handlerMethodValidationException,
        new ErrorResponse(message, CommonErrorCode.INVALID_PARAMETER.name()),
        httpHeaders,
        httpStatusCode,
        webRequest);
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException typeMismatchException,
      HttpHeaders httpHeaders,
      HttpStatusCode httpStatusCode,
      WebRequest webRequest) {

    ErrorCode errorCode = CommonErrorCode.TYPE_MISMATCH;
    String message = errorCode.getMessage();
    if (typeMismatchException instanceof MethodArgumentTypeMismatchException mismatch) {
      message = "%s: %s".formatted(mismatch.getName(), message);
    }

    return handleExceptionInternal(
        typeMismatchException,
        new ErrorResponse(message, errorCode.name()),
        httpHeaders,
        httpStatusCode,
        webRequest);
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException missingServletRequestParameterException,
      HttpHeaders httpHeaders,
      HttpStatusCode httpStatusCode,
      WebRequest webRequest) {

    ErrorCode errorCode = CommonErrorCode.MISSING_REQUEST_PARAMETER;
    String message =
        "%s: %s"
            .formatted(
                missingServletRequestParameterException.getParameterName(), errorCode.getMessage());

    return handleExceptionInternal(
        missingServletRequestParameterException,
        new ErrorResponse(message, errorCode.name()),
        httpHeaders,
        httpStatusCode,
        webRequest);
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException servletRequestBindingException,
      HttpHeaders httpHeaders,
      HttpStatusCode httpStatusCode,
      WebRequest webRequest) {

    if (servletRequestBindingException instanceof MissingRequestHeaderException missingHeader) {
      ErrorCode errorCode = CommonErrorCode.MISSING_REQUEST_HEADER;
      String message = "%s: %s".formatted(missingHeader.getHeaderName(), errorCode.getMessage());

      return handleExceptionInternal(
          servletRequestBindingException,
          new ErrorResponse(message, errorCode.name()),
          httpHeaders,
          httpStatusCode,
          webRequest);
    }

    return handleExceptionInternal(
        servletRequestBindingException, null, httpHeaders, httpStatusCode, webRequest);
  }

  @Override
  @Nullable
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode httpStatusCode,
      WebRequest webRequest) {

    if (httpStatusCode.isError()) {
      logFailure(exception, httpStatusCode);
    }

    ErrorCode errorCode = resolveErrorCode(httpStatusCode);
    ErrorResponse response =
        body instanceof ErrorResponse errorResponse
            ? errorResponse
            : new ErrorResponse(errorCode.getMessage(), errorCode.name());

    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.putAll(headers);
    responseHeaders.setContentType(MediaType.APPLICATION_JSON);

    return super.handleExceptionInternal(
        exception, response, responseHeaders, httpStatusCode, webRequest);
  }

  private ResponseEntity<ErrorResponse> createResponse(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.getHttpStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(errorCode.getMessage(), errorCode.name()));
  }

  private ErrorCode resolveErrorCode(HttpStatusCode httpStatusCode) {
    return switch (httpStatusCode.value()) {
      case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
      case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
      case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
      case 413 -> CommonErrorCode.PAYLOAD_TOO_LARGE;
      case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
      case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
      default ->
          httpStatusCode.is4xxClientError()
              ? CommonErrorCode.INVALID_PARAMETER
              : CommonErrorCode.INTERNAL_SERVER_ERROR;
    };
  }

  private void logFailure(Exception exception, HttpStatusCode httpStatusCode) {
    var event = httpStatusCode.is5xxServerError() ? log.atError() : log.atWarn();
    String errorMessage =
        exception instanceof RestApiException restApiException
            ? restApiException.getErrorCode().getMessage()
            : httpStatusCode.is4xxClientError() ? "Request failed" : "Unexpected server error";
    event
        .addKeyValue("status", httpStatusCode.value())
        .addKeyValue("error_type", exception.getClass().getSimpleName())
        .addKeyValue("error_message", errorMessage);
    if (httpStatusCode.is5xxServerError()) {
      event.setCause(exception);
    }
    event.log("Request failed");
  }
}
