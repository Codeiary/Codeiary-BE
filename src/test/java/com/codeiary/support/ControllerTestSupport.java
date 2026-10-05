package com.codeiary.support;

import com.codeiary.global.auth.service.AuthService;
import com.codeiary.global.auth.config.SecurityConfig;
import com.codeiary.global.exception.GlobalExceptionHandler;
import com.codeiary.global.auth.service.JwtTokenService;
import com.codeiary.global.auth.exception.SecurityErrorHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@Import({SecurityConfig.class, SecurityErrorHandler.class, GlobalExceptionHandler.class})
public abstract class ControllerTestSupport {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @MockitoBean protected AuthService authService;
    @MockitoBean protected JwtTokenService jwtTokens;
}
