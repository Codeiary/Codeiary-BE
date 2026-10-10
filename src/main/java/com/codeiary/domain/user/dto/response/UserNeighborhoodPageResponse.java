package com.codeiary.domain.user.dto.response;

import java.util.List;

public record UserNeighborhoodPageResponse(
        List<UserNeighborhoodResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
