package com.sweep.project.route.graph;

/**
 * 도보 구간 엣지. 누적 시간에 관계없이 travelSeconds만큼 소요된다.
 */
public record WalkEdge(String to, double travelSeconds) implements RouteEdge {

    @Override
    public double cost(double accumulatedSeconds) {
        return travelSeconds;
    }
}
