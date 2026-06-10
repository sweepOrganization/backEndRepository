package com.sweep.project.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweep.project.route.domain.PathSearchType;
import com.sweep.project.route.TrafficResponse;
import com.sweep.project.redis.RouteRedisService;
import com.sweep.project.route.domain.RouteDbService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 경로 검색에 Redis 분산락 + 3단계 캐싱 전략을 적용하는 AOP.
 *
 * <pre>
 * 1. 분산락 획득 (lock:route:{type}:{coords})
 * 2. Redis 캐시 조회  → 히트 시 반환
 * 3. DB 조회          → 히트 시 Redis 재캐싱 후 반환
 * 4. ODsay API 호출  → DB 저장 → Redis 캐싱 후 반환
 * 5. finally 에서 락 해제
 * </pre>
 */
@Component
@Aspect
@RequiredArgsConstructor
@Slf4j
public class RedisGeoCacheAop {

    private static final long LOCK_WAIT_SEC  = 15L;
    private static final long LOCK_LEASE_SEC = 10L;

    private final RouteRedisService routeRedisService;
    private final RouteDbService    routeDbService;
    private final RedissonClient    redissonClient;

    @Qualifier("redisObjectMapper")
    private final ObjectMapper redisObjectMapper;

    private static final TypeReference<TrafficResponse> ROUTE_TYPE = new TypeReference<>() {};

    @Around("@annotation(geoLocationCache)")
    public Object geoLocationCache(ProceedingJoinPoint joinPoint, GeoLocationCache geoLocationCache)
            throws Throwable {

        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        PathSearchType type = PathSearchType.valueOf(PathSearchType.class,
                request.getParameter("type"));
        double startLat = Double.parseDouble(request.getParameter("startLat"));
        double startLon = Double.parseDouble(request.getParameter("startLon"));
        double endLat   = Double.parseDouble(request.getParameter("endLat"));
        double endLon   = Double.parseDouble(request.getParameter("endLon"));

        String lockKey = buildLockKey(type, startLat, startLon, endLat, endLon);
        RLock  lock    = redissonClient.getLock(lockKey);
        boolean locked = lock.tryLock(LOCK_WAIT_SEC, LOCK_LEASE_SEC, TimeUnit.SECONDS);
        if (!locked) {
            log.warn("[GeoCache] 분산락 획득 실패, 요청을 처리하지 않습니다. lockKey={}", lockKey);
            return List.of();
        }

        try {
            // ── Step 1. Redis 캐시 조회 ──────────────────────────────────────
            Optional<List<TrafficResponse>> cached =
                    routeRedisService.find(type, startLat, startLon, endLat, endLon);
            if (cached.isPresent()) {
                log.info("[GeoCache] Redis 캐시 히트 type={}", type);
                return cached.get();
            }

            // ── Step 2. DB 조회 ──────────────────────────────────────────────
            List<RouteDbService.RouteWithId> dbRoutes =
                    routeDbService.findRoutes(type, startLat, startLon, endLat, endLon);

            if (!dbRoutes.isEmpty()) {
                log.info("[GeoCache] DB 히트 count={} type={}", dbRoutes.size(), type);

                List<Long>   ids   = dbRoutes.stream().map(RouteDbService.RouteWithId::id).collect(Collectors.toList());
                List<String> jsons = dbRoutes.stream().map(RouteDbService.RouteWithId::routeJson).collect(Collectors.toList());

                // DB 데이터를 Redis 에 재캐싱
                routeRedisService.saveIfAbsent(type, startLat, startLon, endLat, endLon, ids, jsons);

                // 역직렬화 후 routeId 주입 (id-json 인덱스 정렬 유지)
                List<TrafficResponse> routes = new java.util.ArrayList<>();
                for (int i = 0; i < dbRoutes.size(); i++) {
                    TrafficResponse tr = deserializeQuietly(jsons.get(i));
                    if (tr != null) {
                        tr.setRouteId(ids.get(i));
                        routes.add(tr);
                    }
                }
                //--->노션별 그래픽 데이터 캐싱부문
                return routes;
            }

            // ── Step 3. ODsay API 호출 ───────────────────────────────────────
            @SuppressWarnings("unchecked")
            List<TrafficResponse> result = (List<TrafficResponse>) joinPoint.proceed();

            List<String> routeJsonList = result.stream()
                    .map(this::serializeQuietly)
                    .collect(Collectors.toList());
            // DB 저장
            List<Long> routeIds = routeDbService.saveAll(type, startLat, startLon, endLat, endLon, routeJsonList);
            log.info("[GeoCache] DB 저장 완료 routeIds={} type={}", routeIds, type);


            // routeId 주입 (result 와 routeIds 는 인덱스 정렬)
            for (int i = 0; i < result.size() && i < routeIds.size(); i++) {
                result.get(i).setRouteId(routeIds.get(i));
            }
            /**
             *
             * 로직이 비효율적이긴한대 route자체가 identitiy라 saveall 메서드도 비효휼적 나중에 개선좀 해야될것으로보임.
             * */
            routeJsonList=result.stream()
                    .map(this::serializeQuietly)
                    .collect(Collectors.toList());

            routeDbService.updateJsons(routeIds,routeJsonList);

            //--->노션별 그래픽 데이터 캐싱부문

            // Redis 캐싱
            routeRedisService.saveIfAbsent(type, startLat, startLon, endLat, endLon, routeIds, routeJsonList);
            return result;

        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[GeoCache] 분산락 해제 lockKey={}", lockKey);
            }
        }
    }

    // ── private ───────────────────────────────────────────────────────────────

    private List<TrafficResponse> deserializeRoutes(List<String> jsons) {
        return jsons.stream()
                .map(this::deserializeQuietly)
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    private TrafficResponse deserializeQuietly(String json) {
        try {
            return redisObjectMapper.readValue(json, ROUTE_TYPE);
        } catch (JsonProcessingException e) {
            log.error("[GeoCache] route 역직렬화 실패", e);
            return null;
        }
    }

    private String serializeQuietly(Object value) {
        try {
            return redisObjectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("[GeoCache] route 직렬화 실패", e);
            return "{}";
        }
    }

    /** 분산락 키: lock:route:{type}:{startLat:.4f}:{startLon:.4f}:{endLat:.4f}:{endLon:.4f} */
    private String buildLockKey(PathSearchType type,
                                double startLat, double startLon,
                                double endLat, double endLon) {
        return String.format("lock:route:%s:%.4f:%.4f:%.4f:%.4f",
                RouteRedisService.toTypeName(type), startLat, startLon, endLat, endLon);
    }
}
