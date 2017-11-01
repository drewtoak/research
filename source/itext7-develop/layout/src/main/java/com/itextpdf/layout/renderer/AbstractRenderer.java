/*

    This file is part of the iText (R) project.
    Copyright (c) 1998-2017 iText Group NV
    Authors: Bruno Lowagie, Paulo Soares, et al.

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation with the addition of the
    following permission added to Section 15 as permitted in Section 7(a):
    FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
    ITEXT GROUP. ITEXT GROUP DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
    OF THIRD PARTY RIGHTS

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program; if not, see http://www.gnu.org/licenses or write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA, 02110-1301 USA, or download the license from the following URL:
    http://itextpdf.com/terms-of-use/

    The interactive user interfaces in modified source and object code versions
    of this program must display Appropriate Legal Notices, as required under
    Section 5 of the GNU Affero General Public License.

    In accordance with Section 7(b) of the GNU Affero General Public License,
    a covered work must retain the producer line in every PDF that is created
    or manipulated using iText.

    You can be released from the requirements of the license by purchasing
    a commercial license. Buying such a license is mandatory as soon as you
    develop commercial activities involving the iText software without
    disclosing the source code of your own applications.
    These activities include: offering paid services to customers as an ASP,
    serving PDFs on the fly in a web application, shipping iText with a closed
    source product.

    For more information, please contact iText Software Corp. at this
    address: sales@itextpdf.com
 */
package com.itextpdf.layout.renderer;

import com.itextpdf.io.LogMessageConstant;
import com.itextpdf.io.util.MessageFormatUtil;
import com.itextpdf.io.util.NumberUtil;
import com.itextpdf.kernel.color.Color;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.AffineTransform;
import com.itextpdf.kernel.geom.Point;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.action.PdfAction;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.canvas.CanvasArtifact;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagutils.IAccessibleElement;
import com.itextpdf.kernel.pdf.tagutils.TagTreePointer;
import com.itextpdf.layout.IPropertyContainer;
import com.itextpdf.layout.border.Border;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.IElement;
import com.itextpdf.layout.font.FontCharacteristics;
import com.itextpdf.layout.font.FontFamilySplitter;
import com.itextpdf.layout.font.FontProvider;
import com.itextpdf.layout.layout.LayoutArea;
import com.itextpdf.layout.layout.LayoutContext;
import com.itextpdf.layout.layout.LayoutPosition;
import com.itextpdf.layout.layout.PositionedLayoutContext;
import com.itextpdf.layout.minmaxwidth.MinMaxWidth;
import com.itextpdf.layout.minmaxwidth.MinMaxWidthUtils;
import com.itextpdf.layout.property.Background;
import com.itextpdf.layout.property.BackgroundImage;
import com.itextpdf.layout.property.BaseDirection;
import com.itextpdf.layout.property.BoxSizingPropertyValue;
import com.itextpdf.layout.property.HorizontalAlignment;
import com.itextpdf.layout.property.Property;
import com.itextpdf.layout.property.TransparentColor;
import com.itextpdf.layout.property.Transform;
import com.itextpdf.layout.property.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the most common properties and behavior that are shared by most
 * {@link IRenderer} implementations. All default Renderers are subclasses of
 * this default implementation.
 */
public abstract class AbstractRenderer implements IRenderer {

    /**
     * The maximum difference between {@link Rectangle} coordinates to consider rectangles equal
     */
    public static final float EPS = 1e-4f;

    /**
     * The infinity value which is used while layouting
     */
    public static final float INF = 1e6f;

    // TODO linkedList?
    protected List<IRenderer> childRenderers = new ArrayList<>();
    protected List<IRenderer> positionedRenderers = new ArrayList<>();
    protected IPropertyContainer modelElement;
    protected boolean flushed = false;
    protected LayoutArea occupiedArea;
    protected IRenderer parent;
    protected Map<Integer, Object> properties = new HashMap<>();
    protected boolean isLastRendererForModelElement = true;

    /**
     * Creates a renderer.
     */
    protected AbstractRenderer() {
    }

    /**
     * Creates a renderer for the specified layout element.
     *
     * @param modelElement the layout element that will be drawn by this renderer
     */
    protected AbstractRenderer(IElement modelElement) {
        this.modelElement = modelElement;
    }

    protected AbstractRenderer(AbstractRenderer other) {
        this.childRenderers = other.childRenderers;
        this.positionedRenderers = other.positionedRenderers;
        this.modelElement = other.modelElement;
        this.flushed = other.flushed;
        this.occupiedArea = other.occupiedArea != null ? other.occupiedArea.clone() : null;
        this.parent = other.parent;
        this.properties.putAll(other.properties);
        this.isLastRendererForModelElement = other.isLastRendererForModelElement;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addChild(IRenderer renderer) {
        // https://www.webkit.org/blog/116/webcore-rendering-iii-layout-basics
        // "The rules can be summarized as follows:"...
        Integer positioning = renderer.<Integer>getProperty(Property.POSITION);
        if (positioning == null || positioning == LayoutPosition.RELATIVE || positioning == LayoutPosition.STATIC) {
            childRenderers.add(renderer);
        } else if (positioning == LayoutPosition.FIXED) {
            AbstractRenderer root = this;
            while (root.parent instanceof AbstractRenderer) {
                root = (AbstractRenderer) root.parent;
            }
            if (root == this) {
                positionedRenderers.add(renderer);
            } else {
                root.addChild(renderer);
            }
        } else if (positioning == LayoutPosition.ABSOLUTE) {
            // For position=absolute, if none of the top, bottom, left, right properties are provided,
            // the content should be displayed in the flow of the current content, not overlapping it.
            // The behavior is just if it would be statically positioned except it does not affect other elements
            AbstractRenderer positionedParent = this;
            boolean noPositionInfo = AbstractRenderer.noAbsolutePositionInfo(renderer);
            while (!positionedParent.isPositioned() && !noPositionInfo) {
                IRenderer parent = positionedParent.parent;
                if (parent instanceof AbstractRenderer) {
                    positionedParent = (AbstractRenderer) parent;
                } else {
                    break;
                }
            }
            if (positionedParent == this) {
                positionedRenderers.add(renderer);
            } else {
                positionedParent.addChild(renderer);
            }
        }

        // Fetch positioned renderers from non-positioned child because they might be stuck there because child's parent was null previously
        if (renderer instanceof AbstractRenderer && !((AbstractRenderer) renderer).isPositioned() && ((AbstractRenderer) renderer).positionedRenderers.size() > 0) {
            // For position=absolute, if none of the top, bottom, left, right properties are provided,
            // the content should be displayed in the flow of the current content, not overlapping it.
            // The behavior is just if it would be statically positioned except it does not affect other elements
            int pos = 0;
            List<IRenderer> childPositionedRenderers = ((AbstractRenderer) renderer).positionedRenderers;
            while (pos < childPositionedRenderers.size()) {
                if (AbstractRenderer.noAbsolutePositionInfo(childPositionedRenderers.get(pos))) {
                    pos++;
                } else {
                    positionedRenderers.add(childPositionedRenderers.get(pos));
                    childPositionedRenderers.remove(pos);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IPropertyContainer getModelElement() {
        return modelElement;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IRenderer> getChildRenderers() {
        return childRenderers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasProperty(int property) {
        return hasOwnProperty(property)
                || (modelElement != null && modelElement.hasProperty(property))
                || (parent != null && Property.isPropertyInherited(property) && parent.hasProperty(property));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasOwnProperty(int property) {
        return properties.containsKey(property);
    }

    /**
     * Checks if this renderer or its model element have the specified property,
     * i.e. if it was set to this very element or its very model element earlier.
     *
     * @param property the property to be checked
     * @return {@code true} if this instance or its model element have given own property, {@code false} otherwise
     */
    public boolean hasOwnOrModelProperty(int property) {
        return hasOwnOrModelProperty(this, property);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteOwnProperty(int property) {
        properties.remove(property);
    }

    /**
     * Deletes property from this very renderer, or in case the property is specified on its model element, the
     * property of the model element is deleted
     *
     * @param property the property key to be deleted
     */
    public void deleteProperty(int property) {
        if (properties.containsKey(property)) {
            properties.remove(property);
        } else {
            if (modelElement != null) {
                modelElement.deleteOwnProperty(property);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T1> T1 getProperty(int key) {
        Object property;
        if ((property = properties.get(key)) != null || properties.containsKey(key)) {
            return (T1) property;
        }
        if (modelElement != null && ((property = modelElement.<T1>getProperty(key)) != null || modelElement.hasProperty(key))) {
            return (T1) property;
        }
        // TODO in some situations we will want to check inheritance with additional info, such as parent and descendant.
        if (parent != null && Property.isPropertyInherited(key) && (property = parent.<T1>getProperty(key)) != null) {
            return (T1) property;
        }
        property = this.<T1>getDefaultProperty(key);
        if (property != null) {
            return (T1) property;
        }
        return modelElement != null ? modelElement.<T1>getDefaultProperty(key) : (T1) (Object) null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T1> T1 getOwnProperty(int property) {
        return (T1) properties.get(property);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T1> T1 getProperty(int property, T1 defaultValue) {
        T1 result = this.<T1>getProperty(property);
        return result != null ? result : defaultValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setProperty(int property, Object value) {
        properties.put(property, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T1> T1 getDefaultProperty(int property) {
        return (T1) (Object) null;
    }

    /**
     * Returns a property with a certain key, as a font object.
     *
     * @param property an {@link Property enum value}
     * @return a {@link PdfFont}
     */
    public PdfFont getPropertyAsFont(int property) {
        return this.<PdfFont>getProperty(property);
    }

    /**
     * Returns a property with a certain key, as a color.
     *
     * @param property an {@link Property enum value}
     * @return a {@link Color}
     */
    public Color getPropertyAsColor(int property) {
        return this.<Color>getProperty(property);
    }

    /**
     * Returns a property with a certain key, as a {@link TransparentColor}.
     *
     * @param property an {@link Property enum value}
     * @return a {@link TransparentColor}
     */
    public TransparentColor getPropertyAsTransparentColor(int property) {
        return this.<TransparentColor>getProperty(property);
    }

    /**
     * Returns a property with a certain key, as a floating point value.
     *
     * @param property an {@link Property enum value}
     * @return a {@link Float}
     */
    public Float getPropertyAsFloat(int property) {
        return NumberUtil.asFloat(this.<Object>getProperty(property));
    }

    /**
     * Returns a property with a certain key, as a floating point value.
     *
     * @param property     an {@link Property enum value}
     * @param defaultValue default value to be returned if property is not found
     * @return a {@link Float}
     */
    public Float getPropertyAsFloat(int property, Float defaultValue) {
        return NumberUtil.asFloat(this.<Object>getProperty(property, defaultValue));
    }

    /**
     * Returns a property with a certain key, as a boolean value.
     *
     * @param property an {@link Property enum value}
     * @return a {@link Boolean}
     */
    public Boolean getPropertyAsBoolean(int property) {
        return this.<Boolean>getProperty(property);
    }

    /**
     * Returns a property with a certain key, as an integer value.
     *
     * @param property an {@link Property enum value}
     * @return a {@link Integer}
     */
    public Integer getPropertyAsInteger(int property) {
        return NumberUtil.asInteger(this.<Object>getProperty(property));
    }

    /**
     * Returns a string representation of the renderer.
     *
     * @return a {@link String}
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (IRenderer renderer : childRenderers) {
            sb.append(renderer.toString());
        }
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LayoutArea getOccupiedArea() {
        return occupiedArea;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(DrawContext drawContext) {
        applyDestinationsAndAnnotation(drawContext);

        boolean relativePosition = isRelativePosition();
        if (relativePosition) {
            applyRelativePositioningTranslation(false);
        }

        beginElementOpacityApplying(drawContext);
        drawBackground(drawContext);
        drawBorder(drawContext);
        drawChildren(drawContext);
        drawPositionedChildren(drawContext);
        endElementOpacityApplying(drawContext);

        if (relativePosition) {
            applyRelativePositioningTranslation(true);
        }

        flushed = true;
    }

    protected void beginElementOpacityApplying(DrawContext drawContext) {
        Float opacity = this.getPropertyAsFloat(Property.OPACITY);
        if (opacity != null && opacity < 1f) {
            PdfExtGState extGState = new PdfExtGState();
            extGState
                    .setStrokeOpacity((float) opacity)
                    .setFillOpacity((float) opacity);
            drawContext.getCanvas()
                    .saveState()
                    .setExtGState(extGState);
        }
    }

    protected void endElementOpacityApplying(DrawContext drawContext) {
        Float opacity = this.getPropertyAsFloat(Property.OPACITY);
        if (opacity != null && opacity < 1f) {
            drawContext.getCanvas().restoreState();
        }
    }

    /**
     * Draws a background layer if it is defined by a key {@link Property#BACKGROUND}
     * in either the layout element or this {@link IRenderer} itself.
     *
     * @param drawContext the context (canvas, document, etc) of this drawing operation.
     */
    public void drawBackground(DrawContext drawContext) {
        Background background = this.<Background>getProperty(Property.BACKGROUND);
        BackgroundImage backgroundImage = this.<BackgroundImage>getProperty(Property.BACKGROUND_IMAGE);
        if (background != null || backgroundImage != null) {
            Rectangle bBox = getOccupiedAreaBBox();
            boolean isTagged = drawContext.isTaggingEnabled() && getModelElement() instanceof IAccessibleElement;
            if (isTagged) {
                drawContext.getCanvas().openTag(new CanvasArtifact());
            }
            Rectangle backgroundArea = applyMargins(bBox, false);
            if (backgroundArea.getWidth() <= 0 || backgroundArea.getHeight() <= 0) {
                Logger logger = LoggerFactory.getLogger(AbstractRenderer.class);
                logger.warn(MessageFormatUtil.format(LogMessageConstant.RECTANGLE_HAS_NEGATIVE_OR_ZERO_SIZES, "background"));
            } else {
                boolean backgroundAreaIsClipped = false;
                if (background != null) {
                    backgroundAreaIsClipped = clipBackgroundArea(drawContext, backgroundArea);
                    TransparentColor backgroundColor = new TransparentColor(background.getColor(), background.getOpacity());
                    drawContext.getCanvas().saveState().setFillColor(backgroundColor.getColor());
                    backgroundColor.applyFillTransparency(drawContext.getCanvas());
                    drawContext.getCanvas()
                            .rectangle(backgroundArea.getX() - background.getExtraLeft(), backgroundArea.getY() - background.getExtraBottom(),
                                    backgroundArea.getWidth() + background.getExtraLeft() + background.getExtraRight(),
                                    backgroundArea.getHeight() + background.getExtraTop() + background.getExtraBottom()).
                            fill().restoreState();

                }
                if (backgroundImage != null && backgroundImage.getImage() != null) {
                    if (!backgroundAreaIsClipped) {
                        backgroundAreaIsClipped = clipBackgroundArea(drawContext, backgroundArea);
                    }
                    applyBorderBox(backgroundArea, false);
                    Rectangle imageRectangle = new Rectangle(backgroundArea.getX(), backgroundArea.getTop() - backgroundImage.getImage().getHeight(),
                            backgroundImage.getImage().getWidth(), backgroundImage.getImage().getHeight());
                    if (imageRectangle.getWidth() <= 0 || imageRectangle.getHeight() <= 0) {
                        Logger logger = LoggerFactory.getLogger(AbstractRenderer.class);
                        logger.warn(MessageFormatUtil.format(LogMessageConstant.RECTANGLE_HAS_NEGATIVE_OR_ZERO_SIZES, "background-image"));
                    } else {
                        applyBorderBox(backgroundArea, true);
                        drawContext.getCanvas().saveState().rectangle(backgroundArea).clip().newPath();
                        float initialX = backgroundImage.isRepeatX() ? imageRectangle.getX() - imageRectangle.getWidth() : imageRectangle.getX();
                        float initialY = backgroundImage.isRepeatY() ? imageRectangle.getTop() : imageRectangle.getY();
                        imageRectangle.setY(initialY);
                        do {
                            imageRectangle.setX(initialX);
                            do {
                                drawContext.getCanvas().addXObject(backgroundImage.getImage(), imageRectangle);
                                imageRectangle.moveRight(imageRectangle.getWidth());
                            }
                            while (backgroundImage.isRepeatX() && imageRectangle.getLeft() < backgroundArea.getRight());
                            imageRectangle.moveDown(imageRectangle.getHeight());
                        } while (backgroundImage.isRepeatY() && imageRectangle.getTop() > backgroundArea.getBottom());
                        drawContext.getCanvas().restoreState();
                    }
                }
                if (backgroundAreaIsClipped) {
                    drawContext.getCanvas().restoreState();
                }
            }
            if (isTagged) {
                drawContext.getCanvas().closeTag();
            }
        }
    }

    protected boolean clipBorderArea(DrawContext drawContext, Rectangle outerBorderBox) {
        final double curv = 0.4477f;
        UnitValue borderRadius = this.<UnitValue>getProperty(Property.BORDER_RADIUS);
        float radius = 0;
        if (null != borderRadius) {
            if (borderRadius.isPercentValue()) {
                Logger logger = LoggerFactory.getLogger(BlockRenderer.class);
                logger.error(MessageFormatUtil.format(LogMessageConstant.PROPERTY_IN_PERCENTS_NOT_SUPPORTED, "border-radius"));
            } else {
                radius = borderRadius.getValue();
            }
        }
        if (0 != radius) {
            float top = outerBorderBox.getTop(), right = outerBorderBox.getRight(), bottom = outerBorderBox.getBottom(), left = outerBorderBox.getLeft();
            float verticalRadius = Math.min(outerBorderBox.getHeight() / 2, radius);
            float horizontalRadius = Math.min(outerBorderBox.getWidth() / 2, radius);
            // radius border bbox
            float x1 = right - horizontalRadius, y1 = top - verticalRadius,
                    x2 = right - horizontalRadius, y2 = bottom + verticalRadius,
                    x3 = left + horizontalRadius, y3 = bottom + verticalRadius,
                    x4 = left + horizontalRadius, y4 = top - verticalRadius;

            PdfCanvas canvas = drawContext.getCanvas();
            canvas.saveState();

            // right top corner
            canvas
                    .moveTo(left, top)
                    .lineTo(x1, top)
                    .curveTo(x1 + horizontalRadius * curv, top, right, y1 + verticalRadius * curv, right, y1)
                    .lineTo(right, bottom)
                    .lineTo(left, bottom)
                    .lineTo(left, top);
            canvas.clip().newPath();

            // right bottom corner
            canvas
                    .moveTo(right, top)
                    .lineTo(right, y2)
                    .curveTo(right, y2 - verticalRadius * curv, x2 + horizontalRadius * curv, bottom, x2, bottom)
                    .lineTo(left, bottom)
                    .lineTo(left, top)
                    .lineTo(right, top);
            canvas.clip().newPath();

            // left bottom corner
            canvas
                    .moveTo(right, bottom)
                    .lineTo(x3, bottom)
                    .curveTo(x3 - horizontalRadius * curv, bottom, left, y3 - verticalRadius * curv, left, y3)
                    .lineTo(left, top)
                    .lineTo(right, top)
                    .lineTo(right, bottom);
            canvas.clip().newPath();

            // left top corner
            canvas
                    .moveTo(left, bottom)
                    .lineTo(left, y4)
                    .curveTo(left, y4 + verticalRadius * curv, x4 - horizontalRadius * curv, top, x4, top)
                    .lineTo(right, top)
                    .lineTo(right, bottom)
                    .lineTo(left, bottom);
            canvas.clip().newPath();

            Border[] borders = getBorders();

            float radiusTop = verticalRadius, radiusRight = horizontalRadius, radiusBottom = verticalRadius, radiusLeft = horizontalRadius;
            float topBorderWidth = 0, rightBorderWidth = 0, bottomBorderWidth = 0, leftBorderWidth = 0;
            if (borders[0] != null) {
                topBorderWidth = borders[0].getWidth();
                top = top - borders[0].getWidth();
                if (y1 > top) {
                    y1 = top;
                    y4 = top;
                }
                radiusTop = Math.max(0, radiusTop - borders[0].getWidth());
            }
            if (borders[1] != null) {
                rightBorderWidth = borders[1].getWidth();

                right = right - borders[1].getWidth();
                if (x1 > right) {
                    x1 = right;
                    x2 = right;
                }
                radiusRight = Math.max(0, radiusRight - borders[1].getWidth());
            }
            if (borders[2] != null) {
                bottomBorderWidth = borders[2].getWidth();

                bottom = bottom + borders[2].getWidth();
                if (x3 < left) {
                    x3 = left;
                    x4 = left;
                }

                radiusBottom = Math.max(0, radiusBottom - borders[2].getWidth());
            }
            if (borders[3] != null) {
                leftBorderWidth = borders[3].getWidth();

                left = left + borders[3].getWidth();
                radiusLeft = Math.max(0, radiusLeft - borders[3].getWidth());
            }

            canvas
                    .moveTo(x1, top)
                    .curveTo(x1 + Math.min(radiusTop, radiusRight) * curv, top, right, y1 + Math.min(radiusTop, radiusRight) * curv, right, y1)
                    .lineTo(right, y2)
                    .lineTo(x3, y2)
                    .lineTo(x3, top)
                    .lineTo(x1, top)
                    .lineTo(x1, top + topBorderWidth)
                    .lineTo(left - leftBorderWidth, top + topBorderWidth)
                    .lineTo(left - leftBorderWidth, bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth, bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth, top + topBorderWidth)
                    .lineTo(x1, top + topBorderWidth);
            canvas.clip().newPath();

            canvas
                    .moveTo(right, y2)
                    .curveTo(right, y2 - Math.min(radiusRight, radiusBottom) * curv, x2 + Math.min(radiusRight, radiusBottom) * curv, bottom, x2, bottom)
                    .lineTo(x3, bottom)
                    .lineTo(x3, y4)
                    .lineTo(right, y4)
                    .lineTo(right, y2)
                    .lineTo(right + rightBorderWidth, y2)
                    .lineTo(right + rightBorderWidth, top + topBorderWidth)
                    .lineTo(left -  leftBorderWidth, top + topBorderWidth)
                    .lineTo(left -  leftBorderWidth,  bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth,  bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth, y2);
            canvas.clip().newPath();

            canvas
                    .moveTo(x3, bottom)
                    .curveTo(x3 - Math.min(radiusBottom, radiusLeft) * curv, bottom, left, y3 - Math.min(radiusBottom, radiusLeft) * curv, left, y3)
                    .lineTo(left, y4)
                    .lineTo(x1, y4)
                    .lineTo(x1, bottom)
                    .lineTo(x3, bottom)
                    .lineTo(x3, bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth, bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth, top + topBorderWidth)
                    .lineTo(left - leftBorderWidth, top + topBorderWidth)
                    .lineTo(left - leftBorderWidth, bottom - bottomBorderWidth)
                    .lineTo(x3, bottom - bottomBorderWidth);
            canvas.clip().newPath();

            canvas
                    .moveTo(left, y4)
                    .curveTo(left, y4 + Math.min(radiusLeft, radiusTop) * curv, x4 - Math.min(radiusLeft, radiusTop) * curv, top, x4, top)
                    .lineTo(x1, top)
                    .lineTo(x1, y2)
                    .lineTo(left, y2)
                    .lineTo(left, y4)
                    .lineTo(left - leftBorderWidth, y4)
                    .lineTo(left - leftBorderWidth, bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth, bottom - bottomBorderWidth)
                    .lineTo(right + rightBorderWidth, top + topBorderWidth)
                    .lineTo(left - leftBorderWidth, top + topBorderWidth)
                    .lineTo(left - leftBorderWidth, y4);
            canvas.clip().newPath();

        }
        return 0 != radius;
    }


    protected boolean clipBackgroundArea(DrawContext drawContext, Rectangle outerBorderBox) {
        final double curv = 0.4477f;
        UnitValue borderRadius = this.<UnitValue>getProperty(Property.BORDER_RADIUS);
        float radius = 0;
        if (null != borderRadius) {
            if (borderRadius.isPercentValue()) {
                Logger logger = LoggerFactory.getLogger(BlockRenderer.class);
                logger.error(MessageFormatUtil.format(LogMessageConstant.PROPERTY_IN_PERCENTS_NOT_SUPPORTED, "border-radius"));
            } else {
                radius = borderRadius.getValue();
            }
        }
        if (0 != radius) {
            float top = outerBorderBox.getTop(), right = outerBorderBox.getRight(), bottom = outerBorderBox.getBottom(), left = outerBorderBox.getLeft();
            float verticalRadius = Math.min(outerBorderBox.getHeight() / 2, radius);
            float horizontalRadius = Math.min(outerBorderBox.getWidth() / 2, radius);

            // radius border bbox
            float x1 = right - horizontalRadius, y1 = top - verticalRadius,
                    x2 = right - horizontalRadius, y2 = bottom + verticalRadius,
                    x3 = left + horizontalRadius, y3 = bottom + verticalRadius,
                    x4 = left + horizontalRadius, y4 = top - verticalRadius;

            PdfCanvas canvas = drawContext.getCanvas();
            canvas.saveState();

            canvas
                    .moveTo(left, top)
                    .lineTo(x1, top)
                    .curveTo(x1 + horizontalRadius * curv, top, right, y1 + verticalRadius * curv, right, y1)
                    .lineTo(right, bottom)
                    .lineTo(left, bottom)
                    .lineTo(left, top);
            canvas.clip().newPath();

            canvas
                    .moveTo(right, top)
                    .lineTo(right, y2)
                    .curveTo(right, y2 - verticalRadius * curv, x2 + horizontalRadius * curv, bottom, x2, bottom)
                    .lineTo(left, bottom)
                    .lineTo(left, top)
                    .lineTo(right, top);
            canvas.clip().newPath();

            canvas
                    .moveTo(right, bottom)
                    .lineTo(x3, bottom)
                    .curveTo(x3 - horizontalRadius * curv, bottom, left, y3 - verticalRadius * curv, left, y3)
                    .lineTo(left, top)
                    .lineTo(right, top)
                    .lineTo(right, bottom);
            canvas.clip().newPath();

            canvas
                    .moveTo(left, bottom)
                    .lineTo(left, y4)
                    .curveTo(left, y4 + verticalRadius * curv, x4 - horizontalRadius * curv, top, x4, top)
                    .lineTo(right, top)
                    .lineTo(right, bottom)
                    .lineTo(left, bottom);
            canvas.clip().newPath();
        }
        return 0 != radius;
    }

    /**
     * Performs the drawing operation for all {@link IRenderer children}
     * of this renderer.
     *
     * @param drawContext the context (canvas, document, etc) of this drawing operation.
     */
    public void drawChildren(DrawContext drawContext) {
        List<IRenderer> waitingRenderers = new ArrayList<>();
        for (IRenderer child : childRenderers) {
            Transform transformProp = child.<Transform>getProperty(Property.TRANSFORM);
            Border outlineProp = child.<Border>getProperty(Property.OUTLINE);
            RootRenderer rootRenderer = getRootRenderer();
            List<IRenderer> waiting = (rootRenderer != null && !rootRenderer.waitingDrawingElements.contains(child)) ? rootRenderer.waitingDrawingElements : waitingRenderers;
            processWaitingDrawing(child, transformProp, outlineProp, waiting);
            if (!FloatingHelper.isRendererFloating(child) && transformProp == null) {
                child.draw(drawContext);
            }
        }
        for (IRenderer waitingRenderer : waitingRenderers) {
            waitingRenderer.draw(drawContext);
        }
    }

    static void processWaitingDrawing(IRenderer child, Transform transformProp, Border outlineProp, List<IRenderer> waitingDrawing) {
        if (FloatingHelper.isRendererFloating(child) || transformProp != null) {
            waitingDrawing.add(child);
        }
        if (outlineProp != null && child instanceof AbstractRenderer) {
            AbstractRenderer abstractChild = (AbstractRenderer) child;
            if (abstractChild.isRelativePosition())
                abstractChild.applyRelativePositioningTranslation(false);
            Div outlines = new Div();
            outlines.setRole(null);
            if (transformProp != null)
                outlines.setProperty(Property.TRANSFORM, transformProp);
            outlines.setProperty(Property.BORDER, outlineProp);
            float offset = outlines.<Border>getProperty(Property.BORDER).getWidth();
            if (abstractChild.getPropertyAsFloat(Property.OUTLINE_OFFSET) != null)
                offset += (float) abstractChild.getPropertyAsFloat(Property.OUTLINE_OFFSET);
            DivRenderer div = new DivRenderer(outlines);
            Rectangle divOccupiedArea = abstractChild.applyMargins(abstractChild.occupiedArea.clone().getBBox(), false).moveLeft(offset).moveDown(offset);
            divOccupiedArea.setWidth(divOccupiedArea.getWidth() + 2 * offset).setHeight(divOccupiedArea.getHeight() + 2 * offset);
            div.occupiedArea = new LayoutArea(abstractChild.getOccupiedArea().getPageNumber(), divOccupiedArea);
            float outlineWidth = div.<Border>getProperty(Property.BORDER).getWidth();
            if (divOccupiedArea.getWidth() >= outlineWidth * 2 && divOccupiedArea.getHeight() >= outlineWidth * 2) {
                waitingDrawing.add(div);
            }
            if (abstractChild.isRelativePosition())
                abstractChild.applyRelativePositioningTranslation(true);
        }
    }

    /**
     * Performs the drawing operation for the border of this renderer, if
     * defined by any of the {@link Property#BORDER} values in either the layout
     * element or this {@link IRenderer} itself.
     *
     * @param drawContext the context (canvas, document, etc) of this drawing operation.
     */
    public void drawBorder(DrawContext drawContext) {
        Border[] borders = getBorders();
        boolean gotBorders = false;

        for (Border border : borders)
            gotBorders = gotBorders || border != null;

        if (gotBorders) {
            float topWidth = borders[0] != null ? borders[0].getWidth() : 0;
            float rightWidth = borders[1] != null ? borders[1].getWidth() : 0;
            float bottomWidth = borders[2] != null ? borders[2].getWidth() : 0;
            float leftWidth = borders[3] != null ? borders[3].getWidth() : 0;

            Rectangle bBox = getBorderAreaBBox();
            if (bBox.getWidth() < 0 || bBox.getHeight() < 0) {
                Logger logger = LoggerFactory.getLogger(AbstractRenderer.class);
                logger.error(MessageFormatUtil.format(LogMessageConstant.RECTANGLE_HAS_NEGATIVE_SIZE, "border"));
                return;
            }
            float x1 = bBox.getX();
            float y1 = bBox.getY();
            float x2 = bBox.getX() + bBox.getWidth();
            float y2 = bBox.getY() + bBox.getHeight();

            boolean isTagged = drawContext.isTaggingEnabled() && getModelElement() instanceof IAccessibleElement;
            PdfCanvas canvas = drawContext.getCanvas();
            if (isTagged) {
                canvas.openTag(new CanvasArtifact());
            }

            boolean isAreaClipped = clipBorderArea(drawContext, applyMargins(occupiedArea.getBBox().clone(), getMargins(), false));
            UnitValue borderRadius = this.<UnitValue>getProperty(Property.BORDER_RADIUS);
            float radius = 0;
            if (null != borderRadius) {
                if (borderRadius.isPercentValue()) {
                    Logger logger = LoggerFactory.getLogger(BlockRenderer.class);
                    logger.error(MessageFormatUtil.format(LogMessageConstant.PROPERTY_IN_PERCENTS_NOT_SUPPORTED, "border-radius"));
                } else {
                    radius = borderRadius.getValue();
                }
            }

            if (0 == radius) {
                if (borders[0] != null) {
                    borders[0].draw(canvas, x1, y2, x2, y2, Border.Side.TOP, leftWidth, rightWidth);
                }
                if (borders[1] != null) {
                    borders[1].draw(canvas, x2, y2, x2, y1, Border.Side.RIGHT, topWidth, bottomWidth);
                }
                if (borders[2] != null) {
                    borders[2].draw(canvas, x2, y1, x1, y1, Border.Side.BOTTOM, rightWidth, leftWidth);
                }
                if (borders[3] != null) {
                    borders[3].draw(canvas, x1, y1, x1, y2, Border.Side.LEFT, bottomWidth, topWidth);
                }
            } else {
                if (borders[0] != null) {
                    borders[0].draw(canvas, x1, y2, x2, y2, radius, Border.Side.TOP, leftWidth, rightWidth);
                }
                if (borders[1] != null) {
                    borders[1].draw(canvas, x2, y2, x2, y1, radius, Border.Side.RIGHT, topWidth, bottomWidth);
                }
                if (borders[2] != null) {
                    borders[2].draw(canvas, x2, y1, x1, y1, radius, Border.Side.BOTTOM, rightWidth, leftWidth);
                }
                if (borders[3] != null) {
                    borders[3].draw(canvas, x1, y1, x1, y2, radius, Border.Side.LEFT, bottomWidth, topWidth);
                }
            }

            if (isAreaClipped) {
                drawContext.getCanvas().restoreState();
            }

            if (isTagged) {
                canvas.closeTag();
            }
        }
    }

    /**
     * Indicates whether this renderer is flushed or not, i.e. if {@link #draw(DrawContext)} has already
     * been called.
     *
     * @return whether the renderer has been flushed
     * @see #draw
     */
    @Override
    public boolean isFlushed() {
        return flushed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IRenderer setParent(IRenderer parent) {
        this.parent = parent;
        return this;
    }

    /**
     * Gets the parent of this {@link IRenderer}, if previously set by {@link #setParent(IRenderer)}
     *
     * @return parent of the renderer
     */
    public IRenderer getParent() {
        return parent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void move(float dxRight, float dyUp) {
        occupiedArea.getBBox().moveRight(dxRight);
        occupiedArea.getBBox().moveUp(dyUp);
        for (IRenderer childRenderer : childRenderers) {
            childRenderer.move(dxRight, dyUp);
        }
        for (IRenderer childRenderer : positionedRenderers) {
            childRenderer.move(dxRight, dyUp);
        }
    }

    /**
     * Gets all rectangles that this {@link IRenderer} can draw upon in the given area.
     *
     * @param area a physical area on the {@link DrawContext}
     * @return a list of {@link Rectangle rectangles}
     */
    public List<Rectangle> initElementAreas(LayoutArea area) {
        return Collections.singletonList(area.getBBox());
    }

    /**
     * Gets the bounding box that contains all content written to the
     * {@link DrawContext} by this {@link IRenderer}.
     *
     * @return the smallest {@link Rectangle} that surrounds the content
     */
    public Rectangle getOccupiedAreaBBox() {
        return occupiedArea.getBBox().clone();
    }

    /**
     * Gets the border box of a renderer.
     * This is a box used to draw borders.
     *
     * @return border box of a renderer
     */
    public Rectangle getBorderAreaBBox() {
        Rectangle rect = getOccupiedAreaBBox();
        applyMargins(rect, false);
        applyBorderBox(rect, false);
        return rect;
    }

    public Rectangle getInnerAreaBBox() {
        Rectangle rect = getOccupiedAreaBBox();
        applyMargins(rect, false);
        applyBorderBox(rect, false);
        applyPaddings(rect, false);
        return rect;
    }

    protected void applyDestinationsAndAnnotation(DrawContext drawContext) {
        applyDestination(drawContext.getDocument());
        applyAction(drawContext.getDocument());
        applyLinkAnnotation(drawContext.getDocument());
    }

    static boolean isBorderBoxSizing(IRenderer renderer) {
        BoxSizingPropertyValue boxSizing = renderer.<BoxSizingPropertyValue>getProperty(Property.BOX_SIZING);
        return boxSizing != null && boxSizing.equals(BoxSizingPropertyValue.BORDER_BOX);
    }

    /**
     * Retrieves element's fixed content box width, if it's set.
     * Takes into account {@link Property#BOX_SIZING}, {@link Property#MIN_WIDTH},
     * and {@link Property#MAX_WIDTH} properties.
     * @param parentBoxWidth width of the parent element content box.
     *                       If element has relative width, it will be
     *                       calculated relatively to this parameter.
     * @return element's fixed content box width or null if it's not set.
     * @see AbstractRenderer#hasAbsoluteUnitValue(int)
     */
    protected Float retrieveWidth(float parentBoxWidth) {
        Float minWidth = retrieveUnitValue(parentBoxWidth, Property.MIN_WIDTH);

        Float maxWidth = retrieveUnitValue(parentBoxWidth, Property.MAX_WIDTH);
        if (maxWidth != null && minWidth != null && minWidth > maxWidth) {
            maxWidth = minWidth;
        }

        Float width = retrieveUnitValue(parentBoxWidth, Property.WIDTH);
        if (width != null) {
            if (maxWidth != null) {
                width = width > maxWidth ? maxWidth : width;
            }
            if (minWidth != null) {
                width = width < minWidth ? minWidth : width;
            }
        } else if (maxWidth != null) {
            width = maxWidth < parentBoxWidth ? maxWidth : null;
        }

        if (width != null && isBorderBoxSizing(this)) {
            width -= calculatePaddingBorderWidth(this);
        }

        return width != null ? (Float) Math.max(0, (float) width) : null;
    }

    /**
     * Retrieves element's fixed content box max width, if it's set.
     * Takes into account {@link Property#BOX_SIZING} and {@link Property#MIN_WIDTH} properties.
     * @param parentBoxWidth width of the parent element content box.
     *                       If element has relative width, it will be
     *                       calculated relatively to this parameter.
     * @return element's fixed content box max width or null if it's not set.
     * @see AbstractRenderer#hasAbsoluteUnitValue(int)
     */
    protected Float retrieveMaxWidth(float parentBoxWidth) {
        Float maxWidth = retrieveUnitValue(parentBoxWidth, Property.MAX_WIDTH);
        if (maxWidth != null) {
            Float minWidth = retrieveUnitValue(parentBoxWidth, Property.MIN_WIDTH);
            if (minWidth != null && minWidth > maxWidth) {
                maxWidth = minWidth;
            }

            if (isBorderBoxSizing(this)) {
                maxWidth -= calculatePaddingBorderWidth(this);
            }
            return maxWidth > 0 ? maxWidth : 0;
        } else {
            return null;
        }
    }

    /**
     * Retrieves element's fixed content box max width, if it's set.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @param parentBoxWidth width of the parent element content box.
     *                       If element has relative width, it will be
     *                       calculated relatively to this parameter.
     * @return element's fixed content box max width or null if it's not set.
     * @see AbstractRenderer#hasAbsoluteUnitValue(int)
     */
    protected Float retrieveMinWidth(float parentBoxWidth) {
        Float minWidth = retrieveUnitValue(parentBoxWidth, Property.MIN_WIDTH);
        if (minWidth != null) {
            if (isBorderBoxSizing(this)) {
                minWidth -= calculatePaddingBorderWidth(this);
            }
            return minWidth > 0 ? minWidth : 0;
        } else {
            return null;
        }
    }

    /**
     * Updates fixed content box width value for this renderer.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @param updatedWidthValue element's new fixed content box width.
     */
    void updateWidth(UnitValue updatedWidthValue) {
        if (updatedWidthValue.isPointValue() && isBorderBoxSizing(this)) {
            updatedWidthValue.setValue(updatedWidthValue.getValue() + calculatePaddingBorderWidth(this));
        }
        setProperty(Property.WIDTH, updatedWidthValue);
    }

    /**
     * Retrieves element's fixed content box height, if it's set.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @return element's fixed content box height or null if it's not set.
     */
    protected Float retrieveHeight() {
        Float height = this.<Float>getProperty(Property.HEIGHT);
        if (height != null && isBorderBoxSizing(this)) {
            height = Math.max(0, (float)height - calculatePaddingBorderHeight(this));
        }
        return height;
    }

    /**
     * Updates fixed content box height value for this renderer.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @param updatedHeightValue element's new fixed content box height, shall be not null.
     */
    void updateHeight(Float updatedHeightValue) {
        if (isBorderBoxSizing(this)) {
            updatedHeightValue += calculatePaddingBorderHeight(this);
        }
        setProperty(Property.HEIGHT, updatedHeightValue);
    }

    /**
     * Retrieves element's content box max-height, if it's set.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @return element's content box max-height or null if it's not set.
     */
    protected Float retrieveMaxHeight() {
        Float maxHeight = this.<Float>getProperty(Property.MAX_HEIGHT);
        if (maxHeight != null && isBorderBoxSizing(this)) {
            maxHeight = Math.max(0, (float)maxHeight - calculatePaddingBorderHeight(this));
        }
        return maxHeight;
    }

    /**
     * Updates content box max-height value for this renderer.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @param updatedMaxHeightValue element's new content box max-height, shall be not null.
     */
    void updateMaxHeight(Float updatedMaxHeightValue) {
        if (isBorderBoxSizing(this)) {
            updatedMaxHeightValue += calculatePaddingBorderHeight(this);
        }
        setProperty(Property.MAX_HEIGHT, updatedMaxHeightValue);
    }

    /**
     * Retrieves element's content box max-height, if it's set.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @return element's content box min-height or null if it's not set.
     */
    protected Float retrieveMinHeight() {
        Float minHeight = this.<Float>getProperty(Property.MIN_HEIGHT);
        if (minHeight != null && isBorderBoxSizing(this)) {
            minHeight = Math.max(0, (float)minHeight - calculatePaddingBorderHeight(this));
        }
        return minHeight;
    }

    /**
     * Updates content box min-height value for this renderer.
     * Takes into account {@link Property#BOX_SIZING} property value.
     * @param updatedMinHeightValue element's new content box min-height, shall be not null.
     */
    void updateMinHeight(Float updatedMinHeightValue) {
        if (isBorderBoxSizing(this)) {
            updatedMinHeightValue += calculatePaddingBorderHeight(this);
        }
        setProperty(Property.MIN_HEIGHT, updatedMinHeightValue);
    }

    protected Float retrieveUnitValue(float basePercentValue, int property) {
        UnitValue value = this.<UnitValue>getProperty(property);
        if (value != null) {
            if (value.getUnitType() == UnitValue.PERCENT) {
                return value.getValue() * basePercentValue / 100;
            } else {
                assert value.getUnitType() == UnitValue.POINT;
                return value.getValue();
            }
        } else {
            return null;
        }
    }

    //TODO is behavior of copying all properties in split case common to all renderers?
    protected Map<Integer, Object> getOwnProperties() {
        return properties;
    }

    protected void addAllProperties(Map<Integer, Object> properties) {
        this.properties.putAll(properties);
    }

    /**
     * Gets the first yLine of the nested children recursively. E.g. for a list, this will be the yLine of the
     * first item (if the first item is indeed a paragraph).
     * NOTE: this method will no go further than the first child.
     * @return the first yline of the nested children, null if there is no text found
     */
    protected Float getFirstYLineRecursively() {
        if (childRenderers.size() == 0) {
            return null;
        }
        return ((AbstractRenderer) childRenderers.get(0)).getFirstYLineRecursively();
    }

    protected Float getLastYLineRecursively() {
        for (int i = childRenderers.size() - 1; i >= 0; i--) {
            IRenderer child = childRenderers.get(i);
            if (child instanceof AbstractRenderer) {
                Float lastYLine = ((AbstractRenderer) child).getLastYLineRecursively();
                if (lastYLine != null) {
                    return lastYLine;
                }
            }
        }
        return null;
    }

    /**
     * Applies margins of the renderer on the given rectangle
     *
     * @param rect    a rectangle margins will be applied on.
     * @param reverse indicates whether margins will be applied
     *                inside (in case of false) or outside (in case of true) the rectangle.
     * @return a {@link Rectangle border box} of the renderer
     * @see #getMargins
     */
    protected Rectangle applyMargins(Rectangle rect, boolean reverse) {
        return this.applyMargins(rect, getMargins(), reverse);
    }

    /**
     * Applies given margins on the given rectangle
     *
     * @param rect    a rectangle margins will be applied on.
     * @param margins the margins to be applied on the given rectangle
     * @param reverse indicates whether margins will be applied
     *                inside (in case of false) or outside (in case of true) the rectangle.
     * @return a {@link Rectangle border box} of the renderer
     */
    protected Rectangle applyMargins(Rectangle rect, float[] margins, boolean reverse) {
        return rect.<Rectangle>applyMargins(margins[0], margins[1], margins[2], margins[3], reverse);
    }

    /**
     * Returns margins of the renderer
     *
     * @return a {@code float[]} margins of the renderer
     */
    protected float[] getMargins() {
        return getMargins(this);
    }

    /**
     * Returns paddings of the renderer
     *
     * @return a {@code float[]} paddings of the renderer
     */
    protected float[] getPaddings() {
        return getPaddings(this);
    }

    /**
     * Applies paddings of the renderer on the given rectangle
     *
     * @param rect    a rectangle paddings will be applied on.
     * @param reverse indicates whether paddings will be applied
     *                inside (in case of false) or outside (in case of false) the rectangle.
     * @return a {@link Rectangle border box} of the renderer
     * @see #getPaddings
     */
    protected Rectangle applyPaddings(Rectangle rect, boolean reverse) {
        return applyPaddings(rect, getPaddings(), reverse);
    }

    /**
     * Applies given paddings on the given rectangle
     *
     * @param rect     a rectangle paddings will be applied on.
     * @param paddings the paddings to be applied on the given rectangle
     * @param reverse  indicates whether paddings will be applied
     *                 inside (in case of false) or outside (in case of false) the rectangle.
     * @return a {@link Rectangle border box} of the renderer
     */
    protected Rectangle applyPaddings(Rectangle rect, float[] paddings, boolean reverse) {
        return rect.<Rectangle>applyMargins(paddings[0], paddings[1], paddings[2], paddings[3], reverse);
    }

    /**
     * Applies the border box of the renderer on the given rectangle
     * If the border of a certain side is null, the side will remain as it was.
     *
     * @param rect    a rectangle the border box will be applied on.
     * @param reverse indicates whether the border box will be applied
     *                inside (in case of false) or outside (in case of false) the rectangle.
     * @return a {@link Rectangle border box} of the renderer
     * @see #getBorders
     */
    protected Rectangle applyBorderBox(Rectangle rect, boolean reverse) {
        Border[] borders = getBorders();
        return applyBorderBox(rect, borders, reverse);
    }

    /**
     * Applies the given border box (borders) on the given rectangle
     *
     * @param rect    a rectangle paddings will be applied on.
     * @param borders the {@link Border borders} to be applied on the given rectangle
     * @param reverse indicates whether the border box will be applied
     *                inside (in case of false) or outside (in case of false) the rectangle.
     * @return a {@link Rectangle border box} of the renderer
     */
    protected Rectangle applyBorderBox(Rectangle rect, Border[] borders, boolean reverse) {
        float topWidth = borders[0] != null ? borders[0].getWidth() : 0;
        float rightWidth = borders[1] != null ? borders[1].getWidth() : 0;
        float bottomWidth = borders[2] != null ? borders[2].getWidth() : 0;
        float leftWidth = borders[3] != null ? borders[3].getWidth() : 0;
        return rect.<Rectangle>applyMargins(topWidth, rightWidth, bottomWidth, leftWidth, reverse);
    }

    protected void applyAbsolutePosition(Rectangle parentRect) {
        Float top = this.getPropertyAsFloat(Property.TOP);
        Float bottom = this.getPropertyAsFloat(Property.BOTTOM);
        Float left = this.getPropertyAsFloat(Property.LEFT);
        Float right = this.getPropertyAsFloat(Property.RIGHT);

        if (left == null && right == null && BaseDirection.RIGHT_TO_LEFT.equals(this.<BaseDirection>getProperty(Property.BASE_DIRECTION))) {
            right = 0f;
        }

        if (top == null && bottom == null) {
            top = 0f;
        }

        try {
            if (right != null) {
                move(parentRect.getRight() - (float) right - occupiedArea.getBBox().getRight(), 0);
            }

            if (left != null) {
                move(parentRect.getLeft() + (float) left - occupiedArea.getBBox().getLeft(), 0);
            }

            if (top != null) {
                move(0, parentRect.getTop() - (float) top - occupiedArea.getBBox().getTop());
            }

            if (bottom != null) {
                move(0, parentRect.getBottom() + (float) bottom - occupiedArea.getBBox().getBottom());
            }
        } catch (Exception exc) {
            Logger logger = LoggerFactory.getLogger(AbstractRenderer.class);
            logger.error(MessageFormatUtil.format(LogMessageConstant.OCCUPIED_AREA_HAS_NOT_BEEN_INITIALIZED, "Absolute positioning might be applied incorrectly."));
        }
    }

    protected void applyRelativePositioningTranslation(boolean reverse) {
        float top = (float) this.getPropertyAsFloat(Property.TOP, 0f);
        float bottom = (float) this.getPropertyAsFloat(Property.BOTTOM, 0f);
        float left = (float) this.getPropertyAsFloat(Property.LEFT, 0f);
        float right = (float) this.getPropertyAsFloat(Property.RIGHT, 0f);

        int reverseMultiplier = reverse ? -1 : 1;

        float dxRight = left != 0 ? left * reverseMultiplier : -right * reverseMultiplier;
        float dyUp = top != 0 ? -top * reverseMultiplier : bottom * reverseMultiplier;

        if (dxRight != 0 || dyUp != 0)
            move(dxRight, dyUp);
    }

    protected void applyDestination(PdfDocument document) {
        String destination = this.<String>getProperty(Property.DESTINATION);
        if (destination != null) {
            PdfArray array = new PdfArray();
            array.add(document.getPage(occupiedArea.getPageNumber()).getPdfObject());
            array.add(PdfName.XYZ);
            array.add(new PdfNumber(occupiedArea.getBBox().getX()));
            array.add(new PdfNumber(occupiedArea.getBBox().getY() + occupiedArea.getBBox().getHeight()));
            array.add(new PdfNumber(0));
            document.addNamedDestination(destination, array.makeIndirect(document));

            deleteProperty(Property.DESTINATION);
        }
    }

    protected void applyAction(PdfDocument document) {
        PdfAction action = this.<PdfAction>getProperty(Property.ACTION);
        if (action != null) {
            PdfLinkAnnotation link = this.<PdfLinkAnnotation>getProperty(Property.LINK_ANNOTATION);
            if (link == null) {
                link = (PdfLinkAnnotation) new PdfLinkAnnotation(new Rectangle(0, 0, 0, 0)).setFlags(PdfAnnotation.PRINT);
                Border border = this.<Border>getProperty(Property.BORDER);
                if (border != null) {
                    link.setBorder(new PdfArray(new float[]{0, 0, border.getWidth()}));
                } else {
                    link.setBorder(new PdfArray(new float[]{0, 0, 0}));
                }
                setProperty(Property.LINK_ANNOTATION, link);
            }
            link.setAction(action);
        }
    }

    protected void applyLinkAnnotation(PdfDocument document) {
        PdfLinkAnnotation linkAnnotation = this.<PdfLinkAnnotation>getProperty(Property.LINK_ANNOTATION);
        if (linkAnnotation != null) {
            Rectangle pdfBBox = calculateAbsolutePdfBBox();
            linkAnnotation.setRectangle(new PdfArray(pdfBBox));

            PdfPage page = document.getPage(occupiedArea.getPageNumber());
            page.addAnnotation(linkAnnotation);
        }
    }

    protected void updateHeightsOnSplit(boolean wasHeightClipped, AbstractRenderer splitRenderer, AbstractRenderer overflowRenderer) {
        Float maxHeight = retrieveMaxHeight();
        if (maxHeight != null) {
            overflowRenderer.updateMaxHeight(maxHeight - occupiedArea.getBBox().getHeight());
        }
        Float minHeight = retrieveMinHeight();
        if (minHeight != null) {
            overflowRenderer.updateMinHeight(minHeight - occupiedArea.getBBox().getHeight());
        }
        Float height = retrieveHeight();
        if (height != null) {
            overflowRenderer.updateHeight(height - occupiedArea.getBBox().getHeight());
        }
        if (wasHeightClipped) {
            Logger logger = LoggerFactory.getLogger(BlockRenderer.class);
            logger.warn(LogMessageConstant.CLIP_ELEMENT);
            splitRenderer.occupiedArea.getBBox()
                    .moveDown((float)maxHeight - occupiedArea.getBBox().getHeight())
                    .setHeight((float)maxHeight);
        }
    }


    protected MinMaxWidth getMinMaxWidth(float availableWidth) {
        return MinMaxWidthUtils.countDefaultMinMaxWidth(this, availableWidth);
    }

    protected boolean setMinMaxWidthBasedOnFixedWidth(MinMaxWidth minMaxWidth) {
        // retrieve returns max width, if there is no width.
        if (hasAbsoluteUnitValue(Property.WIDTH)) {
            //Renderer may override retrieveWidth, double check is required.
            Float width = retrieveWidth(0);
            if (width != null) {
                minMaxWidth.setChildrenMaxWidth((float) width);
                minMaxWidth.setChildrenMinWidth((float) width);
                return true;
            }
        }
        return false;
    }

    protected boolean isNotFittingHeight(LayoutArea layoutArea) {
        return !isPositioned() && occupiedArea.getBBox().getHeight() > layoutArea.getBBox().getHeight();
    }

    protected boolean isNotFittingWidth(LayoutArea layoutArea) {
        return !isPositioned() && occupiedArea.getBBox().getWidth() > layoutArea.getBBox().getWidth();
    }

    protected boolean isNotFittingLayoutArea(LayoutArea layoutArea) {
        return isNotFittingHeight(layoutArea) || isNotFittingWidth(layoutArea);
    }

    /**
     * Indicates whether the renderer's position is fixed or not.
     *
     * @return a {@code boolean}
     */
    protected boolean isPositioned() {
        return !isStaticLayout();
    }

    /**
     * Indicates whether the renderer's position is fixed or not.
     *
     * @return a {@code boolean}
     */
    protected boolean isFixedLayout() {
        Object positioning = this.<Object>getProperty(Property.POSITION);
        return Integer.valueOf(LayoutPosition.FIXED).equals(positioning);
    }

    protected boolean isStaticLayout() {
        Object positioning = this.<Object>getProperty(Property.POSITION);
        return positioning == null || Integer.valueOf(LayoutPosition.STATIC).equals(positioning);
    }

    protected boolean isRelativePosition() {
        Integer positioning = this.getPropertyAsInteger(Property.POSITION);
        return Integer.valueOf(LayoutPosition.RELATIVE).equals(positioning);
    }

    protected boolean isAbsolutePosition() {
        Integer positioning = this.getPropertyAsInteger(Property.POSITION);
        return Integer.valueOf(LayoutPosition.ABSOLUTE).equals(positioning);
    }

    protected boolean isKeepTogether() {
        return Boolean.TRUE.equals(getPropertyAsBoolean(Property.KEEP_TOGETHER));
    }

    @Deprecated
    protected void alignChildHorizontally(IRenderer childRenderer, float availableWidth) {
        HorizontalAlignment horizontalAlignment = childRenderer.<HorizontalAlignment>getProperty(Property.HORIZONTAL_ALIGNMENT);
        if (horizontalAlignment != null && horizontalAlignment != HorizontalAlignment.LEFT) {
            float freeSpace = availableWidth - childRenderer.getOccupiedArea().getBBox().getWidth();

            switch (horizontalAlignment) {
                case RIGHT:
                    childRenderer.move(freeSpace, 0);
                    break;
                case CENTER:
                    childRenderer.move(freeSpace / 2, 0);
                    break;
            }
        }
    }

    // Note! The second parameter is here on purpose. Currently occupied area is passed as a value of this parameter in
    // BlockRenderer, but actually, the block can have many areas, and occupied area will be the common area of sub-areas,
    // whereas child element alignment should be performed area-wise.
    protected void alignChildHorizontally(IRenderer childRenderer, Rectangle currentArea) {
        float availableWidth = currentArea.getWidth();
        HorizontalAlignment horizontalAlignment = childRenderer.<HorizontalAlignment>getProperty(Property.HORIZONTAL_ALIGNMENT);
        if (horizontalAlignment != null && horizontalAlignment != HorizontalAlignment.LEFT) {
            float freeSpace = availableWidth - childRenderer.getOccupiedArea().getBBox().getWidth();
            if (freeSpace > 0) {
                try {
                    switch (horizontalAlignment) {
                        case RIGHT:
                            childRenderer.move(freeSpace, 0);
                            break;
                        case CENTER:
                            childRenderer.move(freeSpace / 2, 0);
                            break;
                    }
                } catch (Exception e) { // TODO Review exception type when DEVSIX-1592 is resolved.
                    Logger logger = LoggerFactory.getLogger(AbstractRenderer.class);
                    logger.error(MessageFormatUtil.format(LogMessageConstant.OCCUPIED_AREA_HAS_NOT_BEEN_INITIALIZED, "Some of the children might not end up aligned horizontally."));
                }
            }
        }
    }

    /**
     * Gets borders of the element in the specified order: top, right, bottom, left.
     *
     * @return an array of BorderDrawer objects.
     * In case when certain border isn't set <code>Property.BORDER</code> is used,
     * and if <code>Property.BORDER</code> is also not set then <code>null</code> is returned
     * on position of this border
     */
    protected Border[] getBorders() {
        return getBorders(this);
    }

    protected AbstractRenderer setBorders(Border border, int borderNumber) {
        switch (borderNumber) {
            case 0:
                setProperty(Property.BORDER_TOP, border);
                break;
            case 1:
                setProperty(Property.BORDER_RIGHT, border);
                break;
            case 2:
                setProperty(Property.BORDER_BOTTOM, border);
                break;
            case 3:
                setProperty(Property.BORDER_LEFT, border);
                break;
        }

        return this;
    }

    /**
     * Calculates the bounding box of the content in the coordinate system of the pdf entity on which content is placed,
     * e.g. document page or form xObject. This is particularly useful for the cases when element is nested in the rotated
     * element.
     *
     * @return a {@link Rectangle} which is a bbox of the content not relative to the parent's layout area but rather to
     * the some pdf entity coordinate system.
     */
    protected Rectangle calculateAbsolutePdfBBox() {
        Rectangle contentBox = getOccupiedAreaBBox();
        List<Point> contentBoxPoints = rectangleToPointsList(contentBox);
        AbstractRenderer renderer = this;
        while (renderer.parent != null) {
            if (renderer instanceof BlockRenderer) {
                Float angle = renderer.<Float>getProperty(Property.ROTATION_ANGLE);
                if (angle != null) {
                    BlockRenderer blockRenderer = (BlockRenderer) renderer;
                    AffineTransform rotationTransform = blockRenderer.createRotationTransformInsideOccupiedArea();
                    transformPoints(contentBoxPoints, rotationTransform);
                }
            }

            if (renderer.<Transform>getProperty(Property.TRANSFORM) != null) {
                if (renderer instanceof BlockRenderer || renderer instanceof ImageRenderer || renderer instanceof TableRenderer) {
                    AffineTransform rotationTransform = renderer.createTransformationInsideOccupiedArea();
                    transformPoints(contentBoxPoints, rotationTransform);
                }
            }
            renderer = (AbstractRenderer) renderer.parent;
        }

        return calculateBBox(contentBoxPoints);
    }

    /**
     * Calculates bounding box around points.
     *
     * @param points list of the points calculated bbox will enclose.
     * @return array of float values which denote left, bottom, right, top lines of bbox in this specific order
     */
    protected Rectangle calculateBBox(List<Point> points) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Point p : points) {
            minX = Math.min(p.getX(), minX);
            minY = Math.min(p.getY(), minY);
            maxX = Math.max(p.getX(), maxX);
            maxY = Math.max(p.getY(), maxY);
        }
        return new Rectangle((float) minX, (float) minY, (float) (maxX - minX), (float) (maxY - minY));
    }

    protected List<Point> rectangleToPointsList(Rectangle rect) {
        List<Point> points = new ArrayList<>();
        points.addAll(Arrays.asList(new Point(rect.getLeft(), rect.getBottom()), new Point(rect.getRight(), rect.getBottom()),
                new Point(rect.getRight(), rect.getTop()), new Point(rect.getLeft(), rect.getTop())));
        return points;
    }

    protected List<Point> transformPoints(List<Point> points, AffineTransform transform) {
        for (Point point : points) {
            transform.transform(point, point);
        }

        return points;
    }

    /**
     * This method calculates the shift needed to be applied to the points in order to position
     * upper and left borders of their bounding box at the given lines.
     *
     * @param left   x coordinate at which points bbox left border is to be aligned
     * @param top    y coordinate at which points bbox upper border is to be aligned
     * @param points the points, which bbox will be aligned at the given position
     * @return array of two floats, where first element denotes x-coordinate shift and the second
     * element denotes y-coordinate shift which are needed to align points bbox at the given lines.
     */
    protected float[] calculateShiftToPositionBBoxOfPointsAt(float left, float top, List<Point> points) {
        double minX = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Point point : points) {
            minX = Math.min(point.getX(), minX);
            maxY = Math.max(point.getY(), maxY);
        }

        float dx = (float) (left - minX);
        float dy = (float) (top - maxY);
        return new float[]{dx, dy};
    }

    protected void overrideHeightProperties() {
        Float height = getPropertyAsFloat(Property.HEIGHT);
        Float maxHeight = getPropertyAsFloat(Property.MAX_HEIGHT);
        Float minHeight = getPropertyAsFloat(Property.MIN_HEIGHT);
        if (null != height) {
            if (null == maxHeight || height < maxHeight) {
                maxHeight = height;
            } else {
                height = maxHeight;
            }
            if (null == minHeight || height > minHeight) {
                minHeight = height;
            }
        }
        if (null != maxHeight && null != minHeight && minHeight > maxHeight) {
            maxHeight = minHeight;
        }
        if (null != maxHeight) {
            setProperty(Property.MAX_HEIGHT, maxHeight);
        }
        if (null != minHeight) {
            setProperty(Property.MIN_HEIGHT, minHeight);
        }
    }

    /**
     * Check if corresponding property has point value.
     *
     * @param property {@link Property}
     * @return false if property value either null, or percent, otherwise true.
     */
    protected boolean hasAbsoluteUnitValue(int property) {
        UnitValue value = this.<UnitValue>getProperty(property);
        return value != null && value.isPointValue();
    }

    boolean isFirstOnRootArea() {
        boolean isFirstOnRootArea = true;
        AbstractRenderer ancestor = this;
        while (isFirstOnRootArea && ancestor.getParent() != null) {
            IRenderer parent = ancestor.getParent();
            if (parent instanceof RootRenderer) {
                isFirstOnRootArea = ((RootRenderer) parent).getCurrentArea().isEmptyArea();
            } else {
                isFirstOnRootArea = parent.getOccupiedArea().getBBox().getHeight() < EPS;
            }
            if (!(parent instanceof AbstractRenderer)) {
                break;
            }
            ancestor = (AbstractRenderer) parent;
        }
        return isFirstOnRootArea;
    }

    RootRenderer getRootRenderer() {
        IRenderer currentRenderer = this;
        while (currentRenderer instanceof AbstractRenderer) {
            if (currentRenderer instanceof RootRenderer) {
                return (RootRenderer) currentRenderer;
            }
            currentRenderer = ((AbstractRenderer) currentRenderer).getParent();
        }
        return null;
    }

    static float calculateAdditionalWidth(AbstractRenderer renderer) {
        Rectangle dummy = new Rectangle(0, 0);
        renderer.applyMargins(dummy, true);
        renderer.applyBorderBox(dummy, true);
        renderer.applyPaddings(dummy, true);
        return dummy.getWidth();
    }

    static boolean noAbsolutePositionInfo(IRenderer renderer) {
        return !renderer.hasProperty(Property.TOP) && !renderer.hasProperty(Property.BOTTOM) && !renderer.hasProperty(Property.LEFT) && !renderer.hasProperty(Property.RIGHT);
    }

    static Float getPropertyAsFloat(IRenderer renderer, int property) {
        return NumberUtil.asFloat(renderer.<Object>getProperty(property));
    }

    static void applyGeneratedAccessibleAttributes(TagTreePointer tagPointer, PdfDictionary attributes) {
        if (attributes == null) {
            return;
        }

        // TODO if taggingPointer.getProperties will always write directly to struct elem, use it instead (add addAttributes overload with index)
        PdfStructElem structElem = tagPointer.getDocument().getTagStructureContext().getPointerStructElem(tagPointer);
        PdfObject structElemAttr = structElem.getAttributes(false);
        if (structElemAttr == null || !structElemAttr.isDictionary() && !structElemAttr.isArray()) {
            structElem.setAttributes(attributes);
        } else if (structElemAttr.isDictionary()) {
            PdfArray attrArr = new PdfArray();
            attrArr.add(attributes);
            attrArr.add(structElemAttr);
            structElem.setAttributes(attrArr);
        } else { // isArray
            PdfArray attrArr = (PdfArray) structElemAttr;
            attrArr.add(0, attributes);
        }
    }

    void shrinkOccupiedAreaForAbsolutePosition() {
        // In case of absolute positioning and not specified left, right, width values, the parent box is shrunk to fit
        // the children. It does not occupy all the available width if it does not need to.
        if (isAbsolutePosition()) {
            Float left = this.getPropertyAsFloat(Property.LEFT);
            Float right = this.getPropertyAsFloat(Property.RIGHT);
            UnitValue width = this.<UnitValue>getProperty(Property.WIDTH);
            if (left == null && right == null && width == null) {
                occupiedArea.getBBox().setWidth(0);
            }
        }
    }

    void drawPositionedChildren(DrawContext drawContext) {
        for (IRenderer positionedChild : positionedRenderers) {
            positionedChild.draw(drawContext);
        }
    }

    FontCharacteristics createFontCharacteristics() {
        FontCharacteristics fc = new FontCharacteristics();
        if (this.hasProperty(Property.FONT_WEIGHT)) {
            fc.setFontWeight((String) this.<Object>getProperty(Property.FONT_WEIGHT));
        }
        if (this.hasProperty(Property.FONT_STYLE)) {
            fc.setFontStyle((String) this.<Object>getProperty(Property.FONT_STYLE));
        }
        return fc;
    }

    // This method is intended to get first valid PdfFont in this renderer, based of font property.
    // It is usually done for counting some layout characteristics like ascender or descender.
    // NOTE: It neither change Font Property of renderer, nor is guarantied to contain all glyphs used in renderer.
    PdfFont resolveFirstPdfFont() {
        Object font = this.<Object>getProperty(Property.FONT);
        if (font instanceof PdfFont) {
            return (PdfFont) font;
        } else if (font instanceof String) {
            FontProvider provider = this.<FontProvider>getProperty(Property.FONT_PROVIDER);
            if (provider == null) {
                throw new IllegalStateException("Invalid font type. FontProvider expected. Cannot resolve font with string value");
            }
            FontCharacteristics fc = createFontCharacteristics();
            return resolveFirstPdfFont((String) font, provider, fc);
        } else {
            throw new IllegalStateException("String or PdfFont expected as value of FONT property");
        }
    }

    // This method is intended to get first valid PdfFont described in font string,
    // with specific FontCharacteristics with the help of specified font provider.
    // This method is intended to be called from previous method that deals with Font Property.
    // NOTE: It neither change Font Property of renderer, nor is guarantied to contain all glyphs used in renderer.
    // TODO this mechanism does not take text into account
    PdfFont resolveFirstPdfFont(String font, FontProvider provider, FontCharacteristics fc) {
        return provider.getPdfFont(provider.getFontSelector(FontFamilySplitter.splitFontFamily(font), fc).bestMatch());
    }

    void applyAbsolutePositionIfNeeded(LayoutContext layoutContext) {
        if (isAbsolutePosition()) {
            applyAbsolutePosition(layoutContext instanceof PositionedLayoutContext ? ((PositionedLayoutContext) layoutContext).getParentOccupiedArea().getBBox() : layoutContext.getArea().getBBox());
        }
    }

    void preparePositionedRendererAndAreaForLayout(IRenderer childPositionedRenderer, Rectangle fullBbox, Rectangle parentBbox) {
        Float left = getPropertyAsFloat(childPositionedRenderer, Property.LEFT);
        Float right = getPropertyAsFloat(childPositionedRenderer, Property.RIGHT);
        Float top = getPropertyAsFloat(childPositionedRenderer, Property.TOP);
        Float bottom = getPropertyAsFloat(childPositionedRenderer, Property.BOTTOM);
        childPositionedRenderer.setParent(this);
        adjustPositionedRendererLayoutBoxWidth(childPositionedRenderer, fullBbox, left, right);

        if (Integer.valueOf(LayoutPosition.ABSOLUTE).equals(childPositionedRenderer.<Integer>getProperty(Property.POSITION))) {
            updateMinHeightForAbsolutelyPositionedRenderer(childPositionedRenderer, parentBbox, top, bottom);
        }
    }

    private void updateMinHeightForAbsolutelyPositionedRenderer(IRenderer renderer, Rectangle parentRendererBox, Float top, Float bottom) {
        if (top != null && bottom != null && !renderer.hasProperty(Property.HEIGHT)) {
            Float currentMaxHeight = getPropertyAsFloat(renderer, Property.MAX_HEIGHT);
            Float currentMinHeight = getPropertyAsFloat(renderer, Property.MIN_HEIGHT);
            float resolvedMinHeight = Math.max(0, parentRendererBox.getTop() - (float) top - parentRendererBox.getBottom() - (float) bottom);

            Rectangle dummy = new Rectangle(0, 0);
            if (!isBorderBoxSizing(renderer)) {
                applyPaddings(dummy, getPaddings(renderer), true);
                applyBorderBox(dummy, getBorders(renderer), true);
            }
            applyMargins(dummy, getMargins(renderer), true);
            resolvedMinHeight -= dummy.getHeight();

            if (currentMinHeight != null) {
                resolvedMinHeight = Math.max(resolvedMinHeight, (float) currentMinHeight);
            }
            if (currentMaxHeight != null) {
                resolvedMinHeight = Math.min(resolvedMinHeight, (float) currentMaxHeight);
            }

            renderer.setProperty(Property.MIN_HEIGHT, resolvedMinHeight);
        }
    }

    private void adjustPositionedRendererLayoutBoxWidth(IRenderer renderer, Rectangle fullBbox, Float left, Float right) {
        if (left != null) {
            fullBbox.setWidth(fullBbox.getWidth() - (float) left).setX(fullBbox.getX() + (float) left);
        }
        if (right != null) {
            fullBbox.setWidth(fullBbox.getWidth() - (float) right);
        }

        if (left == null && right == null && !renderer.hasProperty(Property.WIDTH)) {
            // Other, non-block renderers won't occupy full width anyway
            MinMaxWidth minMaxWidth = renderer instanceof BlockRenderer ? ((BlockRenderer) renderer).getMinMaxWidth(MinMaxWidthUtils.getMax()) : null;
            if (minMaxWidth != null && minMaxWidth.getMaxWidth() < fullBbox.getWidth()) {
                fullBbox.setWidth(minMaxWidth.getMaxWidth() + AbstractRenderer.EPS);
            }
        }
    }

    private static float calculatePaddingBorderWidth(AbstractRenderer renderer) {
        Rectangle dummy = new Rectangle(0, 0);
        renderer.applyBorderBox(dummy, true);
        renderer.applyPaddings(dummy, true);
        return dummy.getWidth();
    }

    private static float calculatePaddingBorderHeight(AbstractRenderer renderer) {
        Rectangle dummy = new Rectangle(0, 0);
        renderer.applyBorderBox(dummy, true);
        renderer.applyPaddings(dummy, true);
        return dummy.getHeight();
    }

    /**
     * This method creates {@link AffineTransform} instance that could be used
     * to transform content inside the occupied area,
     * considering the centre of the occupiedArea as the origin of a coordinate system for transformation.
     *
     * @return {@link AffineTransform} that transforms the content and places it inside occupied area.
     */
    private AffineTransform createTransformationInsideOccupiedArea() {
        Rectangle backgroundArea = applyMargins(occupiedArea.clone().getBBox(), false);
        float x = backgroundArea.getX();
        float y = backgroundArea.getY();
        float height = backgroundArea.getHeight();
        float width = backgroundArea.getWidth();

        AffineTransform transform = AffineTransform.getTranslateInstance(-1 * (x + width / 2), -1 * (y + height / 2));
        transform.preConcatenate(Transform.getAffineTransform(this.<Transform>getProperty(Property.TRANSFORM), width, height));
        transform.preConcatenate(AffineTransform.getTranslateInstance(x + width / 2, y + height / 2));

        return transform;
    }

    protected void beginTranformationIfApplied(PdfCanvas canvas) {
        if (this.<Transform>getProperty(Property.TRANSFORM) != null) {
            AffineTransform transform = createTransformationInsideOccupiedArea();
            canvas.saveState().concatMatrix(transform);
        }
    }

    protected void endTranformationIfApplied(PdfCanvas canvas) {
        if (this.<Transform>getProperty(Property.TRANSFORM) != null) {
            canvas.restoreState();
        }
    }

    private static float[] getMargins(IRenderer renderer) {
        return new float[]{(float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.MARGIN_TOP)), (float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.MARGIN_RIGHT)),
                (float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.MARGIN_BOTTOM)), (float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.MARGIN_LEFT))};
    }

    private static Border[] getBorders(IRenderer renderer) {
        Border border = renderer.<Border>getProperty(Property.BORDER);
        Border topBorder = renderer.<Border>getProperty(Property.BORDER_TOP);
        Border rightBorder = renderer.<Border>getProperty(Property.BORDER_RIGHT);
        Border bottomBorder = renderer.<Border>getProperty(Property.BORDER_BOTTOM);
        Border leftBorder = renderer.<Border>getProperty(Property.BORDER_LEFT);

        Border[] borders = {topBorder, rightBorder, bottomBorder, leftBorder};

        if (!hasOwnOrModelProperty(renderer, Property.BORDER_TOP)) {
            borders[0] = border;
        }
        if (!hasOwnOrModelProperty(renderer, Property.BORDER_RIGHT)) {
            borders[1] = border;
        }
        if (!hasOwnOrModelProperty(renderer, Property.BORDER_BOTTOM)) {
            borders[2] = border;
        }
        if (!hasOwnOrModelProperty(renderer, Property.BORDER_LEFT)) {
            borders[3] = border;
        }

        return borders;
    }

    private static float[] getPaddings(IRenderer renderer) {
        return new float[]{(float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.PADDING_TOP)), (float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.PADDING_RIGHT)),
                (float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.PADDING_BOTTOM)), (float) NumberUtil.asFloat(renderer.<Object>getProperty(Property.PADDING_LEFT))};
    }

    private static boolean hasOwnOrModelProperty(IRenderer renderer, int property) {
        return renderer.hasOwnProperty(property) || (null != renderer.getModelElement() && renderer.getModelElement().hasProperty(property));
    }
}
