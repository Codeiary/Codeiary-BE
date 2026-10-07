package com.codeiary.global.security.dto.adapter;

import java.util.Map;
import java.util.Objects;

import com.codeiary.global.security.dto.OAuth2Response;


public final class GoogleOAuth2Response implements OAuth2Response {

    private final Map<String, Object> attributes;

    public GoogleOAuth2Response(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

	@Override
	public String getProvider() {
		return "google";
	}

	@Override
    public String getSubject() {
        return Objects.toString(attributes.get("sub"), null);
    }

	@Override
	public String getEmail() {
		return Objects.toString(attributes.get("email"), null);
	}

	@Override
	public String getName() {
		return Objects.toString(attributes.get("name"), null);
	}
}
