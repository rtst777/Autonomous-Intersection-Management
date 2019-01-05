package org.movsim.simulator.roadnetwork;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage for all the raw information about the virtual roads. It is raw information because it is using userID
 * instead of roadID.
 */
public class RawVirtualRoadInfo {
    // The values we load from yaml file is userID, we need to transform the userID to roadID
    public Map<String, Map<String, Double>> rawDistanceOffsetDueToCollisionPoint;
    public Map<String, Map<String, Double>> rawCollisionDistanceThreshold;
    public Map<String, Double> rawProblematicCurve;
    public Map<String, List<String>> rawOverlappingRoads;
    public double controlZoneThreshold;
    // metrics name -> list of intersection roads
    public Map<String, List<String>> rawIntersectionThroughput;
    public Map<String, List<String>> rawIntersectionDelay;
    public int levelOfAncestorVehicle;
    public double metricsDisplayX;
    public double metricsDisplayY;
    public int vehicleIdFont;
    public int metricsFont;
}
