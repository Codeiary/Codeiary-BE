package com.codeiary.domain.images.validation;

import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ImageUrlValidator {

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.endpoint:}")
    private String endpoint;

    public void validate(String value) {
        if (!isAllowed(value)) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    public boolean isAllowed(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            URI url = URI.create(value);
            if (url.getHost() == null || url.getUserInfo() != null) {
                return false;
            }
            if ("https".equalsIgnoreCase(url.getScheme())) {
                return true;
            }
            URI localEndpoint = StringUtils.hasText(endpoint) ? URI.create(endpoint) : null;
            return localEndpoint != null && "http".equals(localEndpoint.getScheme())
                    && "localhost".equals(localEndpoint.getHost())
                    && "http".equals(url.getScheme()) && "localhost".equals(url.getHost())
                    && url.getPort() == localEndpoint.getPort()
                    && (url.getPath().equals("/" + bucket) || url.getPath().startsWith("/" + bucket + "/"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
