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
package com.itextpdf.layout.element;

import com.itextpdf.kernel.pdf.tagutils.IAccessibleElement;
import com.itextpdf.layout.property.OverflowPropertyValue;
import com.itextpdf.layout.property.Property;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.layout.property.VerticalAlignment;

/**
 * A {@link BlockElement} will try to take up as much horizontal space as
 * available to it on the canvas or page. The concept is comparable to the block
 * element in HTML. Also like in HTML, the visual representation of the object
 * can be delimited by padding, a border, and/or a margin.
 *
 * @param <T> the type of the implementation
 */
public abstract class BlockElement<T extends IElement> extends AbstractElement<T> implements IAccessibleElement, IBlockElement {

    /**
     * Creates a BlockElement.
     */
    protected BlockElement() {
    }

    @Override
    public <T1> T1 getDefaultProperty(int property) {
        switch (property) {
            case Property.OVERFLOW:
            case Property.OVERFLOW_X:
            case Property.OVERFLOW_Y:
                return (T1) (Object) OverflowPropertyValue.FIT;
            default:
                return super.<T1>getDefaultProperty(property);
        }
    }

    /**
     * Gets the current left margin width of the element.
     *
     * @return the left margin width, as a <code>float</code>
     */
    public Float getMarginLeft() {
        return this.<Float>getProperty(Property.MARGIN_LEFT);
    }

    /**
     * Sets the left margin width of the element.
     *
     * @param value the new left margin width
     * @return this element
     */
    public T setMarginLeft(float value) {
        setProperty(Property.MARGIN_LEFT, value);
        return (T) (Object) this;
    }

    /**
     * Gets the current right margin width of the element.
     *
     * @return the right margin width, as a <code>float</code>
     */
    public Float getMarginRight() {
        return this.<Float>getProperty(Property.MARGIN_RIGHT);
    }

    /**
     * Sets the right margin width of the element.
     *
     * @param value the new right margin width
     * @return this element
     */
    public T setMarginRight(float value) {
        setProperty(Property.MARGIN_RIGHT, value);
        return (T) (Object) this;
    }

    /**
     * Gets the current top margin width of the element.
     *
     * @return the top margin width, as a <code>float</code>
     */
    public Float getMarginTop() {
        return this.<Float>getProperty(Property.MARGIN_TOP);
    }

    /**
     * Sets the top margin width of the element.
     *
     * @param value the new top margin width
     * @return this element
     */
    public T setMarginTop(float value) {
        setProperty(Property.MARGIN_TOP, value);
        return (T) (Object) this;
    }

    /**
     * Gets the current bottom margin width of the element.
     *
     * @return the bottom margin width, as a <code>float</code>
     */
    public Float getMarginBottom() {
        return this.<Float>getProperty(Property.MARGIN_BOTTOM);
    }

    /**
     * Sets the bottom margin width of the element.
     *
     * @param value the new bottom margin width
     * @return this element
     */
    public T setMarginBottom(float value) {
        setProperty(Property.MARGIN_BOTTOM, value);
        return (T) (Object) this;
    }

    /**
     * Sets all margins around the element to the same width.
     *
     * @param commonMargin the new margin width
     * @return this element
     */
    public T setMargin(float commonMargin) {
        return setMargins(commonMargin, commonMargin, commonMargin, commonMargin);
    }

    /**
     * Sets the margins around the element to a series of new widths.
     *
     * @param marginTop    the new margin top width
     * @param marginRight  the new margin right width
     * @param marginBottom the new margin bottom width
     * @param marginLeft   the new margin left width
     * @return this element
     */
    public T setMargins(float marginTop, float marginRight, float marginBottom, float marginLeft) {
        setMarginTop(marginTop);
        setMarginRight(marginRight);
        setMarginBottom(marginBottom);
        setMarginLeft(marginLeft);
        return (T) (Object) this;
    }

    /**
     * Gets the current left padding width of the element.
     *
     * @return the left padding width, as a <code>float</code>
     */
    public Float getPaddingLeft() {
        return this.<Float>getProperty(Property.PADDING_LEFT);
    }

    /**
     * Sets the left padding width of the element.
     *
     * @param value the new left padding width
     * @return this element
     */
    public T setPaddingLeft(float value) {
        setProperty(Property.PADDING_LEFT, value);
        return (T) (Object) this;
    }

    /**
     * Gets the current right padding width of the element.
     *
     * @return the right padding width, as a <code>float</code>
     */
    public Float getPaddingRight() {
        return this.<Float>getProperty(Property.PADDING_RIGHT);
    }

    /**
     * Sets the right padding width of the element.
     *
     * @param value the new right padding width
     * @return this element
     */
    public T setPaddingRight(float value) {
        setProperty(Property.PADDING_RIGHT, value);
        return (T) (Object) this;
    }

    /**
     * Gets the current top padding width of the element.
     *
     * @return the top padding width, as a <code>float</code>
     */
    public Float getPaddingTop() {
        return this.<Float>getProperty(Property.PADDING_TOP);
    }

    /**
     * Sets the top padding width of the element.
     *
     * @param value the new top padding width
     * @return this element
     */
    public T setPaddingTop(float value) {
        setProperty(Property.PADDING_TOP, value);
        return (T) (Object) this;
    }

    /**
     * Gets the current bottom padding width of the element.
     *
     * @return the bottom padding width, as a <code>float</code>
     */
    public Float getPaddingBottom() {
        return this.<Float>getProperty(Property.PADDING_BOTTOM);
    }

    /**
     * Sets the bottom padding width of the element.
     *
     * @param value the new bottom padding width
     * @return this element
     */
    public T setPaddingBottom(float value) {
        setProperty(Property.PADDING_BOTTOM, value);
        return (T) (Object) this;
    }

    /**
     * Sets all paddings around the element to the same width.
     *
     * @param commonPadding the new padding width
     * @return this element
     */
    public T setPadding(float commonPadding) {
        return setPaddings(commonPadding, commonPadding, commonPadding, commonPadding);
    }

    /**
     * Sets the paddings around the element to a series of new widths.
     *
     * @param paddingTop    the new padding top width
     * @param paddingRight  the new padding right width
     * @param paddingBottom the new padding bottom width
     * @param paddingLeft   the new padding left width
     * @return this element
     */
    public T setPaddings(float paddingTop, float paddingRight, float paddingBottom, float paddingLeft) {
        setPaddingTop(paddingTop);
        setPaddingRight(paddingRight);
        setPaddingBottom(paddingBottom);
        setPaddingLeft(paddingLeft);
        return (T) (Object) this;
    }

    /**
     * Sets the vertical alignment of the element.
     *
     * @param verticalAlignment the vertical alignment setting
     * @return this element
     */
    public T setVerticalAlignment(VerticalAlignment verticalAlignment) {
        setProperty(Property.VERTICAL_ALIGNMENT, verticalAlignment);
        return (T) (Object) this;
    }

    /**
     * Sets a ratio which determines in which proportion will word spacing and character spacing
     * be applied when horizontal alignment is justified.
     *
     * @param ratio the ratio coefficient. It must be between 0 and 1, inclusive.
     *              It means that <b>ratio</b> part of the free space will
     *              be compensated by word spacing, and <b>1-ratio</b> part of the free space will
     *              be compensated by character spacing.
     *              If <b>ratio</b> is 1, additional character spacing will not be applied.
     *              If <b>ratio</b> is 0, additional word spacing will not be applied.
     * @return
     */
    public T setSpacingRatio(float ratio) {
        setProperty(Property.SPACING_RATIO, ratio);
        return (T) (Object) this;
    }

    /**
     * Returns whether the {@link BlockElement} should be kept together as much
     * as possible.
     *
     * @return the current value of the {@link Property#KEEP_TOGETHER} property
     */
    public Boolean isKeepTogether() {
        return this.<Boolean>getProperty(Property.KEEP_TOGETHER);
    }

    /**
     * Sets whether the {@link BlockElement} should be kept together as much
     * as possible.
     *
     * @param keepTogether the new value of the {@link Property#KEEP_TOGETHER} property
     * @return this element
     */
    public T setKeepTogether(boolean keepTogether) {
        setProperty(Property.KEEP_TOGETHER, keepTogether);
        return (T) (Object) this;
    }

    /**
     * Returns whether the end of this {@link BlockElement} and the start of the next sibling of this element
     * should be placed in the same area.
     *
     * @return the current value of the {@link Property#KEEP_WITH_NEXT} property
     */
    public Boolean isKeepWithNext() {
        return this.<Boolean>getProperty(Property.KEEP_WITH_NEXT);
    }

    /**
     * Sets whether the end of this {@link BlockElement} and the start of the next sibling of this element
     * should be placed in the same area.
     * Note that this will only work for high-level elements, i.e. elements added to the {@link com.itextpdf.layout.RootElement}.
     *
     * @param keepWithNext the new value of the {@link Property#KEEP_WITH_NEXT} property
     * @return this element
     */
    public T setKeepWithNext(boolean keepWithNext) {
        setProperty(Property.KEEP_WITH_NEXT, keepWithNext);
        return (T) (Object) this;
    }

    /**
     * Sets the rotation radAngle.
     *
     * @param angleInRadians the new rotation radAngle, as a <code>float</code>, in radians
     * @return this element
     */
    public T setRotationAngle(float angleInRadians) {
        setProperty(Property.ROTATION_ANGLE, angleInRadians);
        return (T) (Object) this;
    }

    /**
     * Sets the rotation angle.
     *
     * @param angleInRadians the new rotation angle, as a <code>double</code>, in radians
     * @return this element
     */
    public T setRotationAngle(double angleInRadians) {
        setProperty(Property.ROTATION_ANGLE, (float) angleInRadians);
        return (T) (Object) this;
    }

    @Override
    public T setHeight(float height) {
        super.setHeight(height);
        return (T) (Object) this;
    }

    public T setMaxHeight(float maxHeight) {
        setProperty(Property.MAX_HEIGHT, maxHeight);
        return (T) (Object) this;
    }

    public T setMinHeight(float minHeight) {
        setProperty(Property.MIN_HEIGHT, minHeight);
        return (T) (Object) this;
    }

    public T setMaxWidth(float maxWidth) {
        setProperty(Property.MAX_WIDTH, UnitValue.createPointValue(maxWidth));
        return (T) (Object) this;
    }

    public T setMinWidth(float minWidth) {
        setProperty(Property.MIN_WIDTH, UnitValue.createPointValue(minWidth));
        return (T) (Object) this;
    }

}
