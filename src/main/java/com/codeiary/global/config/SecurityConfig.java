package com.codeiary.global.config;

import com.codeiary.global.security.handler.CustomOauth2FailureHandler;
import com.codeiary.global.security.handler.CustomOauth2SuccessHandler;
import com.codeiary.global.security.handler.SecurityErrorHandler;
import com.codeiary.global.security.service.CustomOAuth2UserService;
import com.codeiary.global.security.service.CustomOidcUserService;
import com.codeiary.global.security.token.cookie.TokenAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityErrorHandler errors,
            TokenAuthenticationFilter tokenFilter,
            CustomOAuth2UserService oauth2UserService,
            CustomOidcUserService oidcUserService,
            CustomOauth2SuccessHandler successHandler,
            CustomOauth2FailureHandler failureHandler
    ) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oauth2UserService)
                                .oidcUserService(oidcUserService))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/reissue", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/admin", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/users/me", "/api/users/nickname-availability").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/users/me/onboarding").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .anyRequest().hasAnyRole("USER", "ADMIN"))
                .addFilterBefore(tokenFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    FilterRegistrationBean<TokenAuthenticationFilter> tokenFilterRegistration(TokenAuthenticationFilter filter) {
        FilterRegistrationBean<TokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
