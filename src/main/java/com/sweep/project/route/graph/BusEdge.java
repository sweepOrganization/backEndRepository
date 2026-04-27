package com.sweep.project.route.graph;

/**
 * 버스 구간 엣지.
 *
 * <p>대기 비용 = 도착시간 - 누적시간 (음수이면 해당 버스 놓침).</p>
 * <ul>
 *   <li>누적 ≤ traTime1  →  1차 버스: 대기 = traTime1 - acc</li>
 *   <li>누적 ≤ traTime2  →  2차 버스: 대기 = traTime2 - acc</li>
 *   <li>누적 > traTime2  →  불가 ({@link Double#POSITIVE_INFINITY})</li>
 * </ul>
 * 탑승 시간(ridingSeconds)은 1·2차 동일하게 적용된다.
 *
 * @param to             도착 노드 ID
 * @param traTime1       1차 버스 도착까지 남은 시간(초, 현재시각 기준)
 * @param traTime2       2차 버스 도착까지 남은 시간(초, 현재시각 기준)
 * @param ridingSeconds  탑승 총 소요 시간(초)
 */
public record BusEdge(
        String to,
        double traTime1,
        double traTime2,
        double ridingSeconds
) implements RouteEdge {

    @Override
    public double cost(double accumulatedSeconds) {
        if (accumulatedSeconds <= traTime1) {
            return (traTime1 - accumulatedSeconds) + ridingSeconds;
        }
        if (accumulatedSeconds <= traTime2) {
            return (traTime2 - accumulatedSeconds) + ridingSeconds;
        }
        return Double.POSITIVE_INFINITY;  // 2차 버스도 놓침
    }

    /** 누적 시간 기준으로 탑승하는 버스 차수 (1 또는 2). 탑승 불가이면 -1. */
    public int boardingOrder(double accumulatedSeconds) {
        if (accumulatedSeconds <= traTime1) return 1;
        if (accumulatedSeconds <= traTime2) return 2;
        return -1;
    }

    /** 실제 대기 시간(초). 탑승 불가이면 {@link Double#POSITIVE_INFINITY}. */
    public double waitSeconds(double accumulatedSeconds) {
        if (accumulatedSeconds <= traTime1) return traTime1 - accumulatedSeconds;
        if (accumulatedSeconds <= traTime2) return traTime2 - accumulatedSeconds;
        return Double.POSITIVE_INFINITY;
    }
}
