package org.movsim.simulator.roadnetwork.IntersectionMetrics;

import java.awt.*;

public interface IntersectionMetrics {

    Color[] METRICS_COLORS = new Color[]{ Color.BLUE, Color.ORANGE, Color.PINK };

    /**
     * Return the name of metrics
     */
    String getName();

    /**
     * Return the value of metrics
     */
    Number getValue();

    /**
     * Record metrics
     *
     * @param value
     */
    void record(Number value, Integer roadID);
}
