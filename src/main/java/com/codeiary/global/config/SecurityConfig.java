package com.codeiary.global.config;

import com.codeiary.domain.github.dto.GitHubLinkContext;
import com.codeiary.domain.user.entity.User;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.global.logging.MDCFilter;
import com.codeiary.global.security.exception.SecurityErrorCode;
import com.codeiary.global.security.exception.SecurityErrorHandler;
import com.codeiary.global.security.filter.TokenAuthenticationFilter;
import com.codeiary.global.security.handler.CustomOauth2SuccessHandler;
import com.codeiary.global.security.repository.OAuth2RequestRepository;
import com.codeiary.global.security.service.CustomOAuth2UserService;
import com.codeiary.global.security.service.CustomOidcUserService;
import jakarta.servlet.DispatcherType;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity httpSecurity,
      SecurityErrorHandler securityErrorHandler,
      TokenAuthenticationFilter tokenAuthenticationFilter,
      CustomOAuth2UserService customOAuth2UserService,
      CustomOidcUserService customOidcUserService,
      CustomOauth2SuccessHandler customOauth2SuccessHandler,
      OAuth2RequestRepository authorizationRequests,
      DefaultOAuth2AuthorizationRequestResolver defaultOAuth2AuthorizationRequestResolver,
      @Value("${logging.api.warn-error-only:}") List<String> warnErrorOnlyApis) {
    return httpSecurity
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .oauth2Login(oauth2 ->
            oauth2
                .withObjectPostProcessor(
                    new ObjectPostProcessor<OAuth2AuthorizationRequestRedirectFilter>() {
                      @Override
                      public <O extends OAuth2AuthorizationRequestRedirectFilter> O postProcess(
                          O filter) {
                        filter.setAuthenticationFailureHandler(securityErrorHandler);
                        return filter;
                      }
                    })
                .authorizationEndpoint(endpoint ->
                    endpoint
                        .authorizationRequestResolver(defaultOAuth2AuthorizationRequestResolver)
                        .authorizationRequestRepository(authorizationRequests))
                .userInfoEndpoint(userInfo ->
                    userInfo
                        .userService(customOAuth2UserService)
                        .oidcUserService(customOidcUserService))
                .successHandler(customOauth2SuccessHandler)
                .failureHandler(securityErrorHandler::handleOAuthFailure))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(securityErrorHandler)
                .accessDeniedHandler(securityErrorHandler))
        .authorizeHttpRequests(authorize ->
            authorize
                .dispatcherTypeMatchers(DispatcherType.ERROR)
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/health")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                .permitAll()
                .requestMatchers("/oauth2/**", "/login/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/reissue", "/auth/logout")
                .permitAll()
                .requestMatchers("/admin", "/admin/**")
                .hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/users/me", "/users/nickname-availability")
                .authenticated()
                .requestMatchers(HttpMethod.GET, "/posts/mine")
                .authenticated()
                .requestMatchers(HttpMethod.GET, "/portfolios/mine", "/portfolios/fonts")
                .hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/portfolios", "/portfolios/*")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/users/me/onboarding")
                .authenticated()
                .requestMatchers(
                    HttpMethod.GET,
                    "/posts/**",
                    "/users/**",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**")
                .permitAll()
                .anyRequest()
                .hasAnyRole("USER", "ADMIN"))
        .addFilterBefore(tokenAuthenticationFilter, OAuth2AuthorizationRequestRedirectFilter.class)
        .addFilterBefore(new MDCFilter(warnErrorOnlyApis), TokenAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${cors.allowed-origins}") List<String> origins) {
    CorsConfiguration corsConfiguration = new CorsConfiguration();
    corsConfiguration.setAllowedOrigins(origins);
    corsConfiguration.setAllowedMethods(
        List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    corsConfiguration.setAllowedHeaders(List.of("*"));
    corsConfiguration.setExposedHeaders(List.of(MDCFilter.REQUEST_ID_HEADER));
    corsConfiguration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource =
        new UrlBasedCorsConfigurationSource();
    urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);
    return urlBasedCorsConfigurationSource;
  }

  @Bean
  FilterRegistrationBean<TokenAuthenticationFilter> tokenFilterRegistration(
      TokenAuthenticationFilter tokenAuthenticationFilter) {
    FilterRegistrationBean<TokenAuthenticationFilter> filterRegistrationBean =
        new FilterRegistrationBean<>(tokenAuthenticationFilter);
    filterRegistrationBean.setEnabled(false);
    return filterRegistrationBean;
  }

  @Bean
  DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver(
      ClientRegistrationRepository clientRegistrationRepository) {
    var resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            clientRegistrationRepository, "/oauth2/authorization");
    resolver.setAuthorizationRequestCustomizer(builder ->
        builder.attributes(attributes -> {
          if (!OAuthProvider.GITHUB
              .getRegistrationId()
              .equals(attributes.get(OAuth2ParameterNames.REGISTRATION_ID))) {
            return;
          }
          var authentication = SecurityContextHolder.getContext().getAuthentication();
          if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new InsufficientAuthenticationException(
                SecurityErrorCode.UNAUTHORIZED.getMessage());
          }
          if (!user.isOnboardingCompleted()) {
            throw new AccessDeniedException(SecurityErrorCode.DENIED_ACCESS.getMessage());
          }
          builder.additionalParameters(parameters -> parameters.put("prompt", "select_account"));
          attributes.put(
              GitHubLinkContext.ATTRIBUTE, new GitHubLinkContext(user.getId(), Instant.now()));
        }));
    return resolver;
  }
}
