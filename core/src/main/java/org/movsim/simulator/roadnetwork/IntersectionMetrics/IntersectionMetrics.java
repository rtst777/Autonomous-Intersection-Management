package org.movsim.simulator.roadnetwork.IntersectionMetrics;

import java.awt.*;

public interface IntersectionMetrics {

    Color[] METRICS_COLORS = new Color[]{ new Color(220, 0, 0), Color.BLUE, Color.YELLOW, Color.PINK, Color.MAGENTA };

    /**
     * Return the name of metrics
     */
    String getName();

    /**
     * Return the value of metrics
     */
    Number getValue();

    /**
     * Return the average value of metrics (e.g. value per vehicle, value per second, etc)
     */
    Number getAverageValue(Number normalizationFactor);

    /**
     * Record metrics
     *
     * @param value
     */
    void record(Number value, Integer roadID);
}
