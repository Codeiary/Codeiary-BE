package com.codeiary.global.security.dto.adapter;

import java.util.Map;
import java.util.Objects;

import com.codeiary.global.security.dto.OAuth2Response;


public final class NaverOAuth2Response implements OAuth2Response {

    private final Map<String, Object> attributes;

    public NaverOAuth2Response(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

	@Override
	public String getProvider() {
		return "naver";
	}

	@Override
    public String getSubject() {
		return Objects.toString(response().get("id"), null);
    }

	@Override
	public String getEmail() {
		return Objects.toString(response().get("email"), null);
	}

	@Override
	public String getName() {
		return Objects.toString(response().get("name"), null);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> response() {
		return (Map<String, Object>) attributes.get("response");
	}
}
