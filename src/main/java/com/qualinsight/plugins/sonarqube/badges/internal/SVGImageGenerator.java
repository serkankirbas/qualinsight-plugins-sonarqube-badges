/*
 * qualinsight-plugins-sonarqube-badges
 * Copyright (c) 2015, QualInsight
 * http://www.qualinsight.com/
 *
 * This program is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program. If not, you can retrieve a copy
 * from <http://www.gnu.org/licenses/>.
 */
package com.qualinsight.plugins.sonarqube.badges.internal;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.ServerExtension;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

/**
 * Generates SVG images based on a quality gate status. A reusable {@link InputStream} is kept in a cache for each generated image in order to decrease computation time.
 *
 * @author Michel Pawlak
 */
public final class SVGImageGenerator implements ServerExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(SVGImageGenerator.class);

    private static final String SVG_NAMESPACE_URI = "http://www.w3.org/2000/svg";

    private static final String QUALIFIED_NAME = "svg";

    private static final String COMMENT_STRING = "Generated by QualInsight SVG Badge Generator";

    private static final Font FONT = FontProvider.preferredFont();

    private static final int X_MARGIN = 4;

    private static final int CANVAS_HEIGHT = 20;

    private static final int LABEL_WIDTH = 75;

    private static final String LABEL_TEXT = "quality gate";

    private static final int BACKGROUND_CORNER_ARC_DIAMETER = 6;

    private static final Color COLOR_BACKGROUND_LABEL = new Color(85, 85, 85, 255);

    private static final Color COLOR_SHADOW = new Color(0, 0, 0, 85);

    private static final Color COLOR_TEXT = new Color(255, 255, 255, 255);

    private static final int Y_OFFSET_SHADOW = 14;

    private static final int Y_OFFSET_TEXT = 14;

    private final EnumMap<QualityGateStatus, InputStream> qualityGateStatusImagesMap = new EnumMap<>(QualityGateStatus.class);

    private SVGGeneratorContext svgGeneratorContext;

    /**
     * {@link SVGImageGenerator} IoC constructor.
     */
    public SVGImageGenerator() {
        final DOMImplementation domImplementation = GenericDOMImplementation.getDOMImplementation();
        final Document document = domImplementation.createDocument(SVG_NAMESPACE_URI, QUALIFIED_NAME, null);
        this.svgGeneratorContext = SVGGeneratorContext.createDefault(document);
        this.svgGeneratorContext.setComment(COMMENT_STRING);
        LOGGER.info("SVGImageGenerator is now ready.");
    }

    /**
     * Returns an {@link InputStream} holding the content of the generated image for the provided quality gate status. All {@link InputStream}s are cached for future reuse.
     *
     * @param status quality gate status for which the image has to be generated
     * @return {@link InputStream} holding the expected SVG image
     * @throws IOException if a IO problem occurs during streams manipulation
     */
    public InputStream svgImageInputStreamFor(final QualityGateStatus status) throws IOException {
        InputStream svgImageInputStream;
        if (this.qualityGateStatusImagesMap.containsKey(status)) {
            LOGGER.debug("Found SVG image for {} status in cache, reusing it.");
            svgImageInputStream = this.qualityGateStatusImagesMap.get(status);
            // we don't trust previous InpuStream user, so we reset the position of the InpuStream
            svgImageInputStream.reset();
        } else {
            SVGGraphics2D svgGraphics2D;
            LOGGER.debug("Generating SVG image for {} status, then caching it.");
            svgGraphics2D = generateFor(status);
            // create a svgImageOutputStream to write svgGraphics2D content to
            final ByteArrayOutputStream svgImageOutputStream = new ByteArrayOutputStream();
            final Writer out = new OutputStreamWriter(svgImageOutputStream, StandardCharsets.UTF_8);
            // stream out the content of svgGraphics2D to svgImageOutputStream using CSS styling
            final boolean useCSS = true;
            svgGraphics2D.stream(out, useCSS);
            // create a svgImageInputStream from svgImageOutputStream content
            svgImageInputStream = new ByteArrayInputStream(svgImageOutputStream.toByteArray());
            // mark svgImageInputStream position to make it reusable
            svgImageInputStream.mark(Integer.MAX_VALUE);
            // put it into cache
            this.qualityGateStatusImagesMap.put(status, svgImageInputStream);
        }
        return svgImageInputStream;
    }

    private SVGGraphics2D generateFor(final QualityGateStatus status) {
        // new SVG graphics
        final SVGGraphics2D svgGraphics2D = new SVGGraphics2D(this.svgGeneratorContext, false);
        // set SVG canvas size
        svgGraphics2D.setSVGCanvasSize(new Dimension(LABEL_WIDTH + status.displayWidth(), CANVAS_HEIGHT));
        // set font
        svgGraphics2D.setFont(FONT);
        // draw Label background
        svgGraphics2D.setColor(COLOR_BACKGROUND_LABEL);
        svgGraphics2D.fillRoundRect(0, 0, LABEL_WIDTH, CANVAS_HEIGHT, BACKGROUND_CORNER_ARC_DIAMETER, BACKGROUND_CORNER_ARC_DIAMETER);
        svgGraphics2D.fillRect(LABEL_WIDTH - BACKGROUND_CORNER_ARC_DIAMETER, 0, BACKGROUND_CORNER_ARC_DIAMETER, CANVAS_HEIGHT);
        // draw Label text shadow
        svgGraphics2D.setColor(COLOR_SHADOW);
        svgGraphics2D.drawString(LABEL_TEXT, X_MARGIN, Y_OFFSET_SHADOW);
        // draw Label text
        svgGraphics2D.setColor(COLOR_TEXT);
        svgGraphics2D.drawString(LABEL_TEXT, X_MARGIN, Y_OFFSET_TEXT);
        // draw result background
        svgGraphics2D.setColor(status.displayBackgroundColor());
        svgGraphics2D.fillRoundRect(LABEL_WIDTH, 0, status.displayWidth(), CANVAS_HEIGHT, BACKGROUND_CORNER_ARC_DIAMETER, BACKGROUND_CORNER_ARC_DIAMETER);
        svgGraphics2D.fillRect(LABEL_WIDTH, 0, BACKGROUND_CORNER_ARC_DIAMETER, CANVAS_HEIGHT);
        // draw result text shadow
        svgGraphics2D.setColor(COLOR_SHADOW);
        svgGraphics2D.drawString(status.displayText(), LABEL_WIDTH + X_MARGIN, 15);
        // draw result text
        svgGraphics2D.setColor(COLOR_TEXT);
        svgGraphics2D.drawString(status.displayText(), LABEL_WIDTH + X_MARGIN, 14);
        return svgGraphics2D;
    }
}