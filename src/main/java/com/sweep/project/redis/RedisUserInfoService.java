package com.sweep.project.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
@Slf4j
public class RedisUserInfoService {

    private final RedisTemplate<String,String> redisTemplate;
    private final static String userInfoKey="member-info-key-";
    private final static String userRefreshTokenKey="member-refresh-key-";
    private final static String userDailyAccessKey="member-daily-access-";
    private final StringRedisTemplate stringRedisTemplate;

    public void setLoginUserInfo(Long id,String member,String refreshToken){
        stringRedisTemplate.execute(new DefaultRedisScript<>(RedisLuaScript.setLoginUserInfo),
                List.of(userInfoKey+id,userRefreshTokenKey+id)
                ,member,refreshToken,String.valueOf(TimeUnit.DAYS.toSeconds(30L)));
    }
    public void logOutUserInfo(Long id){
        stringRedisTemplate.execute(new DefaultRedisScript<>(RedisLuaScript.logOutUserInfo),
                List.of(userInfoKey+id,userRefreshTokenKey+id));
    }
    public void setRedisUserInfo(Long id,String member){
        String key=userInfoKey+id;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            redisTemplate.opsForValue().set(key, member, ttl, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, member,TimeUnit.DAYS.toSeconds(30L),TimeUnit.SECONDS);
        }
    }

    public String getUserInfo(Long id){
        return redisTemplate.opsForValue().get(userInfoKey+id);
    }

    public void saveRefreshToken(Long memberId,String refreshToken){
        redisTemplate.opsForValue().set(userRefreshTokenKey+memberId,refreshToken,30,TimeUnit.DAYS);
    }
    public Boolean existRefreshToken(Long memberId){
        return  redisTemplate.opsForValue().get(userRefreshTokenKey+memberId)!=null;
    }

    /**
     * 오늘 날짜 기준으로 접속 기록이 없으면 Redis에 캐싱 후 true 반환.
     * 이미 기록이 있으면 false 반환.
     */
    public boolean checkAndSetDailyAccess(Long memberId) {
        String key = userDailyAccessKey + memberId + "-" + LocalDate.now();
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", 1, TimeUnit.DAYS);
        return Boolean.TRUE.equals(isNew);
    }

}
