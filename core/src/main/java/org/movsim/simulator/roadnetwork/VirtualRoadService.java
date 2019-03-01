package org.movsim.simulator.roadnetwork;

import com.google.common.base.Preconditions;
import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.movsim.simulator.MovsimConstants;
import org.movsim.simulator.roadnetwork.IntersectionMetrics.IntersectionDelay;
import org.movsim.simulator.roadnetwork.IntersectionMetrics.IntersectionMetrics;
import org.movsim.simulator.roadnetwork.IntersectionMetrics.IntersectionThroughput;
import org.movsim.simulator.roadnetwork.controller.TrafficLight;
import org.movsim.simulator.roadnetwork.controller.TrafficLightController;
import org.movsim.simulator.vehicles.Vehicle;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import javax.lang.model.type.IntersectionType;
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
    //                              is 2; 5 > 2, so the collision point need to be considered
    //
    //  - X - - vehicle - -END      The distance from vehicle to end of segment is 2, the collision distance threshold
    //                              is 4; 2 < 4, so the collision point don't need to be considered
    private static Map<Integer, Map<Integer, Double>> collisionDistanceThreshold;

    // The id of the (curve) road which has incorrect length -> the factor need to apply on the length of that road
    private static Map<Integer, Double> problematicCurve;

    // id of road -> ids of the roads that are overlapping with that road
    private static Map<Integer, Set<Integer>> overlappingRoads;

    // for all the (long straight) entry roads, we consider two different cases when doing target assignment:
    //   case 1: if the distance from vehicle to end of the road is greater than controlZoneThreshold, we only consider
    //           the overlapping roads to find the closet front vehicle
    //   case 2: if the distance from vehicle to end of the road is smaller than (or equal to) the controlZoneThreshold,
    //           we would consider all the virtual roads to find the closet front vehicle
    //
    // for other roads, the target assignment process should be unaffected
    // Note: controlZoneThreshold has to be smaller than the length of the entry roads, but greater than intersection
    //       dimension
    private static double controlZoneThreshold = -1;

    private static List<IntersectionThroughput> intersectionThroughputMetrics;
    private static List<IntersectionDelay> intersectionDelaysMetrics;
    // The coordinates where we display the metrics value on the UI. The values should not be displayed when there is
    // no metrics registered
    private static double metricsDisplayX;
    private static double metricsDisplayY;

    // for debugging purpose
    private static int vehicleIdFont = 9;
    private static int metricsFont = 20;

    // e.g. if levelOfAncestorVehicle = 2, we only consider the front vehicle of the current vehicle and the front vehicle
    // of the front vehicle of the current vehicle as the ancestor vehicle of the current vehicle
    private static int levelOfAncestorVehicle = 4;

    private static boolean isInitialized = false;

    // Only for debugging purpose
    public static Map<Integer, String> roadIdToUserId = new HashMap<>();

    private static PedestrianService pedestrianService;

    public static int getLevelOfAncestorVehicle() {
        return levelOfAncestorVehicle;
    }

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
     * Initialize the RoadService, meanwhile converting the userID in rawVirtualRoadInfo (if exist) to roadID
     *
     * @param roadNetwork
     */
    public static void initializeVirtualRoadService(RoadNetwork roadNetwork){
        userIdToRoadId = new HashMap<>();
        distanceOffsetDueToCollisionPoint = new HashMap<>();
        collisionDistanceThreshold = new HashMap<>();
        overlappingRoads = new HashMap<>();
        problematicCurve = new HashMap<>();
        intersectionThroughputMetrics = new ArrayList<>();
        intersectionDelaysMetrics = new ArrayList<>();

        // create rawVirtualRoadInfo if virtual road config file is present
        if (rawVirtualRoadInfo != null && roadNetwork != null){
            // create userID to roadID mapping
            for (final RoadSegment roadSegment : roadNetwork){
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
                    value.forEach((innerKey, innerValue) -> {
                        distanceThresholds.put(userIdToRoadId.get(innerKey), innerValue);
                    });
                    collisionDistanceThreshold.put(userIdToRoadId.get(key), distanceThresholds);
                });
            }

            if (rawVirtualRoadInfo.rawProblematicCurve != null){
                rawVirtualRoadInfo.rawProblematicCurve.forEach((key, value) -> {
                    problematicCurve.put(userIdToRoadId.get(key), value);
                });
            }

            if (rawVirtualRoadInfo.rawOverlappingRoads != null){
                rawVirtualRoadInfo.rawOverlappingRoads.forEach((key, value) -> {
                    Set<Integer> overlappingRoadList = new HashSet<>();
                    value.forEach(overlappingRoad -> {
                        overlappingRoadList.add(userIdToRoadId.get(overlappingRoad));
                    });
                    overlappingRoads.put(userIdToRoadId.get(key), overlappingRoadList);
                });
            }

            if (rawVirtualRoadInfo.controlZoneThreshold != 0){
                controlZoneThreshold = rawVirtualRoadInfo.controlZoneThreshold;
            }

            if (rawVirtualRoadInfo.rawIntersectionThroughput != null){
                rawVirtualRoadInfo.rawIntersectionThroughput.forEach((key, value) -> {
                    List<Integer> intersectionRoads = new ArrayList<>();
                    value.forEach(roadID -> intersectionRoads.add(userIdToRoadId.get(roadID)));
                    intersectionThroughputMetrics.add(new IntersectionThroughput(key, intersectionRoads));
                });
            }

            if (rawVirtualRoadInfo.rawIntersectionDelay != null){
                rawVirtualRoadInfo.rawIntersectionDelay.forEach((key, value) -> {
                    List<Integer> intersectionRoads = new ArrayList<>();
                    value.forEach(roadID -> intersectionRoads.add(userIdToRoadId.get(roadID)));
                    intersectionDelaysMetrics.add(new IntersectionDelay(key, intersectionRoads));
                });
            }

            if (rawVirtualRoadInfo.levelOfAncestorVehicle > 0){
                levelOfAncestorVehicle = rawVirtualRoadInfo.levelOfAncestorVehicle;
            }

            metricsDisplayX = rawVirtualRoadInfo.metricsDisplayX;
            metricsDisplayY = rawVirtualRoadInfo.metricsDisplayY;
            if (rawVirtualRoadInfo.vehicleIdFont != 0){
                vehicleIdFont = rawVirtualRoadInfo.vehicleIdFont;
            }
            if (rawVirtualRoadInfo.metricsFont != 0){
                metricsFont = rawVirtualRoadInfo.metricsFont;
            }
        }

        isInitialized = true;
    }

    private static void checkInitialization(){
        if (!isInitialized){
            System.err.println("VirtualRoadService is used without being initialized");
            System.exit(-1);
        }
    }

    /**
     * Add virtual roads (virtual road is other road containing vehicles to cause potential collision with the vehicle
     * on the current road) and overlapping roads (overlapping road is other road that is overlapping with the current
     * road) to every road
     *
     * @param roadNetwork
     */
    public static void addVirtualRoadsAndOverlappingRoads(RoadNetwork roadNetwork){
        checkInitialization();

        for (final RoadSegment roadSegment : roadNetwork) {
            int roadID = roadSegment.id();
            if (distanceOffsetDueToCollisionPoint.keySet().contains(roadID)){
                distanceOffsetDueToCollisionPoint.get(roadID).forEach((key, value) -> {
                    roadSegment.addVirtualRoadSegments(roadNetwork.findById(key));
                });
            }

            if (overlappingRoads.keySet().contains(roadID)){
                overlappingRoads.get(roadID).forEach(overlappingRoadID -> {
                    roadSegment.addOverLappingRoadSegments(roadNetwork.findById(overlappingRoadID));
                });
            }
        }
    }

    /**
     * initialize pedestrian service if rawVirtualRoadInfo.rawPedestrianInfo is not null
     *
     * @param trafficLightControllers
     */
    public static void initializePedestrianService(List<TrafficLightController> trafficLightControllers){
        if (rawVirtualRoadInfo.rawPedestrianInfo != null){
            Map<String, Set<Integer>> signalIdToRoadIds = new HashMap<>();
            Set<String> assistantTrafficLights = new HashSet<>();
            Map<String, String> signalIdToDirection = new HashMap<>();
            rawVirtualRoadInfo.rawPedestrianInfo.forEach((key, value) -> {
                PedestrianService.validateDirectionString(key);

                Set<Integer> roads = new HashSet<>();
                value.forEach(trafficLightSignalId -> {
                        roads.add(userIdToRoadId.get(PedestrianService.extractRoadUserId(trafficLightSignalId)));
                        if (!trafficLightSignalId.equals(key)){
                            assistantTrafficLights.add(trafficLightSignalId);
                        }
                });
                signalIdToRoadIds.put(key, roads);
                signalIdToDirection.put(PedestrianService.extractDirection(key), key);
            });

            Map<String, TrafficLightController> signalIdToTrafficLightControllers = new HashMap<>();
            trafficLightControllers.forEach(trafficLightController -> {
                if (signalIdToDirection.containsKey(trafficLightController.groupId())){
                    signalIdToTrafficLightControllers.put(
                            signalIdToDirection.get(trafficLightController.groupId()), trafficLightController);
                }
            });

            pedestrianService = new PedestrianService(signalIdToRoadIds, assistantTrafficLights, signalIdToTrafficLightControllers);
        }
    }

    public static boolean isPedestrianServiceEnabled(){
        return pedestrianService != null;
    }

    /**
     * Return true if the trafficLight is the assistant traffic light in the pedestrian service or master traffic light
     * in the pedestrian service but is not in GREEN status
     *
     * @param trafficLight
     * @return true if the trafficLight is the assistant traffic light in the pedestrian service
     */
    public static boolean ifSkipTrafficLightMouseEvent(TrafficLight trafficLight){
        if (pedestrianService == null){
            return false;
        }

        return pedestrianService.isAssistantTrafficLight(trafficLight) ||
                !pedestrianService.isPedestrianCrossingRequestAccepted(trafficLight);
    }

    /**
     * Return true if the virtualRoad is waiting for pedestrian and is not overlapping road of the host road
     *
     * @param virtualRoadId
     * @param hostRoadID
     * @return true if the roads or its overlapping road is waiting for pedestrian
     */
    public static boolean isNonOverlappingRoadWaitingForPedestrian(int virtualRoadId, int hostRoadID){
        if (pedestrianService == null){
            return false;
        }

        if (!pedestrianService.isRoadWaitingForPedestrian(virtualRoadId)){
            return false;
        }

        if (overlappingRoads.containsKey(hostRoadID)){
            if (overlappingRoads.get(hostRoadID).contains(virtualRoadId)){
                return false;
            }
        }

        return true;
    }

    /**
     * When pedestrianService exists, we disable brake light so that the UI would be more clear
     *
     * @return true if pedestrianService exists
     */
    public static boolean disableBrakeLight(){
        return pedestrianService != null;
    }

    /**
     * Handle pedestrian crossing request by triggering traffic light signal
     *
     * @param diresction
     * @return true if the request is accepted
     */
    public static boolean handlePedestrianCrossingRequest(PedestrianService.Direction diresction){
        return pedestrianService.handlePedestrianCrossingRequest(diresction);
    }

    /**
     * Return true if vehicle is inside the control zone, otherwise false.
     *   - The vehicle is always in the control zone if it is on the non-overlapping road (the intersection roads are
     *     always non-overlapping roads).
     *   - When the vehicle is on the entry road, it is in the control zone if the distance to end of the road is smaller
     *     or equal to controlZoneThreshold
     *   - When vehicle is on the exit road, the return value doesn't matter since the virtual roads of the exit roads
     *     are all overlapping roads
     *
     * @param vehicle the vehicle we want to assign a target to follow
     * @param roadSegment the road where the vehicle is
     * @return true if vehicle is inside the control zone, otherwise false
     */
    public static boolean isInsideControlZone(Vehicle vehicle, RoadSegment roadSegment){
       if (controlZoneThreshold < 0){
           return true;
       }

       if (!overlappingRoads.containsKey(roadSegment.id())){
           return true;
       }

       return vehicle.getDistanceToRoadSegmentEnd() <= controlZoneThreshold;
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
        checkInitialization();

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
     * Return the preceding distance from the front position of host vehicle to the rear position of front vehicle.
     * The preceding distance can be virtual preceding distance (if front vehicle is on the virtual road)
     * or normal preceding distance (if front vehicle is on the same road as the host vehicle)
     *
     * @param hostVehicle the target vehicle
     * @param frontVehicle the front vehicle
     * @return the preceding distance from the front position of host vehicle to the rear position of front vehicle
     */
    public static double getPrecedingDistanceToFrontVehicle(Vehicle hostVehicle, Vehicle frontVehicle) {
        checkInitialization();

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
        checkInitialization();
        return problematicCurve.getOrDefault(roadSegmentId, 1.0);
    }

    public static double getPositionFactorByUserID(String roadSegmentUserId){
        if (rawVirtualRoadInfo == null || rawVirtualRoadInfo.rawProblematicCurve == null){
            return 1.0;
        }

        return rawVirtualRoadInfo.rawProblematicCurve.getOrDefault(roadSegmentUserId, 1.0);
    }

    // currently simply add this method right after all vehicle.remove
    public static void recordIntersectionThroughput(Number value, Integer roadID){
        checkInitialization();
        intersectionThroughputMetrics.forEach(metrics -> metrics.record(value, roadID));
    }

    public static void recordIntersectionDelay(Number value, Integer roadID){
        checkInitialization();
        intersectionDelaysMetrics.forEach(metrics -> metrics.record(value, roadID));
    }

    public static List<IntersectionThroughput> getIntersectionMetrics() {
        return intersectionThroughputMetrics;
    }

    public static List<IntersectionDelay> getIntersectionDelaysMetrics() {
        return intersectionDelaysMetrics;
    }

    public static double getMetricsDisplayX() {
        return metricsDisplayX;
    }

    public static double getMetricsDisplayY() {
        return metricsDisplayY;
    }

    public static int getVehicleIdFont() { return vehicleIdFont; }

    public static int getMetricsFont() { return metricsFont; }

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


