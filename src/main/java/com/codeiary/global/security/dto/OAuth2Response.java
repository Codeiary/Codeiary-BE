package com.codeiary.global.security.dto;

public interface OAuth2Response {

	String getProvider();

	String getSubject();

    String getEmail();

    String getName();
}
