package com.sweep.project.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
public class InAppBrowserFilter extends OncePerRequestFilter {

    // 외부 브라우저로 열어줄 프론트 주소 (메인 = 로그인 화면)
    private static final String TARGET_HOST = "hodadak.vercel.app";

    // 구글 OAuth 진입 경로만 가로챔 (카카오는 webview에서 정상 동작하므로 제외)
    private static final String OAUTH_PATH = "/oauth2/authorization/google";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String userAgent = request.getHeader("User-Agent");

        // OAuth 진입점이 아니거나, 카톡 인앱 브라우저가 아니면 그냥 통과
        if (!path.startsWith(OAUTH_PATH) || userAgent == null || !userAgent.contains("KAKAOTALK")) {
            filterChain.doFilter(request, response);
            return;
        }

        String lowerUserAgent = userAgent.toLowerCase();

        // 안드로이드: intent 스킴으로 외부 브라우저 강제 실행 (크롬 미설치 시 fallback)
        if (lowerUserAgent.contains("android")) {
            log.info("안드로이드 카톡 인앱 브라우저 감지 - 외부 브라우저로 우회합니다");
            String fallbackUrl = URLEncoder.encode("https://" + TARGET_HOST, StandardCharsets.UTF_8);
            String intentUrl = "intent://" + TARGET_HOST
                    + "#Intent;scheme=https;S.browser_fallback_url=" + fallbackUrl + ";end";
            writeHtml(response, "<script>location.href='" + intentUrl + "';</script>");
            return;
        }

        // iOS: 외부 브라우저 강제 실행이 불가능하므로 사용자에게 직접 열도록 안내
        if (lowerUserAgent.contains("iphone") || lowerUserAgent.contains("ipad")) {
            log.info("iOS 카톡 인앱 브라우저 감지 - Safari 열기 안내 페이지를 반환합니다");
            writeHtml(response, """
                    <div style='font-family:sans-serif;padding:24px;text-align:center'>
                      <h3>Safari에서 열어주세요</h3>
                      <p>우측 하단 <b>···</b> 버튼을 누른 뒤<br>
                         <b>Safari로 열기</b>를 선택해 구글 로그인을 진행해주세요.</p>
                    </div>
                    """);
            return;
        }

        // 그 외 카톡 환경(PC 등)은 정상 진행
        filterChain.doFilter(request, response);
    }

    private void writeHtml(HttpServletResponse response, String body) throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(body);
    }
}
