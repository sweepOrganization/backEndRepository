package com.sweep.project.security.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweep.project.ga4.Ga4Service;
import com.sweep.project.member.domain.Member;
import com.sweep.project.member.repository.MemberRepositoryAdvance;
import com.sweep.project.redis.RedisUserInfoService;
import com.sweep.project.security.domain.CustomUserDetail;
import com.sweep.project.util.ApiResponseUtil;
import com.sweep.project.util.jwt.JwtUtility;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import static com.sweep.project.util.jwt.TokenEnum.TOKEN_PREFIX;

@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {
    public JwtAuthFilter(RedisUserInfoService redisUserInfoService, JwtUtility jwtUtility, MemberRepositoryAdvance memberRepository, ObjectMapper objectMapper, Ga4Service ga4Service) {
        this.redisUserInfoService = redisUserInfoService;
        this.jwtUtility = jwtUtility;
        this.memberRepository = memberRepository;
        this.objectMapper = objectMapper;
        this.ga4Service = ga4Service;
    }

    private RedisUserInfoService redisUserInfoService;
    private JwtUtility jwtUtility;
    private MemberRepositoryAdvance memberRepository;
    private ObjectMapper objectMapper;
    private Ga4Service ga4Service;


    private static final String[] freePassPath = {
            "/member/logout",
            "/v3/api-docs",      // /v3 → /v3/api-docs 로 더 명확하게
            "/v3/api-docs.yaml",
            "/swagger-ui",
            "/swagger-resources",
            "/actuator"
    };
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return Arrays.stream(freePassPath).anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // Bug 1 수정: "Bearer " 접두사 제거 후 순수 토큰만 추출
        String token = jwtUtility.getTokenFromHeader(request.getHeader("Authorization"));
        if (token == null || !jwtUtility.validToken(token)) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "토큰이 없거나 유효하지 않는 토큰입니다");
            return;
        }

        Claims claims = jwtUtility.getClaims(token);
        Long memberId = claims.get("id", Long.class);

        // Bug 2, 3, 4 수정: 리프레시 로직 역전 & sendRedirect 제거
        if (jwtUtility.isExpire(token)) {
            if (redisUserInfoService.existRefreshToken(memberId)) {
                // 리프레시 토큰 있음 → 새 액세스 토큰 발급
                String newAccessToken = jwtUtility.genAccessToken(memberId);
                response.setHeader("Authorization", TOKEN_PREFIX.getValue() + newAccessToken);
            } else {
                // 리프레시 토큰 없음 → 재로그인 필요
                sendErrorResponse(response, HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다. 다시 로그인해주세요");
                return;
            }
        }

        Optional<Member> member = getMemberInfo(memberId);
        if (member.isEmpty()) {
            sendErrorResponse(response, HttpStatus.BAD_REQUEST, "없는 회원입니다");
            return;
        }

        CustomUserDetail customUserDetail = new CustomUserDetail(member.get());
        Authentication auth = new UsernamePasswordAuthenticationToken(
                customUserDetail, null, customUserDetail.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        if (redisUserInfoService.checkAndSetDailyAccess(memberId)) {
            ga4Service.sendDailyActiveUserEvent(memberId);
        }

        filterChain.doFilter(request, response);

    }


    private Optional<Member> getMemberInfo(Long memberId){
        try {
            String memberInfo = redisUserInfoService.getUserInfo(memberId);
            if (memberInfo == null) {
                Optional<Member> data = memberRepository.findById(memberId);
                if (data.isEmpty()||data.get().getDeleted()) {
                    return Optional.empty();
                }
                redisUserInfoService.setRedisUserInfo(memberId, objectMapper.writeValueAsString(data.get()));
                return data;
            } else {
                return Optional.of(objectMapper.readValue(memberInfo, Member.class));
            }
        }
        catch (JsonProcessingException e){
            return Optional.empty();
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
