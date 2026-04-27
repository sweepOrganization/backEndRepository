package com.sweep.project.route.graph;

/**
 * 그래프 엣지 타입. 도보(WalkEdge)와 버스(BusEdge) 두 가지를 허용한다.
 * 비용 단위: 초(seconds).
 */
public sealed interface RouteEdge permits WalkEdge, BusEdge {

    String to();

    /**
     * @param accumulatedSeconds 현재까지 누적된 이동 시간(초)
     * @return 이 엣지를 통과하는 데 드는 추가 비용(초). 버스를 탈 수 없으면 {@link Double#POSITIVE_INFINITY}.
     */
    double cost(double accumulatedSeconds);
}
