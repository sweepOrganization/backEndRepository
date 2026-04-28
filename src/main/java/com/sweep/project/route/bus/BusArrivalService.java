package com.sweep.project.route.bus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweep.project.route.RouteSegment;
import com.sweep.project.route.domain.WalkSegment;
import com.sweep.project.route.dto.RequestBusArrivalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.sweep.project.route.graph.BusEdge;
import com.sweep.project.route.graph.PathResult;
import com.sweep.project.route.graph.RouteEdge;
import com.sweep.project.route.graph.RouteGraph;
import com.sweep.project.route.graph.WalkEdge;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 버스 도착 정보 조회 서비스.
 *
 * <p>providerCode에 따라 서울(4) 또는 경기도(2) BIS API를 호출하며,
 * ord가 0인 경우 {@link BusStationOrdService}를 통해 자동 조회한다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusArrivalService {

    private final BusStationOrdService busStationOrdService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Executor asyncExecutor;

    @Value("${api-key.korea-data-portal}")
    private String busApiKey;

    @Value("${bus.api.arrival.seoul-url}")
    private String seoulArrivalUrl;

    @Value("${bus.api.arrival.gbis-url}")
    private String gbisArrivalUrl;


    public List<BusArrivalInfo>bulkBusArrival(List<RequestBusArrivalInfo> requestBusArrivalInfos){

        List<CompletableFuture<BusArrivalInfo>> futures=requestBusArrivalInfos.stream()
                .map(x-> CompletableFuture.supplyAsync(()-> {
                            return getBusArrival(x.getStId(), x.getBusRouteId(), x.getOrd(), x.getProviderCode());
                        },asyncExecutor))
        .toList();
        try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();  // 반드시 await
        }
        catch (CompletionException e){
                throw new RuntimeException(e.getCause());
        }
        return futures.stream().map(x->{
            return x.join();
        }).toList();

    }


    public BusArrivalInfo getBusArrival(String stId, String busRouteId, int ord, int providerCode) {
        int resolvedOrd = (ord == 0) ? busStationOrdService.resolveOrd(providerCode, busRouteId, stId) : ord;
        log.info("[BusArrival] stId={} busRouteId={} ord={} providerCode={}", stId, busRouteId, resolvedOrd, providerCode);

        if (providerCode == 2) {
            return fetchGbisArrival(stId, busRouteId, resolvedOrd);
        }
        return fetchSeoulArrival(stId, busRouteId, resolvedOrd);
    }

    private BusArrivalInfo fetchGbisArrival(String stId, String busRouteId, int ord) {
        String url = UriComponentsBuilder.fromHttpUrl(gbisArrivalUrl)
                .queryParam("serviceKey", busApiKey)
                .queryParam("stationId", stId)
                .queryParam("routeId", busRouteId)
                .queryParam("staOrder", ord)
                .queryParam("format", "json")
                .build(false)
                .toUriString();
        log.info("[BusArrival] 경기도 url={}", url);
        return parseGbisArrivalJson(restTemplate.getForObject(URI.create(url), String.class));
    }

    private BusArrivalInfo fetchSeoulArrival(String stId, String busRouteId, int ord) {
        String url = UriComponentsBuilder.fromHttpUrl(seoulArrivalUrl)
                .queryParam("serviceKey", busApiKey)
                .queryParam("stId", stId)
                .queryParam("busRouteId", busRouteId)
                .queryParam("ord", ord)
                .build(false)
                .toUriString();
        log.info("[BusArrival] 서울 url={}", url);
        return parseBusArrivalXml(restTemplate.getForObject(URI.create(url), String.class));
    }

    private BusArrivalInfo parseGbisArrivalJson(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode msgHeader = root.path("response").path("msgHeader");
            int resultCode = msgHeader.path("resultCode").asInt(-1);

            if (resultCode != 0) {
                String msg = msgHeader.path("resultMessage").asText("결과 없음");
                log.info("[BusArrival] 경기도 정보 없음: {}", msg);
                return new BusArrivalInfo(null, null, null, null, msg, 0, null, 0);
            }

            JsonNode item = root.path("response").path("msgBody").path("busArrivalItem");

            int predictTime1 = item.path("predictTime1").asInt(0);
            int locationNo1  = item.path("locationNo1").asInt(0);
            int predictTime2 = item.path("predictTime2").asInt(0);
            int locationNo2  = item.path("locationNo2").asInt(0);

            String arrmsg1 = predictTime1 > 0
                    ? predictTime1 + "분후[" + locationNo1 + "번째 전]"
                    : "운행 정보 없음";
            String arrmsg2 = predictTime2 > 0
                    ? predictTime2 + "분후[" + locationNo2 + "번째 전]"
                    : "운행 정보 없음";

            return new BusArrivalInfo(
                    item.path("stationId").asText(),
                    item.path("stationNm1").asText(),
                    item.path("routeId").asText(),
                    item.path("routeName").asText(),
                    arrmsg1,
                    item.path("predictTimeSec1").asInt(0),
                    arrmsg2,
                    item.path("predictTimeSec2").asInt(0)
            );
        } catch (Exception e) {
            log.error("[BusArrival] 경기도 JSON 파싱 실패", e);
            throw new RuntimeException("경기도 버스 도착 정보를 파싱할 수 없습니다: " + e.getMessage(), e);
        }
    }

    private BusArrivalInfo parseBusArrivalXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            Element item = (Element) doc.getElementsByTagName("itemList").item(0);
            if (item == null) {
                String headerMsg = doc.getElementsByTagName("headerMsg").getLength() > 0
                        ? doc.getElementsByTagName("headerMsg").item(0).getTextContent().trim()
                        : "결과 없음";
                log.info("[BusArrival] 서울 정보 없음: {}", headerMsg);
                return new BusArrivalInfo(null, null, null, null, headerMsg, 0, null, 0);
            }

            return new BusArrivalInfo(
                    getText(item, "stId"),
                    getText(item, "stNm"),
                    getText(item, "busRouteId"),
                    getText(item, "rtNm"),
                    getText(item, "arrmsg1"),
                    parseIntSafe(getText(item, "traTime1")),
                    getText(item, "arrmsg2"),
                    parseIntSafe(getText(item, "traTime2"))
            );
        } catch (Exception e) {
            log.error("[BusArrival] 서울 XML 파싱 실패", e);
            throw new RuntimeException("버스 도착 정보를 파싱할 수 없습니다: " + e.getMessage(), e);
        }
    }

    private String getText(Element parent, String tagName) {
        var nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return "";
        return nodes.item(0).getTextContent();
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── Yen's K-Shortest 기반 최적 경로 탐색 ─────────────────────────────────

    /**
     * Yen's K-Shortest Paths로 시간 제한 내 최적 버스 경로를 최대 3개 반환한다.
     *
     * <h3>그래프 구조</h3>
     * <pre>
     * SRC ─[Walk(w[0])]─▶ P_0 ─[BusEdge(s)]─▶ Q_0 ─[Walk(w[1])]─▶ P_1 ─[BusEdge(s)]─▶ Q_1 ─[Walk(w[n])]─▶ DEST
     * </pre>
     * 각 버스 구간 앞뒤에 WalkEdge를 삽입해 누적 이동시간을 정확하게 계산한다.
     *
     * @param desiredArrivalTime 목적지 도착 희망 시각
     * @param segmentMap         버스 구간 맵 (key: 탑승 순번, value: 탑승 후보 목록)
     * @param walkSeconds        구간 경계별 도보 시간(초).
     *                           key 0 = 출발→첫 정류장, key i = i-1번 하차→i번 정류장, key segCount = 마지막 하차→목적지.
     *                           키가 없는 경계는 0초로 처리한다.
     * @return 비용 오름차순 최대 3개의 탑승 조합 결과
     */
    public BusArrivalCheckResult findKBestRoutes(
            LocalDateTime desiredArrivalTime,
            Map<Integer, List<BusArrivalCheckRequest.BusSegmentQuery>> segmentMap,
            Map<Integer, Integer> walkSeconds,
            int totalSeconds) {

        LocalDateTime now = LocalDateTime.now();
        long timeLimitSeconds = ChronoUnit.SECONDS.between(now, desiredArrivalTime);
        if (timeLimitSeconds <= 0 || totalSeconds > timeLimitSeconds) {
            return new BusArrivalCheckResult(Collections.emptyList());
        }

        TreeMap<Integer, List<BusArrivalCheckRequest.BusSegmentQuery>> sorted = new TreeMap<>(segmentMap);
        int segCount = sorted.size();
        Map<Integer, Integer> walks = (walkSeconds != null) ? walkSeconds : Collections.emptyMap();

        RouteGraph graph = new RouteGraph();
        IdentityHashMap<BusEdge, EdgeMeta> edgeMeta =new IdentityHashMap<>();

        /*
         * 경계 i(0~segCount) 마다 WalkEdge를 삽입하고, 각 버스 구간의 BusEdge를 연결한다.
         *
         *  SRC ─[Walk(w[0])]─▶ P_0 ─[Bus 옵션들]─▶ Q_0 ─[Walk(w[1])]─▶ P_1 ─ ...
         */
        String prevNode = "SRC";
        int boundaryIdx = 0;

        for (Map.Entry<Integer, List<BusArrivalCheckRequest.BusSegmentQuery>> entry : sorted.entrySet()) {
            // 버스 정류장 앞 도보
            String platformNode = "P_" + boundaryIdx;
            graph.addEdge(prevNode, new WalkEdge(platformNode, walks.getOrDefault(boundaryIdx, 0)));

            // 해당 구간의 모든 버스 후보 엣지
            String afterBusNode = "Q_" + boundaryIdx;

            List<CompletableFuture<Pair>> futures = entry.getValue().stream()
                    .map(x -> CompletableFuture.supplyAsync(() -> {
                        BusArrivalInfo info = getBusArrival(
                                x.getStId(), x.getBusRouteId(), x.getOrd(), x.getProviderCode());
                        BusEdge edge = new BusEdge(afterBusNode,
                                info.getTraTime1(), info.getTraTime2(),
                                x.getSectionTime() * 60.0);
                        return new Pair(edge,new EdgeMeta(entry.getKey(), x, info));
                    },asyncExecutor))
                    .collect(Collectors.toList()); // 타입 지정
            try {
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();  // 반드시 await
            }
            catch (CompletionException e){
                throw new RuntimeException(e.getCause());
            }
            futures.stream().forEach(f->{
                Pair p = f.join();
                graph.addEdge(platformNode, p.busEdge());
                edgeMeta.put(p.busEdge, p.edgeMeta());
            });

            prevNode = afterBusNode;
            boundaryIdx++;
        }

        graph.addEdge(prevNode, new WalkEdge("DEST", walks.getOrDefault(segCount, 0)));

        List<PathResult> paths = graph.yenKShortest("SRC", "DEST", 3, timeLimitSeconds);
        log.info("[findKBestRoutes] 탐색 결과 {}개", paths.size());

        List<BusArrivalCheckResult.CombinationResult> results = paths.stream()
                .map(p -> convertPath(p, edgeMeta, now, desiredArrivalTime))
                .collect(Collectors.toList());

        return new BusArrivalCheckResult(results);
    }

    private BusArrivalCheckResult.CombinationResult convertPath(
            PathResult path,
            IdentityHashMap<BusEdge, EdgeMeta> edgeMeta,
            LocalDateTime now,
            LocalDateTime desiredArrivalTime) {

        List<BusArrivalCheckResult.SegmentResult> segResults = new ArrayList<>();
        double acc = 0; // 출발(now)로부터 누적 경과 시간(초)

        for (RouteEdge edge : path.edges()) {
            if (edge instanceof WalkEdge w) {
                // 마지막 도보 — SegmentResult 추가 없이 시간만 누적
                acc += w.travelSeconds();

            } else if (edge instanceof BusEdge b) {
                EdgeMeta meta = edgeMeta.get(b);
                if (meta == null) {
                    log.warn("[convertPath] BusEdge 메타데이터 누락, 건너뜀");
                    acc += b.cost(acc);
                    continue;
                }

                double waitSec = b.waitSeconds(acc);
                int    order   = b.boardingOrder(acc);
                String arrMsg  = (order == 1)
                        ? meta.info().getArrmsg1()
                        : meta.info().getArrmsg2();

                LocalDateTime boardingTime = now.plusSeconds(Math.round(acc + waitSec));
                LocalDateTime segEndTime   = boardingTime.plusSeconds(Math.round(b.ridingSeconds()));

                segResults.add(new BusArrivalCheckResult.SegmentResult(
                        meta.segmentIndex(),
                        meta.query().getBusRouteId(),
                        meta.query().getStId(),
                        arrMsg,
                        (int) Math.round(waitSec),
                        boardingTime.toLocalTime(),
                        segEndTime.toLocalTime()
                ));

                acc += waitSec + b.ridingSeconds();
            }
        }

        LocalDateTime finalArrival = now.plusSeconds(Math.round(acc));
        long diffMinutes  = ChronoUnit.MINUTES.between(desiredArrivalTime, finalArrival);
        boolean feasible  = !finalArrival.isAfter(desiredArrivalTime);

        return new BusArrivalCheckResult.CombinationResult(
                feasible, finalArrival.toLocalTime(), diffMinutes, segResults);
    }

    public BusArrivalCheckResult findKBestForRoute(BusRoute route, LocalDateTime arrivalTime) {
        Map<Integer, List<BusArrivalCheckRequest.BusSegmentQuery>> segments = new LinkedHashMap<>();
        Map<Integer, Integer> walkSeconds = new HashMap<>();

        int busIdx = 0;
        String lastStartStopId = null;
        int pendingWalkSec = 0;
        boolean hasPendingWalk = false;

        for (RouteSegment seg : route.getSegments()) {
            if (seg instanceof WalkSegment walk) {
                pendingWalkSec = walk.getSectionTime() * 60;
                hasPendingWalk = true;
            } else if (seg instanceof BusRoute.BusSegment bus) {
                String stopId = bus.getLocalBusStationId();
                if (lastStartStopId == null) {
                    // 첫 버스 → 앞 도보 할당
                    if (hasPendingWalk) {
                        walkSeconds.put(busIdx, pendingWalkSec);
                        hasPendingWalk = false;
                    }
                } else if (!stopId.equals(lastStartStopId)) {
                    // 새 승차 지점 → 환승 도보 할당 후 인덱스 증가
                    busIdx++;
                    if (hasPendingWalk) {
                        walkSeconds.put(busIdx, pendingWalkSec);
                        hasPendingWalk = false;
                    }
                }
                BusArrivalCheckRequest.BusSegmentQuery query = new BusArrivalCheckRequest.BusSegmentQuery();
                query.setBusRouteId(bus.getLocalBusId());
                query.setStId(stopId);
                query.setOrd(bus.getStartStopOrder());
                query.setProviderCode(bus.getBusProviderCode());
                query.setSectionTime(bus.getSectionTime());
                segments.computeIfAbsent(busIdx, k -> new ArrayList<>()).add(query);
                lastStartStopId = stopId;
            }
        }
        // 마지막 버스 이후 목적지까지 도보
        if (hasPendingWalk) {
            walkSeconds.put(busIdx + 1, pendingWalkSec);
        }

        return findKBestRoutes(
                arrivalTime,
                segments,
                walkSeconds,
                route.getTotalTime() * 60);
    }


    /** BusEdge에 연결된 원본 쿼리·도착 정보 묶음. */
    private record EdgeMeta(
            int segmentIndex,
            BusArrivalCheckRequest.BusSegmentQuery query,
            BusArrivalInfo info) {}

    private record Pair(BusEdge busEdge,EdgeMeta edgeMeta){}
}
