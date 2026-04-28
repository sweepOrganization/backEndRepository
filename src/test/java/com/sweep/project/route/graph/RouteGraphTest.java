package com.sweep.project.route.graph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
//@SpringBootTest
class RouteGraphTest {

    /**
     * 테스트 그래프 구조:
     *
     *  집 --도보5분--> A정류장 --버스(1차8분,2차20분,탑승12분)--> 목적지
     *  집 --도보10분-> B정류장 --버스(1차15분,2차30분,탑승7분)--> 목적지
     *  A정류장 --도보7분--> B정류장
     *  A정류장 --도보20분-> 목적지
     *
     * 모든 시간 단위: 초(seconds). 이 테스트에서는 가독성을 위해 분=60초로 입력.
     */
    private RouteGraph buildSampleGraph() {
        RouteGraph g = new RouteGraph();
        g.addWalk("집", "A정류장", 5 * 60);
        g.addWalk("집", "B정류장", 10 * 60);
        g.addWalk("A정류장", "B정류장", 7 * 60);
        g.addWalk("A정류장", "목적지", 20 * 60);
        // 버스: (from, to, traTime1, traTime2, ridingSeconds)
        g.addBus("A정류장", "목적지", 8 * 60, 20 * 60, 12 * 60);
        g.addBus("B정류장", "목적지", 15 * 60, 30 * 60, 7 * 60);
        return g;
    }

    @Test
    @DisplayName("K=3, timeLimit 내 경로 탐색 — 비용 오름차순 반환")
    void kShortest_returnsUpToKPathsWithinLimit() {
        RouteGraph g = buildSampleGraph();
        double limitSec = 40 * 60; // 40분

        List<PathResult> results = g.yenKShortest("집", "목적지", 3, limitSec);

        assertThat(results).isNotEmpty();
        assertThat(results.size()).isLessThanOrEqualTo(3);

        // 비용 오름차순 검증
        for (int i = 1; i < results.size(); i++) {
            assertThat(results.get(i).totalSeconds())
                    .isGreaterThanOrEqualTo(results.get(i - 1).totalSeconds());
        }

        // 모든 경로가 timeLimit 이내
        results.forEach(r ->
                assertThat(r.totalSeconds()).isLessThanOrEqualTo(limitSec));

        // 결과 출력
        for (int i = 0; i < results.size(); i++) {
            System.out.printf("%n=== %d위 경로 ===%n", i + 1);
            System.out.print(results.get(i).describe());
        }
    }

    @Test
    @DisplayName("timeLimit 너무 작으면 빈 목록 반환")
    void kShortest_emptyWhenNoFeasiblePath() {
        RouteGraph g = buildSampleGraph();

        List<PathResult> results = g.yenKShortest("집", "목적지", 3, 1 * 60); // 1분

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("누적 시간이 1차 버스를 놓치면 2차 버스로 대기 시간 재계산")
    void busEdge_usesSecondArrivalWhenFirstMissed() {
        // 집에서 B정류장까지 10분 도보 → traTime1=8분 놓침 → 2차(15분) 대기
        // 대기 = 15*60 - 10*60 = 5분 = 300초
        // 총 = 10*60(도보) + 300(대기) + 7*60(탑승) = 600+300+420 = 1320초 = 22분
        RouteGraph g = new RouteGraph();
        g.addWalk("집", "B정류장", 10 * 60);
        g.addBus("B정류장", "목적지", 8 * 60, 15 * 60, 7 * 60);

        List<PathResult> results = g.yenKShortest("집", "목적지", 1, Double.MAX_VALUE);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).totalSeconds()).isEqualTo(1320.0);
        System.out.print(results.get(0).describe());
    }

    @Test
    @DisplayName("2차 버스도 놓치면 해당 버스 경로 불가 처리")
    void busEdge_infiniteWhenBothMissed() {
        // 집에서 A까지 30분 도보 → traTime2=20분이므로 둘 다 놓침
        RouteGraph g = new RouteGraph();
        g.addWalk("집", "A정류장", 30 * 60);
        g.addBus("A정류장", "목적지", 8 * 60, 20 * 60, 12 * 60);

        List<PathResult> results = g.yenKShortest("집", "목적지", 1, Double.MAX_VALUE);

        assertThat(results).isEmpty();
    }
}
