/*
 * Copyright (C) 2010, 2011, 2012 by Arne Kesting, Martin Treiber, Ralph Germ, Martin Budden
 * <movsim.org@gmail.com>
 * -----------------------------------------------------------------------------------------
 * 
 * This file is part of
 * 
 * MovSim - the multi-model open-source vehicular-traffic simulator.
 * 
 * MovSim is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * MovSim is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MovSim. If not, see <http://www.gnu.org/licenses/>
 * or <http://www.movsim.org>.
 * 
 * -----------------------------------------------------------------------------------------
 */

package org.movsim.viewer.roadmapping;

import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Iterator;

import org.movsim.roadmappings.PosTheta;
import org.movsim.roadmappings.RoadMapping;
import org.movsim.roadmappings.RoadMapping.PolygonFloat;
import org.movsim.roadmappings.RoadMappingArc;
import org.movsim.roadmappings.RoadMappingBezier;
import org.movsim.roadmappings.RoadMappingLine;
import org.movsim.roadmappings.RoadMappingPoly;
import org.movsim.roadmappings.RoadMappingPolyBezier;
import org.movsim.roadmappings.RoadMappingPolyLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optimized drawing of RoadSegmentUtils based on the type of their RoadMapping
 */
public final class PaintRoadMapping {

    private static final Logger LOG = LoggerFactory.getLogger(PaintRoadMapping.class);

    private static final boolean DRAW_BEZIER_POINTS = false;

    private PaintRoadMapping() {
        throw new IllegalStateException("do not instanciate");
    }

    public static void paintRoadMapping(Graphics2D g, RoadMapping roadMapping) {
        assert !roadMapping.isPeer() : "should not be painted twice";
        double lateralOffset = roadMapping.calcOffsetToCenterline();
        LOG.debug("paint roads: roadMapping={}", roadMapping);
        LOG.debug("paint roads: roadWidth={}, laneCount={}", roadMapping.roadWidth(), roadMapping.laneCount());
        LOG.debug("paint roads: roadMapping.isPeer={}, lateralOffset={}", roadMapping.isPeer(), lateralOffset);
        paintRoadMapping(g, roadMapping, lateralOffset);
    }

    public static void paintRoadMapping(Graphics2D g, RoadMapping roadMapping, double lateralOffset) {
        assert !roadMapping.isPeer() : "should not be painted twice";

        final Line2D.Double line = new Line2D.Double();
        final Point2D from = new Point2D.Double();
        final Point2D to = new Point2D.Double();
        PosTheta posTheta;

        final double roadLength = roadMapping.roadLength();

        final Class<? extends RoadMapping> roadMappingClass = roadMapping.getClass();
        if (roadMappingClass == RoadMappingLine.class) {
            LOG.debug("paint RoadMappingLine={}", roadMapping);
            posTheta = roadMapping.startPos(lateralOffset);
            from.setLocation(posTheta.getScreenX(), posTheta.getScreenY());
            posTheta = roadMapping.endPos(lateralOffset);
            to.setLocation(posTheta.getScreenX(), posTheta.getScreenY());
            line.setLine(from, to);
            g.draw(line);
            return;
        } else if (roadMappingClass == RoadMappingArc.class) {
            final RoadMappingArc arc = (RoadMappingArc) roadMapping;
            LOG.debug("lateralOffset={},  paint RoadMappingArc={}", lateralOffset, arc);
            posTheta = roadMapping.startPos();
            final double angSt = arc.startAngle() + (arc.clockwise() ? 0.5 * Math.PI : -0.5 * Math.PI);
            final double radius = arc.radius();
            final double dx = radius * Math.cos(angSt);
            final double dy = radius * Math.sin(angSt);
            final Arc2D.Double arc2D = new Arc2D.Double();

            arc2D.setArcByCenter(posTheta.getScreenX() - dx, posTheta.getScreenY() + dy,
                    radius + lateralOffset * (arc.clockwise() ? 1 : -1), Math.toDegrees(angSt),
                    Math.toDegrees(arc.arcAngle()), Arc2D.OPEN);
            g.draw(arc2D);
            return;
        } else if (roadMappingClass == RoadMappingPoly.class) {
            LOG.debug("paint RoadMappingPoly={}", roadMapping);
            final RoadMappingPoly poly = (RoadMappingPoly) roadMapping;
            for (final RoadMapping map : poly) {
                paintRoadMapping(g, map, lateralOffset);
            }
            return;
        } else if (roadMappingClass == RoadMappingPolyLine.class) {
            LOG.debug("paint RoadMappingPolyLine={}", roadMapping);
            // TODO need to properly handle joins of the lines in the polyline
            if (lateralOffset == 0.0) {
                final RoadMappingPolyLine polyLine = (RoadMappingPolyLine) roadMapping;
                final Iterator<RoadMappingLine> iterator = polyLine.iterator();
                if (!iterator.hasNext())
                    return;
                final GeneralPath path = new GeneralPath();
                RoadMappingLine line1 = iterator.next();
                posTheta = line1.startPos(lateralOffset);
                path.moveTo(posTheta.getScreenX(), posTheta.getScreenY());
                posTheta = line1.endPos(lateralOffset);
                path.lineTo(posTheta.getScreenX(), posTheta.getScreenY());
                while (iterator.hasNext()) {
                    line1 = iterator.next();
                    posTheta = line1.endPos(lateralOffset);
                    path.lineTo(posTheta.getScreenX(), posTheta.getScreenY());
                }
                g.draw(path);
                return;
            }
        } else if (roadMappingClass == RoadMappingBezier.class) {
            LOG.debug("paint RoadMappingBezier");
            if (lateralOffset == 0.0) {
                // TODO remove this zero condition when Bezier lateral offset
                // for control points has been fixed
                // Bezier mapping does not quite give correct control point
                // offsets
                // so only use this if lateral offset is zero (ie not for road
                // edge lines)
                final RoadMappingBezier bezier = (RoadMappingBezier) roadMapping;
                final GeneralPath path = new GeneralPath();
                posTheta = bezier.startPos(lateralOffset);
                path.moveTo(posTheta.getScreenX(), posTheta.getScreenY());
                posTheta = bezier.endPos(lateralOffset);
                final double cx = bezier.controlX(lateralOffset);
                final double cy = bezier.controlY(lateralOffset);
                path.quadTo(cx, cy, posTheta.getScreenX(), posTheta.getScreenY());
                g.draw(path);
                return;
            }
        } else if (roadMappingClass == RoadMappingPolyBezier.class) {
            LOG.debug("paint RoadMappingPolyBezier");
            if (lateralOffset == 0.0) {
                final RoadMappingPolyBezier polyBezier = (RoadMappingPolyBezier) roadMapping;
                final Iterator<RoadMappingBezier> iterator = polyBezier.iterator();
                if (!iterator.hasNext())
                    return;
                final GeneralPath path = new GeneralPath();
                RoadMappingBezier bezier = iterator.next();
                posTheta = bezier.startPos(lateralOffset);
                final int radius = 10;
                final int radiusC = 6;
                if (DRAW_BEZIER_POINTS) {
                    g.fillOval((int) posTheta.getScreenX() - radius / 2, (int) posTheta.getScreenY() - radius / 2,
                            radius, radius);
                }
                path.moveTo(posTheta.getScreenX(), posTheta.getScreenY());
                posTheta = bezier.endPos(lateralOffset);
                path.quadTo(bezier.controlX(lateralOffset), bezier.controlY(lateralOffset), posTheta.getScreenX(),
                        posTheta.getScreenY());
                if (DRAW_BEZIER_POINTS) {
                    g.fillOval((int) posTheta.getScreenX() - radius / 2, (int) posTheta.getScreenY() - radius / 2,
                            radius, radius);
                    g.fillOval((int) bezier.controlX(lateralOffset) - radiusC / 2, (int) bezier.controlY(lateralOffset)
                            - radiusC / 2, radiusC, radiusC);
                }
                while (iterator.hasNext()) {
                    bezier = iterator.next();
                    posTheta = bezier.endPos(lateralOffset);
                    path.quadTo(bezier.controlX(lateralOffset), bezier.controlY(lateralOffset), posTheta.getScreenX(),
                            posTheta.getScreenY());
                    if (DRAW_BEZIER_POINTS) {
                        g.fillOval((int) posTheta.getScreenX() - radius / 2, (int) posTheta.getScreenY() - radius / 2,
                                radius, radius);
                        g.fillOval((int) bezier.controlX(lateralOffset) - radiusC / 2,
                                (int) bezier.controlY(lateralOffset) - radiusC / 2, radiusC, radiusC);
                    }
                }
                g.draw(path);
                return;
            }
        }

        // default drawing
        LOG.debug("draw the road in sections 5 meters long");
        final double sectionLength = 5.0;
        double roadPos = 0.0;
        posTheta = roadMapping.startPos(lateralOffset);
        from.setLocation(posTheta.getScreenX(), posTheta.getScreenY());
        while (roadPos < roadLength) {
            roadPos += sectionLength;
            posTheta = roadMapping.map(Math.min(roadPos, roadLength), lateralOffset);
            to.setLocation(posTheta.getScreenX(), posTheta.getScreenY());
            line.setLine(from, to);
            g.draw(line);
            from.setLocation(to.getX(), to.getY());
        }
    }

    public static void setClipPath(Graphics2D g, RoadMapping roadMapping, GeneralPath clipPath) {
        if (roadMapping.clippingPolygons() == null) {
            g.setClip(null);
        } else {
            clipPath.reset();
            assert clipPath.getWindingRule() == Path2D.WIND_EVEN_ODD;
            // add the clip regions
            for (final PolygonFloat polygon : roadMapping.clippingPolygons()) {
                clipPath.moveTo(polygon.getXPoint(0), polygon.getYPoint(0));
                clipPath.lineTo(polygon.getXPoint(1), polygon.getYPoint(1));
                clipPath.lineTo(polygon.getXPoint(2), polygon.getYPoint(2));
                clipPath.lineTo(polygon.getXPoint(3), polygon.getYPoint(3));
                clipPath.lineTo(polygon.getXPoint(0), polygon.getYPoint(0));
            }
            // add the outer region (encloses whole road), so that everything
            // outside the clip
            // region is drawn
            final PolygonFloat polygon = roadMapping.outsideClippingPolygon();
            clipPath.moveTo(polygon.getXPoint(0), polygon.getYPoint(0));
            clipPath.lineTo(polygon.getXPoint(1), polygon.getYPoint(1));
            clipPath.lineTo(polygon.getXPoint(2), polygon.getYPoint(2));
            clipPath.lineTo(polygon.getXPoint(3), polygon.getYPoint(3));
            clipPath.lineTo(polygon.getXPoint(0), polygon.getYPoint(0));
            clipPath.closePath();
            g.setClip(clipPath);
        }
    }
}
