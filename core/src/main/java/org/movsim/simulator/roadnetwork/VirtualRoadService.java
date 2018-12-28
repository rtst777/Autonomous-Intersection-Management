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

    // road -> roads which have vehicles that are possible to have collisions
    public static Map<Integer, List<Integer>> virtualRoadMapping;
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
    public static Map<Integer, Map<Integer, Double>> distanceOffsetDueToCollisionPoint;
    // road that is overlapping with other roads.
    public static Set<Integer> overlappingRoads;
    // the curve road which is twice the length of what it should be
    public static Set<Integer> overLengthCurve;

    public static boolean isBasedOnRoadID = false;

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

        // TODO_ethan: create method for manipulate  field instead of using public field?

        virtualRoadMapping = new HashMap<>();
        rawVirtualRoadInfo.rawVirtualRoadMapping.forEach((key, value) -> {
            List<Integer> virtualRoadsList = new ArrayList<>();
            value.forEach(listValue -> {
                virtualRoadsList.add(userIdToRoadId.get(listValue));
            });
            virtualRoadMapping.put(userIdToRoadId.get(key), virtualRoadsList);
        });

        distanceOffsetDueToCollisionPoint = new HashMap<>();
        rawVirtualRoadInfo.rawDistanceOffsetDueToCollisionPoint.forEach((key, value) -> {
            Map<Integer, Double> distanceOffsets = new HashMap<>();
            value.forEach((inner_key, inner_value) -> {
                distanceOffsets.put(userIdToRoadId.get(inner_key), inner_value);
            });
            distanceOffsetDueToCollisionPoint.put(userIdToRoadId.get(key), distanceOffsets);
        });

        overlappingRoads = new HashSet<>();
        rawVirtualRoadInfo.rawOverlappingRoads.forEach((value) -> {
            overlappingRoads.add(userIdToRoadId.get(value));
        });

        overLengthCurve = new HashSet<>();
        rawVirtualRoadInfo.rawOverLengthCurve.forEach((value) -> {
            overLengthCurve.add(userIdToRoadId.get(value));
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
            if (virtualRoadMapping.containsKey(roadID)){
                for (int virtualRoadID : virtualRoadMapping.get(roadID)){
                    roadSegment.addVirtualRoadSegments(roadNetwork.findById(virtualRoadID));
                }
            }
        }
    }

    /**
     * Transform the hostVehicle to the virtual road, where the otherVehicle resides, then compute and return the
     * distance from the host vehicle to otherVehicle on the virtual road
     *
     * @param otherVehicle the vehicle on the virtual road
     * @param hostVehicle the target vehicle
     * @return the distance from the host vehicle to otherVehicle on the virtual road
     */
    public static double getVirtualPrecedingDistance(Vehicle otherVehicle, Vehicle hostVehicle){
        if (!isBasedOnRoadID){
            System.err.println("VirtualRoadService is used without being initialized");
            System.exit(-1);
        }

        Map<Integer, Double> distanceOffsets = distanceOffsetDueToCollisionPoint.get(hostVehicle.roadSegmentId());
        if (distanceOffsets == null || distanceOffsets.isEmpty()){
            return -1.0;
        }
        else {
            Double distanceOffset = distanceOffsets.get(otherVehicle.roadSegmentId());
            if (distanceOffset == null){
                return -1.0;
            }

            double hostVehicleToVirtualRoadSegmentEnd = hostVehicle.getDistanceToRoadSegmentEnd() + distanceOffset;
            if (hostVehicleToVirtualRoadSegmentEnd < 0) {
                return -1.0;
            }

            double otherVehicleToRoadSegmentEnd = otherVehicle.getDistanceToRoadSegmentEnd();
            double hostVecPosToOtherVecPos = hostVehicleToVirtualRoadSegmentEnd - otherVehicleToRoadSegmentEnd;
            return hostVecPosToOtherVecPos;
        }
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

    // Whenever we assign a virtual preceding vehicle to host vehicle, we will cache the distance between them to avoid
    // redundant computation
    private static Map<VehiclePair, Double> virtualPrecedingDistanceCache = new HashMap<>();

    /**
     * Return the preceding distance from the host vehicle to preceding vehicle
     * The preceding distance can be virtual preceding distance (if preceding vehicle is on the virtual road)
     * or normal preceding distance (if preceding vehicle is on the same road as the host vehicle)
     *
     * @param hostVehicle the target vehicle
     * @param precedingVehicle the front vehicle
     * @return the preceding distance from the host vehicle to preceding vehicle
     */
    public static double getPrecedingDistanceConsideringVirtualRoads(Vehicle hostVehicle, Vehicle precedingVehicle) {
        if (!isBasedOnRoadID){
            System.err.println("VirtualRoadService is used without being initialized");
            System.exit(-1);
        }

        if (precedingVehicle == null) {
            return MovsimConstants.GAP_INFINITY;
        }

        return virtualPrecedingDistanceCache.getOrDefault(new VehiclePair(hostVehicle, precedingVehicle),
                precedingVehicle.getRearPosition() - hostVehicle.getFrontPosition());
    }

    public static void updatePrecedingVirtualDistance(Vehicle hostVehicle, Vehicle precedingVehicle, double distance){
        virtualPrecedingDistanceCache.put(new VehiclePair(hostVehicle, precedingVehicle), distance);
    }

}


