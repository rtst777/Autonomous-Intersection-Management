package org.movsim.simulator.roadnetwork;

import com.google.common.base.Preconditions;
import org.movsim.autogen.TrafficLightStatus;
import org.movsim.simulator.roadnetwork.controller.TrafficLight;
import org.movsim.simulator.roadnetwork.controller.TrafficLightController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PedestrianService {

    private static final Logger LOG = LoggerFactory.getLogger(PedestrianService.class);

    /**
     * check if the directionString is valid. A valid directionString should start with a direction
     *
     * @param directionString
     */
    public static void validateDirectionString(String directionString){
        Preconditions.checkArgument(directionString.startsWith(Direction.LEFT.toString())
                        || directionString.startsWith(Direction.RIGHT.toString())
                        || directionString.startsWith(Direction.TOP.toString())
                        || directionString.startsWith(Direction.BOTTOM.toString()),
                "key=" + directionString + " doesn't start with the direction");
    }

    /**
     * Return the road user id after removing the direction.
     *
     * @param directionString
     * @return return the road user id after removing the direction
     */
    public static String extractRoadUserId(String directionString){
        validateDirectionString(directionString);

        if (directionString.startsWith(Direction.LEFT.toString())){
            return directionString.replaceFirst(Direction.LEFT.toString(), "");
        }
        else if (directionString.startsWith(Direction.RIGHT.toString())){
            return directionString.replaceFirst(Direction.RIGHT.toString(), "");
        }
        else if (directionString.startsWith(Direction.TOP.toString())){
            return directionString.replaceFirst(Direction.TOP.toString(), "");
        }
        else {
            return directionString.replaceFirst(Direction.BOTTOM.toString(), "");
        }
    }

    /**
     * Return the prefix direction
     *
     * @param directionString
     * @return the prefix direction
     */
    public static String extractDirection(String directionString){
        validateDirectionString(directionString);

        if (directionString.startsWith(Direction.LEFT.toString())){
            return Direction.LEFT.toString();
        }
        else if (directionString.startsWith(Direction.RIGHT.toString())){
            return Direction.RIGHT.toString();
        }
        else if (directionString.startsWith(Direction.TOP.toString())){
            return Direction.TOP.toString();
        }
        else {
            return Direction.BOTTOM.toString();
        }
    }

    // signal id of master traffic light -> id of the roads correlated to the signal
    private Map<String, Set<Integer>> signalIdToRoadIds;

    // signal ids of assistant traffic light
    private Set<String> assistantTrafficLights;

    // signal id of master traffic light -> trafficLightController that contains the signal
    private Map<String, TrafficLightController> signalIdToTrafficLightControllers;

    /**
     * @param signalIdToRoadIds
     * @param signalIdToTrafficLightControllers
     */
    public PedestrianService(Map<String, Set<Integer>> signalIdToRoadIds,
                             Set<String> assistantTrafficLights,
                             Map<String, TrafficLightController> signalIdToTrafficLightControllers){
        this.signalIdToRoadIds = new HashMap<>(signalIdToRoadIds);
        this.assistantTrafficLights = new HashSet<>(assistantTrafficLights);
        this.signalIdToTrafficLightControllers = new HashMap<>(signalIdToTrafficLightControllers);
    }

    /**
     * Return true if the trafficLight is the assistant traffic light in the pedestrian service or master traffic light
     * in the pedestrian service but is not in GREEN status
     *
     * @param trafficLight
     * @return true if the trafficLight is the assistant traffic light in the pedestrian service
     */
    public boolean isAssistantTrafficLight(TrafficLight trafficLight){
        return assistantTrafficLights.contains(trafficLight.signalId());
    }

    /**
     * Return true if the trafficLight is master traffic light, and is in GREEN status.
     *
     * Whenever pedestrian want to cross the intersection, they should send request to the master traffic light.
     * (in simulation UI, the request is a mouse click on the master traffic light). The request is accepted if the
     * master traffic light is in Red status.
     *
     * @param trafficLight
     * @return Return true if the trafficLight is master traffic light, and is in GREEN status.
     */
    public boolean isPedestrianCrossingRequestAccepted(TrafficLight trafficLight){
        if (signalIdToTrafficLightControllers.containsKey(trafficLight.signalId())){
            if (trafficLight.status() == TrafficLightStatus.GREEN){
                return true;
            }

            LOG.info("pedestrian crossing request is rejected since the master traffic light is not in GREEN status");
            return false;
        }

        return false;
    }

    /**
     * Return true if the roads is waiting for pedestrian
     *
     * @param roadID
     * @return true if the roads is waiting for pedestrian
     */
    public boolean isRoadWaitingForPedestrian(int roadID){
        for (Map.Entry<String, Set<Integer>> entry : signalIdToRoadIds.entrySet()) {
            String key = entry.getKey();
            Set<Integer> roads = entry.getValue();
            if (roads.contains(roadID)){
                TrafficLightController trafficLightController = signalIdToTrafficLightControllers.get(key);
                TrafficLightStatus trafficLightStatus = trafficLightController.getTrafficLights().
                        entrySet().iterator().next().getValue().status();
                return trafficLightStatus == TrafficLightStatus.RED;
            }
        }

        return false;
    }

    /**
     * Handle pedestrian crossing request. If the request is accepted, trigger traffic light signal and return true
     *
     * @param diresction
     * @return true if the request is accepted
     */
    public boolean handlePedestrianCrossingRequest(Direction diresction){
        if (diresction == null){
            return false;
        }

        for (Map.Entry<String, TrafficLightController> entry : signalIdToTrafficLightControllers.entrySet()) {
            String key = entry.getKey();
            TrafficLightController controller = entry.getValue();
            if (key.startsWith(diresction.toString())){
                TrafficLight masterTrafficLight = controller.getTrafficLights().entrySet().iterator().next().getValue();
                if (masterTrafficLight.status() == TrafficLightStatus.GREEN){
                    masterTrafficLight.triggerNextPhase();
                    return true;
                }
                else {
                    LOG.info("pedestrian crossing request is rejected since the master traffic light is not in GREEN status");
                    return false;
                }
            }
        }

        return false;
    }

    public enum Direction {
        LEFT("cross_left"),
        RIGHT("cross_right"),
        TOP("cross_top"),
        BOTTOM("cross_bottom")
        ;

        private final String text;

        /**
         * @param text
         */
        Direction (final String text) {
            this.text = text;
        }

        /* (non-Javadoc)
         * @see java.lang.Enum#toString()
         */
        @Override
        public String toString() {
            return text;
        }
    }

}
