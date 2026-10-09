package com.codeiary.domain.image.dto.response;

import java.time.Instant;
import java.util.Map;

public record ImageUploadResponse(
		String uploadUrl,
		String imageUrl,
		Map<String, String> headers,
		Instant expiresAt) {
}
