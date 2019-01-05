package org.movsim.simulator.roadnetwork.IntersectionMetrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Intersection delay is the difference between the time a vehicle takes to reach the exit of the road network and the
 * minimum time it could possibly take, which is the time the vehicle would spend if it was the only vehicle in the
 * road network and it could always pass intersections without impediment. This metrics record the total delay across
 * all the vehicles
 * */
public class IntersectionDelay implements IntersectionMetrics {

    private final String metricsName;
    private Set<Integer> intersectionRoads;
    // TODO(ethan) replace double with threadsafe data if there is concurrent access
    private double totalDelayTime;

    public IntersectionDelay(String metricsName, List<Integer> intersectionRoads){
        this.metricsName = metricsName;
        this.intersectionRoads = new HashSet<>(intersectionRoads);
        this.totalDelayTime = 0;
    }

    @Override
    public String getName() {
        return metricsName;
    }

    @Override
    public Number getValue() {
        return totalDelayTime;
    }

    @Override
    public Number getAverageValue(Number numVehicle) {
        numVehicle = numVehicle.intValue() == 0 ? 1 : numVehicle;  // to avoid divide by 0
        return totalDelayTime / numVehicle.intValue();
    }

    @Override
    public void record(Number value, Integer roadID) {
        if (intersectionRoads.contains(roadID)) {
            totalDelayTime += value.doubleValue();
        }
    }
}
