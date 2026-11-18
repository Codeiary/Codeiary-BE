package com.codeiary.domain.user.service;

import com.codeiary.domain.image.validation.ImageUrlValidator;
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
import com.codeiary.global.exception.RestApiException;
import com.codeiary.global.logging.LogExecution;
import com.codeiary.global.security.exception.SecurityErrorCode;
import java.util.Locale;
import java.util.Set;
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
  private static final Set<String> RESERVED_NICKNAMES =
      Set.of(
          "admin",
          "assets",
          "auth",
          "blog",
          "healthz",
          "home",
          "login",
          "news",
          "onboarding",
          "portfolio",
          "write");

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final ImageUrlValidator imageUrlValidator;

  public UserProfileResponse getProfile(User user) {
    return userMapper.toResponse(user);
  }

  @LogExecution
  public UserNeighborhoodPageResponse getNeighborhood(int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_NEIGHBORHOOD_PAGE_SIZE);
    Page<UserNeighborhoodResponse> result =
        userRepository.findNeighborhood(PageRequest.of(safePage, safeSize));
    return new UserNeighborhoodPageResponse(
        result.getContent(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.isFirst(),
        result.isLast());
  }

  @LogExecution
  public PublicUserProfileResponse getPublicProfile(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .filter(this::hasPublicProfile)
            .orElseThrow(() -> new RestApiException(UserErrorCode.NOT_FOUND_USER));
    return userMapper.toPublicResponse(user);
  }

  @LogExecution
  public PublicUserProfileResponse getPublicProfileByNickname(String nickname) {
    User user =
        userRepository
            .findByNicknameIgnoreCase(validNickname(nickname))
            .filter(this::hasPublicProfile)
            .orElseThrow(() -> new RestApiException(UserErrorCode.NOT_FOUND_USER));
    return userMapper.toPublicResponse(user);
  }

  public NicknameAvailabilityResponse checkNickname(Long userId, String nickname) {
    String normalized = validNickname(nickname);
    return new NicknameAvailabilityResponse(
        !RESERVED_NICKNAMES.contains(normalized.toLowerCase(Locale.ROOT))
            && !userRepository.existsByNicknameIgnoreCaseAndIdNot(normalized, userId));
  }

  @Transactional
  @LogExecution
  public UserProfileResponse completeOnboarding(Long userId, OnboardingRequest onboardingRequest) {
    User user = loadCurrentUser(userId);
    String nickname = availableNickname(userId, onboardingRequest.nickname());
    user.completeOnboarding(nickname);
    return saveProfile(user);
  }

  @Transactional
  @LogExecution
  public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest updateProfileRequest) {
    String profileImageUrl = optionalValue(updateProfileRequest.profileImageUrl());
    if (profileImageUrl != null) {
      imageUrlValidator.validate(profileImageUrl);
    }
    User user = loadCurrentUser(userId);
    String nickname = availableNickname(userId, updateProfileRequest.nickname());
    user.updatePublicProfile(
        nickname,
        profileImageUrl,
        optionalValue(updateProfileRequest.githubUrl()),
        optionalValue(updateProfileRequest.contactEmail()));
    return saveProfile(user);
  }

  private User loadCurrentUser(Long userId) {
    return userRepository
        .findByIdForUpdate(userId)
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
    if (RESERVED_NICKNAMES.contains(nickname.toLowerCase(Locale.ROOT))) {
      throw new RestApiException(UserErrorCode.INVALID_NICKNAME);
    }
    if (userRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, userId)) {
      throw new RestApiException(UserErrorCode.NICKNAME_TAKEN);
    }
    return nickname;
  }

  private String optionalValue(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private UserProfileResponse saveProfile(User user) {
    try {
      return userMapper.toResponse(userRepository.saveAndFlush(user));
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
