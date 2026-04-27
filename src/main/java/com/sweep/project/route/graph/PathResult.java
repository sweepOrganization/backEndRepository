package com.sweep.project.route.graph;

import java.util.List;

/**
 * Yen's K-Shortest 탐색 결과 경로 하나.
 *
 * @param totalSeconds 출발부터 도착까지 총 소요 시간(초)
 * @param nodes        경유 노드 ID 목록 (출발~도착 순)
 * @param edges        노드 간 사용된 엣지 목록 (edges[i] = nodes[i] → nodes[i+1])
 */
public record PathResult(double totalSeconds, List<String> nodes, List<RouteEdge> edges) {

    public double totalMinutes() {
        return totalSeconds / 60.0;
    }

    /** 경로를 사람이 읽을 수 있는 형태로 설명한다. */
    public String describe() {
        if (nodes.isEmpty()) return "(빈 경로)";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("총 소요: %.0f초 (%.1f분)%n", totalSeconds, totalMinutes()));

        double acc = 0;
        for (int i = 0; i < edges.size(); i++) {
            String from = nodes.get(i);
            String to   = nodes.get(i + 1);
            RouteEdge e = edges.get(i);

            if (e instanceof WalkEdge w) {
                sb.append(String.format("  도보  : %s → %s  (%.0f초)%n", from, to, w.travelSeconds()));
                acc += w.travelSeconds();

            } else if (e instanceof BusEdge b) {
                int order  = b.boardingOrder(acc);
                double wait = b.waitSeconds(acc);
                sb.append(String.format("  버스(%d차): %s → %s  대기 %.0f초 + 탑승 %.0f초%n",
                        order, from, to, wait, b.ridingSeconds()));
                acc += wait + b.ridingSeconds();
            }
        }
        return sb.toString();
    }
}
