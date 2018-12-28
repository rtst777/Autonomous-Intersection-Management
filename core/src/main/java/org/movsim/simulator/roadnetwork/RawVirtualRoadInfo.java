package org.movsim.simulator.roadnetwork;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage for all the raw information about the virtual roads. It is raw information because it is using userID
 * instead of roadID. RawVirtualRoadInfo should only be used to initialize VirtualRoadService.
 */
public class RawVirtualRoadInfo {
    // The values we load from yaml file is userID, we need to transform the userID to roadID
    public Map<Integer, Map<Integer, Double>> rawDistanceOffsetDueToCollisionPoint;
    public Map<Integer, Double> rawProblematicCurve;
}
