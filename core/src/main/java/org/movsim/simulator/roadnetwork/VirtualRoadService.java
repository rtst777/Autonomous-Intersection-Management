package org.movsim.simulator.roadnetwork;

import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.movsim.simulator.MovsimConstants;
import org.movsim.simulator.vehicles.Vehicle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VirtualRoadService {
    private static RawVirtualRoadInfo rawVirtualRoadInfo = null;

    private static Map<String, Integer> userIdToRoadId;

    // The distance offset we need to apply when transform the vehicle to the virtual road.
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

    // The distance between the collision point to the end of road segment (i.e. the collision distance threshold).
    // If the distance from vehicle to end of segment is smaller than the collision distance threshold, the vehicle
    // can ignore this collision point.
    //
    // e.g.
    //  vehicle - - - X - -END      The distance from vehicle to end of segment is 5, the collision distance threshold
    //                              is 2. 5 > 2, so the collision point need to be considered
    //
    //  - X - - vehicle - -END      The distance from vehicle to end of segment is 2, the collision distance threshold
    //                              is 4. 2 < 4, so the collision point don't need to be considered
    private static Map<Integer, Map<Integer, Double>> collisionDistanceThreshold;

    // the id of the (curve) road which has incorrect length -> the factor need to apply on the vehicle position on
    // that road
    private static Map<Integer, Double> problematicCurve;

    private static boolean isBasedOnRoadID = false;

    // Only for debugging purpose
    public static Map<Integer, String> roadIdToUserId = new HashMap<>();

    /**
     * load the virtual road config file
     * Note:
     *  if the file path of roadConfigFile is: xyz/abc/config.xodr
     *  the virtual road config file path would be: xyz/abc/config.yaml
     *
     * @param roadConfigFile
     */
    public static void loadRawVirtualRoadConfiguration(File roadConfigFile){
        int index = roadConfigFile.getAbsolutePath().indexOf(".");
        String virtualRoadConfigFilePath = roadConfigFile.getAbsolutePath().substring(0,index) + ".yaml";

        // create rawVirtualRoadInfo if virtual road config file is present
        File virtualRoadConfigFile = new File(virtualRoadConfigFilePath);
        if (virtualRoadConfigFile.isFile() && virtualRoadConfigFile.canRead()) {
            InputStream inputStream = null;
            try {
                String template = FileUtils.readFileToString(virtualRoadConfigFile, StandardCharsets.UTF_8);
                Jinjava jinjava = new Jinjava();
                String renderedTemplate = jinjava.render(template, null);
                inputStream = new ByteArrayInputStream(renderedTemplate.getBytes(StandardCharsets.UTF_8));
                Yaml yaml = new Yaml(new Constructor(RawVirtualRoadInfo.class));
                rawVirtualRoadInfo = yaml.load(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (inputStream != null){
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Initialize the RoadService by converting the userID in rawVirtualRoadInfo (if exist) to roadID
     * Note: this method must be called before calling any other methods in this class
     *
     * @param roadNetwork
     */
    public static void initializeVirtualRoadService(RoadNetwork roadNetwork){
        userIdToRoadId = new HashMap<>();
        distanceOffsetDueToCollisionPoint = new HashMap<>();
        collisionDistanceThreshold = new HashMap<>();
        problematicCurve = new HashMap<>();

        // create rawVirtualRoadInfo if virtual road config file is present
        if (rawVirtualRoadInfo != null && roadNetwork != null) {
            // create userID to roadID mapping
            for (final RoadSegment roadSegment : roadNetwork) {
                userIdToRoadId.put(roadSegment.userId(), roadSegment.id());
                roadIdToUserId.put(roadSegment.id(), roadSegment.userId());
            }

            // replace the userID with roadID in RawVirtualRoadInfo
            if (rawVirtualRoadInfo.rawDistanceOffsetDueToCollisionPoint != null){
                rawVirtualRoadInfo.rawDistanceOffsetDueToCollisionPoint.forEach((key, value) -> {
                    Map<Integer, Double> distanceOffsets = new HashMap<>();
                    value.forEach((inner_key, inner_value) -> {
                        distanceOffsets.put(userIdToRoadId.get(inner_key), inner_value);
                    });
                    distanceOffsetDueToCollisionPoint.put(userIdToRoadId.get(key), distanceOffsets);
                });
            }

            if (rawVirtualRoadInfo.rawCollisionDistanceThreshold != null){
                rawVirtualRoadInfo.rawCollisionDistanceThreshold.forEach((key, value) -> {
                    Map<Integer, Double> distanceThresholds = new HashMap<>();
                    value.forEach((inner_key, inner_value) -> {
                        distanceThresholds.put(userIdToRoadId.get(inner_key), inner_value);
                    });
                    collisionDistanceThreshold.put(userIdToRoadId.get(key), distanceThresholds);
                });
            }

            if (rawVirtualRoadInfo.rawProblematicCurve != null) {
                rawVirtualRoadInfo.rawProblematicCurve.forEach((key, value) -> {
                    problematicCurve.put(userIdToRoadId.get(key), value);
                });
            }
        }

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

            double precedingDistanceToVirtualRoad = hostVehicle.getDistanceToRoadSegmentEnd() + distanceOffset;
            Map<Integer, Double> distanceThresholds = collisionDistanceThreshold.get(hostVehicle.roadSegmentId());
            if (distanceThresholds != null && !distanceThresholds.isEmpty()){
                Double distanceThreshold = distanceThresholds.get(virtualRoad.id());
                if (distanceThreshold != null && distanceThreshold >= hostVehicle.getDistanceToRoadSegmentEnd()){
                    // this collision point can be ignored by hostVehicle
                    return -0.1;
                }
            }

            return precedingDistanceToVirtualRoad;
        }
    }

    /**
     * Return the preceding distance from the front position of host vehicle to the rear position of front vehicle
     * The preceding distance can be virtual preceding distance (if front vehicle is on the virtual road)
     * or normal preceding distance (if front vehicle is on the same road as the host vehicle)
     *
     * @param hostVehicle the target vehicle
     * @param frontVehicle the front vehicle
     * @return the preceding distance from the front position of host vehicle to the rear position of front vehicle
     */
    public static double getPrecedingDistanceToFrontVehicle(Vehicle hostVehicle, Vehicle frontVehicle) {
        if (!isBasedOnRoadID){
            System.err.println("VirtualRoadService is used without being initialized");
            System.exit(-1);
        }

        if (frontVehicle == null) {
            return MovsimConstants.GAP_INFINITY;
        }

        double frontPositionDifference = hostVehicle.getPrecedingDistanceToFrontVehicle();
        if (frontPositionDifference < 0){
            return frontVehicle.getRearPosition() - hostVehicle.getFrontPosition();
        }
        else {
            return frontPositionDifference - frontVehicle.getLength();
        }
    }

    /**
     * If the road is problematic road, return the factor need to apply on the vehicle's position in order to address
     * the incorrect length problem of the road; otherwise return 1
     *
     * @param roadSegmentId
     * @return the factor need to apply on the vehicle's position in order to address the incorrect length problem of the road
     */
    public static double getPositionFactor(int roadSegmentId){
        return problematicCurve.getOrDefault(roadSegmentId, 1.0);
    }

    public static double getPositionFactorByUserID(String roadSegmentUserId){
        if (rawVirtualRoadInfo == null || rawVirtualRoadInfo.rawProblematicCurve == null){
            return 1.0;
        }

        return rawVirtualRoadInfo.rawProblematicCurve.getOrDefault(roadSegmentUserId, 1.0);
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


