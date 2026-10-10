package com.codeiary.domain.user.service;

import com.codeiary.domain.user.dto.UserMapper;
import com.codeiary.domain.user.dto.request.OnboardingRequest;
import com.codeiary.domain.user.dto.request.UpdateProfileRequest;
import com.codeiary.domain.user.dto.response.NicknameAvailabilityResponse;
import com.codeiary.domain.user.dto.response.PublicUserProfileResponse;
import com.codeiary.domain.user.dto.response.UserNeighborhoodPageResponse;
import com.codeiary.domain.user.dto.response.UserNeighborhoodResponse;
import com.codeiary.domain.user.dto.response.UserProfileResponse;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.exception.UserErrorCode;
import com.codeiary.domain.user.repository.UserRepository;
import com.codeiary.domain.image.validation.ImageUrlValidator;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.security.exception.SecurityErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final int MAX_NEIGHBORHOOD_PAGE_SIZE = 100;

    private final UserRepository users;
    private final UserMapper userMapper;
    private final ImageUrlValidator imageUrls;

    public UserProfileResponse getProfile(User user) {
        return userMapper.toResponse(user);
    }

    public UserNeighborhoodPageResponse getNeighborhood(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_NEIGHBORHOOD_PAGE_SIZE);
        Page<UserNeighborhoodResponse> result = users.findNeighborhood(PageRequest.of(safePage, safeSize));
        return new UserNeighborhoodPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    public PublicUserProfileResponse getPublicProfile(Long userId) {
        User user = users.findById(userId)
                .filter(this::hasPublicProfile)
                .orElseThrow(() -> new RestApiException(UserErrorCode.NOT_FOUND_USER));
        return userMapper.toPublicResponse(user);
    }

    public PublicUserProfileResponse getPublicProfileByNickname(String nickname) {
        User user = users.findByNicknameIgnoreCase(validNickname(nickname))
                .filter(this::hasPublicProfile)
                .orElseThrow(() -> new RestApiException(UserErrorCode.NOT_FOUND_USER));
        return userMapper.toPublicResponse(user);
    }

    public NicknameAvailabilityResponse checkNickname(Long userId, String nickname) {
        return new NicknameAvailabilityResponse(
                !users.existsByNicknameIgnoreCaseAndIdNot(validNickname(nickname), userId));
    }

    @Transactional
    public UserProfileResponse completeOnboarding(Long userId, OnboardingRequest request) {
        User user = loadCurrentUser(userId);
        String nickname = availableNickname(userId, request.nickname());
        user.completeOnboarding(nickname);
        return saveProfile(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        String profileImageUrl = optionalValue(request.profileImageUrl());
        if (profileImageUrl != null) {
            imageUrls.validate(profileImageUrl);
        }
        User user = loadCurrentUser(userId);
        String nickname = availableNickname(userId, request.nickname());
        user.updatePublicProfile(nickname, profileImageUrl,
                optionalValue(request.githubUrl()), optionalValue(request.contactEmail()));
        return saveProfile(user);
    }

    private User loadCurrentUser(Long userId) {
        return users.findByIdForUpdate(userId)
                .filter(User::isEnabled)
                .orElseThrow(() -> new RestApiException(SecurityErrorCode.UNAUTHORIZED));
    }

    private boolean hasPublicProfile(User user) {
        return user.isEnabled() && user.isOnboardingCompleted();
    }

    private String validNickname(String value) {
        if (value == null || !value.trim().matches("[가-힣A-Za-z0-9_]{2,20}")) {
            throw new RestApiException(UserErrorCode.INVALID_NICKNAME);
        }
        return value.trim();
    }

    private String availableNickname(Long userId, String value) {
        String nickname = validNickname(value);
        if (users.existsByNicknameIgnoreCaseAndIdNot(nickname, userId)) {
            throw new RestApiException(UserErrorCode.NICKNAME_TAKEN);
        }
        return nickname;
    }

    private String optionalValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UserProfileResponse saveProfile(User user) {
        try {
            return userMapper.toResponse(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // 사전 중복 확인 이후 동시에 같은 닉네임을 저장해도 409로 응답한다.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "uk_users_nickname".equals(violation.getConstraintName())) {
                    throw new RestApiException(UserErrorCode.NICKNAME_TAKEN, exception);
                }
            }
            throw exception;
        }
    }
}
