package org.movsim.simulator.roadnetwork.IntersectionMetrics;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * We define intersection throughput as the total number of vehicles passing the intersection and the average
 * intersection throughput as the number of vehicles passing the intersection per second
 * */
// FIXME movsim intentionally limit the maximum number of vehicle can exist in the road network, which limit maximum throughput
public class IntersectionThroughput implements IntersectionMetrics{

    private final String metricsName;
    private Set<Integer> intersectionRoads;
    // TODO(ethan) replace long with AtomicLong if there is concurrent access
    private long totalNumVehPassIntersection;

    public IntersectionThroughput(String metricsName, List<Integer> intersectionRoads){
        this.metricsName = metricsName;
        this.intersectionRoads = new HashSet<>(intersectionRoads);
        this.totalNumVehPassIntersection = 0;
    }

    @Override
    public String getName() {
        return metricsName;
    }

    @Override
    public Number getValue() {
        return totalNumVehPassIntersection;
    }

    @Override
    public Number getAverageValue(Number simulationTimeInSec) {
        return totalNumVehPassIntersection / simulationTimeInSec.doubleValue();
    }

    @Override
    public void record(Number value, Integer roadID) {
        if (intersectionRoads.contains(roadID)) {
            totalNumVehPassIntersection += value.longValue();
        }
    }
}
