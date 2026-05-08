package com.sweep.project.redis;


import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
/**
 * keyexpiration eventmessgelistener를 직접 상속받응 애라서 redis에 특별히 설정파일을 건들 이유도없고
 * 자동으로 redis container에 등록됨.
 *
 *  ex) key가 alarm-1-10-prepare-start-token-0 이라면
 *  => prepareStart = true, remainingMinutes = null 임
 *
 *  ex) key가 alarm-1-10-prepare-start-token-1 이라면
 *  => prepareStart = false, remainingMinutes = 20
 *
 *  ex) key가 alarm-1-10-prepare-before-10-token-10000 이라면
 *  => prepareStart = true, remainingMinutes = 10
 *
 * */
@Component
@Slf4j
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {

    private final String prefixKey="alarm-";
    private static final String EXCHANGE    = "alarmExchange";
    private static final String ROUTING_KEY = "alarm";
    private final RabbitTemplate rabbitTemplate;

    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer,
                                      RabbitTemplate rabbitTemplate) {
        super(listenerContainer);
        this.rabbitTemplate=rabbitTemplate;
    }
    // redis key는 alarm-memberId-alarmId--type-token-idx(준비알람의 경우 들어가는값-->1개의 알람 id에 대해서 여러개이므로) 으로 구성될것임.
    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("expired key:{}",message.toString());
        if(message.toString().startsWith(prefixKey)){
           /* String [] keys=message.toString().split("-");
            com.sweep.project.redis.RedisMessageDto redisMessageDto = new RedisMessageDto(
                    keys[1],keys[2],keys[3], keys[4] ); */
            String key = message.toString();
            String[] parts = key.split("-");

            String memberId = parts[1];
            String alarmId = parts[2];
            String alarmType = parts[3];
            Integer remainingMinutes = null;
            Boolean prepareStart = false;
            int tokenStartInclusive = 4;

            int tokenEndExclusive = parts.length;
            if ("prepare".equals(alarmType) && parts.length > 5) {
                tokenEndExclusive = parts.length - 1; // 마지막 prepare index 제거
                if ("start".equals(parts[4])) {
                    prepareStart = true;
                    tokenStartInclusive = 5;
                } else if (parts.length > 6 && "remain".equals(parts[4])) {
                    remainingMinutes = Integer.parseInt(parts[5]);
                    tokenStartInclusive = 6;
                } else if (parts.length > 6 && "before".equals(parts[4])) {
                    // prepare-before는 "N분 후에 준비해야 해요" 문구에 사용한다.
                    prepareStart = true;
                    remainingMinutes = Integer.parseInt(parts[5]);
                    tokenStartInclusive = 6;
                }
            }

            String token = String.join(
                    "-",
                    java.util.Arrays.copyOfRange(parts, tokenStartInclusive, tokenEndExclusive)
            );

            RedisMessageDto redisMessageDto = new RedisMessageDto(
                    memberId, alarmId, alarmType, token, remainingMinutes, prepareStart
            );
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY,redisMessageDto, msg -> {
                msg.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
                return msg;
            });

        }
    }
}
