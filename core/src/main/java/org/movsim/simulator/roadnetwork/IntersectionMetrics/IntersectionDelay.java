package org.movsim.simulator.roadnetwork.IntersectionMetrics;

/**
 * Intersection delay (measured in s/veh) is the difference between the time a vehicle takes to reach the exit of the
 * network and the minimum time it could possibly take, tideal, which is the time the vehicle would spend if it
 * was the only vehicle in the network and it could always pass intersections without impediment.
 * */
// TODO(ethan)
public class IntersectionDelay implements IntersectionMetrics {

    @Override
    public String getName() {
        return null;
    }

    @Override
    public Number getValue() {
        return null;
    }

    @Override
    public void record(Number value, Integer roadID) {

    }
}
