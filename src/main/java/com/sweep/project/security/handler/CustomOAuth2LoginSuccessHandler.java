package com.sweep.project.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweep.project.ga4.Ga4Service;
import com.sweep.project.redis.RedisUserInfoService;
import com.sweep.project.security.domain.CustomOAuth2User;
import com.sweep.project.util.ApiResponseUtil;
import com.sweep.project.util.jwt.JwtUtility;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import java.io.IOException;

import static com.sweep.project.util.jwt.TokenEnum.TOKEN_PREFIX;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
public class CustomOAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private JwtUtility jwtUtility;
    private RedisUserInfoService redisUserInfoService;
    private ObjectMapper objectMapper;
    private Ga4Service ga4Service;

    public CustomOAuth2LoginSuccessHandler(JwtUtility jwtUtility,
                                           RedisUserInfoService redisUserInfoService, ObjectMapper objectMapper,
                                           Ga4Service ga4Service) {
        this.jwtUtility = jwtUtility;
        this.redisUserInfoService = redisUserInfoService;
        this.objectMapper = objectMapper;
        this.ga4Service = ga4Service;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        try {
            CustomOAuth2User customOAuth2User = (CustomOAuth2User) authentication.getPrincipal();

            String accessToken = jwtUtility.genAccessToken(customOAuth2User.getId());
            String refreshToken = jwtUtility.genRefreshToken(customOAuth2User.getId());
            String member = objectMapper.writeValueAsString(customOAuth2User.getMember());
            redisUserInfoService.setLoginUserInfo(customOAuth2User.getId(), member, refreshToken);

            if (redisUserInfoService.checkAndSetDailyAccess(customOAuth2User.getId())) {
                ga4Service.sendDailyActiveUserEvent(customOAuth2User.getId());
            }

     
            //response.addHeader(AUTHORIZATION, TOKEN_PREFIX.getValue() + accessToken);
            //response.sendRedirect("https://hodadak.vercel.app/oauth2/callback?token=" + accessToken);
            //response.sendRedirect("http://localhost:5173/oauth2/callback?token="+accessToken);
            response.sendRedirect("https://hodadak.vercel.app/oauth2/callback?token=" + accessToken);
            log.info("{} 유저에대한 로그인이 정상적으로 되었습니다", customOAuth2User.getEmail());
        }
        catch (Exception e){
            log.error("oauth2 로그인 성공중:{}",e.getMessage());
            sendErrorResponse(response, HttpStatus.BAD_REQUEST,e.getMessage());
        }
    }

    private void sendErrorResponse(HttpServletResponse response, HttpStatus httpStatus, String message) throws
            IOException {
        response.setStatus(httpStatus.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponseUtil.FailApiResponse(message));
    }
}