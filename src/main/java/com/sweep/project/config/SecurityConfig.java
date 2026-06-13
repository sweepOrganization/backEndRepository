package com.sweep.project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweep.project.ga4.Ga4Service;
import com.sweep.project.member.repository.MemberRepositoryAdvance;
import com.sweep.project.redis.RedisUserInfoService;
import com.sweep.project.security.filter.InAppBrowserFilter;
import com.sweep.project.security.filter.JwtAuthFilter;
import com.sweep.project.security.handler.CustomLogOutHandler;
import com.sweep.project.security.handler.CustomOAuth2LoginFailer;
import com.sweep.project.security.handler.CustomOAuth2LoginSuccessHandler;
import com.sweep.project.security.repository.HttpCookieOAuth2AuthorizationRequestRepository;
import com.sweep.project.security.service.CustomOAuth2Service;
import com.sweep.project.util.jwt.JwtUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final MemberRepositoryAdvance memberRepository;
    private final ObjectMapper objectMapper;
    private final RedisUserInfoService redisUserInfoService;
    private final JwtUtility jwtUtility;
    private final WebConfig webConfig;
    private final Ga4Service ga4Service;

    private final static String[] freePath = {
            "/member/logout",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/actuator/**",
    };
    private final static String[] adminPath = {};

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity security) throws Exception{

        security.formLogin(login->login.disable());
        security.httpBasic(httpBasic->httpBasic.disable());
        security.csrf(csrf->csrf.disable());
        security.cors(cors->cors.configurationSource(webConfig.corsConfigurationSource()));

        security.sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 카톡 인앱 브라우저 감지 → OAuth 리다이렉트 필터보다 먼저 가로채서 외부 브라우저로 우회
        security.addFilterBefore(new InAppBrowserFilter(), OAuth2AuthorizationRequestRedirectFilter.class);

        security.addFilterAfter(new JwtAuthFilter(redisUserInfoService, jwtUtility, memberRepository, objectMapper, ga4Service)
                , UsernamePasswordAuthenticationFilter.class);

        security.logout(logout->logout.logoutUrl("/member/logout")
                .invalidateHttpSession(true)
                .logoutSuccessHandler(new CustomLogOutHandler(jwtUtility,redisUserInfoService,objectMapper)));

        security.oauth2Login(oauth2->oauth2
                .authorizationEndpoint(endpoint ->
                        endpoint.authorizationRequestRepository(
                                new HttpCookieOAuth2AuthorizationRequestRepository()))
                .userInfoEndpoint(userinfo->userinfo.userService(
                        new CustomOAuth2Service(memberRepository)))
                .successHandler(new CustomOAuth2LoginSuccessHandler(jwtUtility, redisUserInfoService, objectMapper, ga4Service))
                .failureHandler(new CustomOAuth2LoginFailer(objectMapper))
        );

        security.authorizeHttpRequests(auth ->
                auth.requestMatchers(freePath).permitAll()
                        .anyRequest().authenticated());

        return security.build();
    }
}