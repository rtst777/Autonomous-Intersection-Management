package org.movsim.simulator.roadnetwork;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.movsim.simulator.MovsimConstants;
import org.movsim.simulator.vehicles.Vehicle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class VirtualRoadService {
    private static String virtualRoadConfigFilePath;

    private static Map<Integer, Integer> userIdToRoadId;

    // the distance offset we need to apply when transform the vehicle to the virtual road.
    // e.g.
    //  roadSeg1 * * X             (roadSeg1 is 2 unit away from collision point)
    //               *
    //               *
    //               *
    //             roadSeg2        (roadSeg2 is 3 unit away from collision point)
    //
    //  We define distanceOffsetDueToCollisionPoint[1][2] = 2 - 3 = -1
    //            distanceOffsetDueToCollisionPoint[2][1] = 3 - 2 = 1
    private static Map<Integer, Map<Integer, Double>> distanceOffsetDueToCollisionPoint;
    // the id of the (curve) road which has incorrect length -> the factor need to apply on the vehicle position on
    // that road
    private static Map<Integer, Double> problematicCurve;

    private static boolean isBasedOnRoadID = false;

    /**
     * get the virtual road config file name by using roadConfigFile.
     * e.g.
     *  if the file path of roadConfigFile is: xyz/abc/config.xodr
     *  the virtual road config file path would be: xyz/abc/config.yaml
     *
     * @param roadConfigFile
     */
    public static void setVirtualRoadConfigFilePath(File roadConfigFile){
        int index = roadConfigFile.getAbsolutePath().indexOf(".");
        virtualRoadConfigFilePath = roadConfigFile.getAbsolutePath().substring(0,index) + ".yaml";
    }

    // Must call this method before using other methods in this class
    public static void initializeVirtualRoadService(RoadNetwork roadNetwork){
        // create rawVirtualRoadInfo
        Yaml yaml = new Yaml(new Constructor(RawVirtualRoadInfo.class));
        RawVirtualRoadInfo rawVirtualRoadInfo = null;
        try (InputStream inputStream = new FileInputStream(virtualRoadConfigFilePath)) {
            rawVirtualRoadInfo = yaml.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // create userID to roadID mapping
        userIdToRoadId = new HashMap<>();
        for (final RoadSegment roadSegment : roadNetwork) {
            userIdToRoadId.put(Integer.parseInt(roadSegment.userId()), roadSegment.id());
        }

        distanceOffsetDueToCollisionPoint = new HashMap<>();
        rawVirtualRoadInfo.rawDistanceOffsetDueToCollisionPoint.forEach((key, value) -> {
            Map<Integer, Double> distanceOffsets = new HashMap<>();
            value.forEach((inner_key, inner_value) -> {
                distanceOffsets.put(userIdToRoadId.get(inner_key), inner_value);
            });
            distanceOffsetDueToCollisionPoint.put(userIdToRoadId.get(key), distanceOffsets);
        });

        problematicCurve = new HashMap<>();
        rawVirtualRoadInfo.rawProblematicCurve.forEach((key, value) -> {
            problematicCurve.put(userIdToRoadId.get(key), value);
        });

        isBasedOnRoadID = true;
    }

    /**
     * Add virtual roads (virtual road is the road containing vehicles to cause potential collision)
     * to each road
     *
     * @param roadNetwork
     */
    public static void addVirtualRoads(RoadNetwork roadNetwork){
        if (!isBasedOnRoadID){
            System.err.println("VirtualRoadService is used without being initialized");
            System.exit(-1);
        }

        for (final RoadSegment roadSegment : roadNetwork) {
            int roadID = roadSegment.id();
            if (distanceOffsetDueToCollisionPoint.keySet().contains(roadID)){
                distanceOffsetDueToCollisionPoint.get(roadID).forEach((key, value) -> {
                    roadSegment.addVirtualRoadSegments(roadNetwork.findById(key));
                });
            }
        }
    }

    /**
     * Transform the hostVehicle to the virtual road, then compute and return the distance from the host vehicle to
     * the end of the virtual road
     *
     * @param virtualRoad the virtual road where host vehicle will transform to
     * @param hostVehicle the target vehicle
     * @return the distance from the host vehicle to the end of the virtual road
     */
    public static double getPrecedingDistanceToVirtualRoad(RoadSegment virtualRoad, Vehicle hostVehicle){
        if (!isBasedOnRoadID){
            System.err.println("VirtualRoadService is used without being initialized");
            System.exit(-1);
        }

        Map<Integer, Double> distanceOffsets = distanceOffsetDueToCollisionPoint.get(hostVehicle.roadSegmentId());
        if (distanceOffsets == null || distanceOffsets.isEmpty()){
            return -1.0;
        }
        else {
            Double distanceOffset = distanceOffsets.get(virtualRoad.id());
            if (distanceOffset == null){
                return -1.0;
            }

            return hostVehicle.getDistanceToRoadSegmentEnd() + distanceOffset;
        }
    }

    // Whenever we assign a virtual preceding vehicle to host vehicle, we will cache the distance between them to avoid
    // redundant computation
    private static Map<VehiclePair, Double> virtualPrecedingDistanceCache = new HashMap<>();

    /**
     * Return the preceding distance from the host vehicle to front vehicle
     * The preceding distance can be virtual preceding distance (if front vehicle is on the virtual road)
     * or normal preceding distance (if front vehicle is on the same road as the host vehicle)
     *
     * @param hostVehicle the target vehicle
     * @param frontVehicle the front vehicle
     * @return the preceding distance from the host vehicle to preceding vehicle
     */
    public static double getPrecedingDistanceToFrontVehicle(Vehicle hostVehicle, Vehicle frontVehicle) {
        if (!isBasedOnRoadID){
            System.err.println("VirtualRoadService is used without being initialized");
            System.exit(-1);
        }

        if (frontVehicle == null) {
            return MovsimConstants.GAP_INFINITY;
        }

        return virtualPrecedingDistanceCache.getOrDefault(new VehiclePair(hostVehicle, frontVehicle),
                frontVehicle.getRearPosition() - hostVehicle.getFrontPosition());
    }

    public static void updatePrecedingVirtualDistance(Vehicle hostVehicle, Vehicle precedingVehicle, double distance){
        virtualPrecedingDistanceCache.put(new VehiclePair(hostVehicle, precedingVehicle), distance);
    }

    /**
     * If the road is problematic road, return the  factor need to apply on the vehicle's position in order to address
     * the incorrect length problem of the road; otherwise return 1
     *
     * @param roadSegmentId
     * @return the factor need to apply on the vehicle's position in order to address the incorrect length problem of the road
     */
    public static double getPositionFactor(int roadSegmentId){
        return problematicCurve.getOrDefault(roadSegmentId, 1.0);
    }

    private static class VehiclePair {
        Vehicle hostVehicle;
        Vehicle precedingVehicle;

        VehiclePair(Vehicle hostVehicle, Vehicle precedingVehicle){
            this.hostVehicle = hostVehicle;
            this.precedingVehicle = precedingVehicle;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (!(obj instanceof VehiclePair)){
                return false;
            }
            if (obj == this){
                return true;
            }

            VehiclePair rhs = (VehiclePair) obj;
            return new EqualsBuilder().
                    append(hostVehicle, rhs.hostVehicle).
                    append(precedingVehicle, rhs.precedingVehicle).
                    isEquals();
        }

        @Override
        public int hashCode()
        {
            return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
                    append(hostVehicle).
                    append(precedingVehicle).
                    toHashCode();
        }
    }

}

