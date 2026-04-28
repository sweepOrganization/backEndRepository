package com.sweep.project.route.graph;

import io.opentelemetry.instrumentation.api.internal.cache.concurrentlinkedhashmap.ConcurrentLinkedHashMap;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 시간 의존적 대중교통 그래프 + Yen's K-Shortest Paths.
 *
 * <h3>노드 종류</h3>
 * <ul>
 *   <li><b>도보 구간</b>: {@link #addWalk} — 고정 소요 시간</li>
 *   <li><b>버스 구간</b>: {@link #addBus}  — 1·2차 도착 시간(traTime) + 탑승 시간</li>
 * </ul>
 *
 * <h3>버스 대기 비용</h3>
 * <pre>대기 = 버스 도착시간 - 누적 이동시간</pre>
 * 누적 시간이 traTime2를 초과하면 해당 버스 엣지는 사용 불가(∞)로 처리한다.
 *
 * <h3>알고리즘</h3>
 * Yen's K-Shortest Paths (시간 의존적 Dijkstra 기반).<br>
 * spur path 탐색 시 root_cost를 start_time으로 전달해 대기 비용을 올바르게 계산한다.
 *
 * <p>시간 단위: 초(seconds)</p>
 */
public class RouteGraph {

    private static final double INF = Double.POSITIVE_INFINITY;

    /** 인접 리스트: node id → 출발 엣지 목록 */
    private final LinkedHashMap<String, List<RouteEdge>> adj = new LinkedHashMap<>();

    // ── 그래프 구성 ─────────────────────────────────────────────────────────

    public void addNode(String id) {
        adj.putIfAbsent(id, new ArrayList<>());
    }

    /**
     * 도보 엣지 추가.
     *
     * @param from          출발 노드 ID
     * @param to            도착 노드 ID
     * @param travelSeconds 도보 소요 시간(초)
     */
    public void addWalk(String from, String to, double travelSeconds) {
        adj.computeIfAbsent(from, k -> new ArrayList<>()).add(new WalkEdge(to, travelSeconds));
        adj.putIfAbsent(to, new ArrayList<>());
    }

    /**
     * 버스 엣지 추가.
     *
     * @param from          출발 노드 ID (탑승 정류소)
     * @param to            도착 노드 ID (하차 정류소)
     * @param traTime1      현재시각 기준 1차 버스 도착까지 남은 시간(초)
     * @param traTime2      현재시각 기준 2차 버스 도착까지 남은 시간(초)
     * @param ridingSeconds 탑승 총 소요 시간(초) — 1·2차 공통
     */
    public void addBus(String from, String to,
                       double traTime1, double traTime2, double ridingSeconds) {
        adj.computeIfAbsent(from, k -> new ArrayList<>()).add(
                new BusEdge(to, traTime1, traTime2, ridingSeconds));
        adj.putIfAbsent(to, new ArrayList<>());
    }

    /**
     * 호출자가 직접 생성한 엣지 객체를 그대로 그래프에 추가한다.
     * IdentityHashMap으로 엣지 메타데이터를 관리할 때 객체 참조 일치가 필요한 경우 사용한다.
     *
     * @param from 출발 노드 ID
     * @param edge 추가할 엣지 (WalkEdge 또는 BusEdge)
     * addEdge가  completablefuture기반으로 호출중이므로 thread safe를 위해서 투입.
     */
    public  void addEdge(String from, RouteEdge edge) {
            adj.computeIfAbsent(from, k -> new ArrayList<>()).add(edge);
            adj.putIfAbsent(edge.to(), new ArrayList<>());
    }

    // ── 내부 유틸 ────────────────────────────────────────────────────────────

    /** blocked에 없는 from→to 첫 번째 엣지 반환. 없으면 null. */
    private RouteEdge pickEdge(String from, String to, Set<RouteEdge> blocked) {
        for (RouteEdge e : adj.getOrDefault(from, Collections.emptyList())) {
            if (e.to().equals(to) && !blocked.contains(e)) return e;
        }
        return null;
    }

    /**
     * 엣지 목록으로 절대 누적 시간(초)을 계산한다.
     * 탑승 불가 엣지가 있으면 {@link Double#POSITIVE_INFINITY} 반환.
     */
    private double costFromEdges(List<RouteEdge> edges) {
        double acc = 0;
        for (RouteEdge e : edges) {
            double c = e.cost(acc);
            if (c == INF) return INF;
            acc += c;
        }
        return acc;
    }

    // ── 시간 의존적 Dijkstra ─────────────────────────────────────────────────

    private record DijkstraResult(List<String> nodes, List<RouteEdge> edges, double cost) {}

    /**
     * 시간 의존적 최단 경로 탐색.
     *
     * @param source       출발 노드
     * @param target       도착 노드
     * @param blockedNodes 탐색에서 제외할 노드 집합
     * @param blockedEdges 탐색에서 제외할 엣지 집합 (객체 동일성 기준)
     * @param startTime    출발 시점 누적 시간(초) — spur path 탐색 시 root_cost를 전달
     */
    private DijkstraResult dijkstra(String source, String target,
                                    Set<String> blockedNodes, Set<RouteEdge> blockedEdges,
                                    double startTime) {
        if (blockedNodes.contains(source)) return new DijkstraResult(null, null, INF);

        Map<String, Double> dist     = new HashMap<>();
        Map<String, String> prevNode = new HashMap<>();
        Map<String, RouteEdge> prevEdge = new HashMap<>();
        dist.put(source, startTime);

        // (누적시간, 노드) 쌍 힙
        record Entry(double time, String node) implements Comparable<Entry> {
            public int compareTo(Entry o) { return Double.compare(this.time, o.time); }
        }
        PriorityQueue<Entry> pq = new PriorityQueue<>();
        pq.offer(new Entry(startTime, source));
        Set<String> visited = new HashSet<>();

        while (!pq.isEmpty()) {
            Entry curr = pq.poll();
            if (visited.contains(curr.node())) continue;
            visited.add(curr.node());
            if (curr.node().equals(target)) break;

            double acc = curr.time();
            for (RouteEdge e : adj.getOrDefault(curr.node(), Collections.emptyList())) {
                if (blockedNodes.contains(e.to()) || blockedEdges.contains(e)) continue;
                double c = e.cost(acc);
                if (c == INF) continue;
                double newAcc = acc + c;
                if (newAcc < dist.getOrDefault(e.to(), INF)) {
                    dist.put(e.to(), newAcc);
                    prevNode.put(e.to(), curr.node());
                    prevEdge.put(e.to(), e);
                    pq.offer(new Entry(newAcc, e.to()));
                }
            }
        }

        if (!dist.containsKey(target)) return new DijkstraResult(null, null, INF);

        // 경로 역추적
        List<String>    nodes = new ArrayList<>();
        List<RouteEdge> edges = new ArrayList<>();
        String cur = target;
        while (!cur.equals(source)) {
            nodes.add(cur);
            edges.add(prevEdge.get(cur));
            cur = prevNode.get(cur);
        }
        nodes.add(source);
        Collections.reverse(nodes);
        Collections.reverse(edges);

        return new DijkstraResult(nodes, edges, dist.get(target));
    }

    // ── Yen's K-Shortest Paths ───────────────────────────────────────────────

    /**
     * 총 소요시간이 {@code timeLimit} 이하인 최단 경로를 최대 {@code k}개 반환한다.
     *
     * <p>반환 목록은 비용(총 소요 초) 오름차순이다.</p>
     *
     * @param source    출발 노드 ID
     * @param target    도착 노드 ID
     * @param k         반환할 최대 경로 수
     * @param timeLimit 허용 최대 총 소요 시간(초)
     * @return 조건을 만족하는 경로 목록 (비용 오름차순). 없으면 빈 리스트.
     */
    public List<PathResult> yenKShortest(String source, String target, int k, double timeLimit) {
        DijkstraResult first = dijkstra(source, target,
                Collections.emptySet(), Collections.emptySet(), 0.0);
        if (first.nodes() == null || first.cost() > timeLimit) return Collections.emptyList();

        List<PathResult> A = new ArrayList<>();
        A.add(new PathResult(first.cost(), first.nodes(), first.edges()));

        // 후보 힙: (비용, 순번, 노드목록, 엣지목록) — 순번으로 동점 안정 정렬
        record Candidate(double cost, int seq, List<String> nodes, List<RouteEdge> edges)
                implements Comparable<Candidate> {
            public int compareTo(Candidate o) {
                int c = Double.compare(this.cost, o.cost);
                return c != 0 ? c : Integer.compare(this.seq, o.seq);
            }
        }
        PriorityQueue<Candidate> B = new PriorityQueue<>();
        Set<List<String>> seen = new HashSet<>();
        seen.add(new ArrayList<>(first.nodes()));
        int seq = 0;

        for (int ki = 1; ki < k; ki++) {
            PathResult prev     = A.get(A.size() - 1);
            List<String>    pNodes = prev.nodes();
            List<RouteEdge> pEdges = prev.edges();

            for (int i = 0; i < pNodes.size() - 1; i++) {
                String      spurNode = pNodes.get(i);
                List<String> root    = pNodes.subList(0, i + 1);

                // 루트 비용 = i번째 노드까지 누적 시간
                double rootCost = costFromEdges(pEdges.subList(0, i));
                if (rootCost == INF) continue;

                // 같은 루트를 공유하는 기존 경로의 spur 출발 엣지 차단
                Set<RouteEdge> blockedEdges = new HashSet<>();
                for (PathResult pr : A) {
                    List<String>    prNodes = pr.nodes();
                    List<RouteEdge> prEdges = pr.edges();
                    if (prNodes.size() > i
                            && prNodes.subList(0, i + 1).equals(root)
                            && prEdges.size() > i) {
                        blockedEdges.add(prEdges.get(i));
                    }
                }

                // 루트 내부 노드 차단 (spurNode 본인 제외)
                Set<String> blockedNodes = new HashSet<>(root.subList(0, root.size() - 1));

                // spur 경로 탐색 — start_time = root_cost (버스 대기 시간을 올바르게 계산)
                DijkstraResult spur = dijkstra(spurNode, target,
                        blockedNodes, blockedEdges, rootCost);
                if (spur.nodes() == null || spur.cost() > timeLimit) continue;

                // 전체 경로 조합: root 앞부분 + spur
                List<String>    totalNodes = new ArrayList<>(root.subList(0, root.size() - 1));
                List<RouteEdge> totalEdges = new ArrayList<>(pEdges.subList(0, i));
                totalNodes.addAll(spur.nodes());
                totalEdges.addAll(spur.edges());

                List<String> key = new ArrayList<>(totalNodes);
                if (seen.contains(key)) continue;
                seen.add(key);

                B.offer(new Candidate(spur.cost(), seq++, totalNodes, totalEdges));
            }

            if (B.isEmpty()) break;

            Candidate next = B.poll();
            if (next.cost() > timeLimit) break;
            A.add(new PathResult(next.cost(), next.nodes(), next.edges()));
        }

        return A;
    }
}
