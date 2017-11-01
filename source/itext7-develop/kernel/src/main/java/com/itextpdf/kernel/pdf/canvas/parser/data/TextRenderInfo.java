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
package com.itextpdf.kernel.pdf.canvas.parser.data;

import com.itextpdf.io.LogMessageConstant;
import com.itextpdf.io.font.otf.GlyphLine;
import com.itextpdf.kernel.color.Color;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.LineSegment;
import com.itextpdf.kernel.geom.Matrix;
import com.itextpdf.kernel.geom.Vector;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.canvas.CanvasGraphicsState;
import com.itextpdf.kernel.pdf.canvas.CanvasTag;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * Provides information and calculations needed by render listeners
 * to display/evaluate text render operations.
 * <br><br>
 * This is passed between the {@link PdfCanvasProcessor} and
 * {@link IEventListener} objects as text rendering operations are
 * discovered
 */
public class TextRenderInfo implements IEventData {

    private final PdfString string;
    private String text = null;
    private final Matrix textToUserSpaceTransformMatrix;
    private CanvasGraphicsState gs;
    private float unscaledWidth = Float.NaN;
    private double[] fontMatrix = null;
    private boolean graphicsStateIsPreserved;

    /**
     * Hierarchy of nested canvas tags for the text from the most inner (nearest to text) tag to the most outer.
     */
    private final List<CanvasTag> canvasTagHierarchy;

    /**
     * Creates a new TextRenderInfo object
     *
     * @param str                the PDF string that should be displayed
     * @param gs                 the graphics state (note: at this time, this is not immutable, so don't cache it)
     * @param textMatrix         the text matrix at the time of the render operation
     * @param canvasTagHierarchy the marked content tags sequence, if available
     */
    public TextRenderInfo(PdfString str, CanvasGraphicsState gs, Matrix textMatrix, Stack<CanvasTag> canvasTagHierarchy) {
        this.string = str;
        this.textToUserSpaceTransformMatrix = textMatrix.multiply(gs.getCtm());
        this.gs = gs;
        this.canvasTagHierarchy = Collections.<CanvasTag>unmodifiableList(new ArrayList<>(canvasTagHierarchy));
        this.fontMatrix = gs.getFont().getFontMatrix();
    }

    /**
     * Used for creating sub-TextRenderInfos for each individual character
     *
     * @param parent           the parent TextRenderInfo
     * @param string           the content of a TextRenderInfo
     * @param horizontalOffset the unscaled horizontal offset of the character that this TextRenderInfo represents
     */
    private TextRenderInfo(TextRenderInfo parent, PdfString string, float horizontalOffset) {
        this.string = string;
        this.textToUserSpaceTransformMatrix = new Matrix(horizontalOffset, 0).multiply(parent.textToUserSpaceTransformMatrix);
        this.gs = parent.gs;
        this.canvasTagHierarchy = parent.canvasTagHierarchy;
        this.fontMatrix = gs.getFont().getFontMatrix();
    }

    /**
     * @return the text to render
     */
    public String getText() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        if (text == null) {
            GlyphLine gl = gs.getFont().decodeIntoGlyphLine(string);
            if (!isReversedChars()) {
                text = gl.toUnicodeString(gl.start, gl.end);
            } else {
                StringBuilder sb = new StringBuilder(gl.end - gl.start);
                for (int i = gl.end - 1; i >= gl.start; i--) {
                    sb.append(gl.get(i).getUnicodeChars());
                }
                text = sb.toString();
            }
        }
        return text;
    }

    /**
     * @return original PDF string
     */
    public PdfString getPdfString() {
        return string;
    }

    /**
     * Checks if the text belongs to a marked content sequence
     * with a given mcid.
     *
     * @param mcid a marked content id
     * @return true if the text is marked with this id
     */
    public boolean hasMcid(int mcid) {
        return hasMcid(mcid, false);
    }

    /**
     * Checks if the text belongs to a marked content sequence
     * with a given mcid.
     *
     * @param mcid                     a marked content id
     * @param checkTheTopmostLevelOnly indicates whether to check the topmost level of marked content stack only
     * @return true if the text is marked with this id
     */
    public boolean hasMcid(int mcid, boolean checkTheTopmostLevelOnly) {
        if (checkTheTopmostLevelOnly) {
            if (canvasTagHierarchy != null) {
                int infoMcid = getMcid();
                return infoMcid != -1 && infoMcid == mcid;
            }
        } else {
            for (CanvasTag tag : canvasTagHierarchy) {
                if (tag.hasMcid())
                    if (tag.getMcid() == mcid)
                        return true;
            }
        }
        return false;
    }

    /**
     * @return the marked content associated with the TextRenderInfo instance.
     */
    public int getMcid() {
        for (CanvasTag tag : canvasTagHierarchy) {
            if (tag.hasMcid()) {
                return tag.getMcid();
            }
        }
        return -1;
    }

    /**
     * Gets the baseline for the text (i.e. the line that the text 'sits' on)
     * This value includes the Rise of the draw operation - see {@link #getRise()} for the amount added by Rise
     *
     * @return the baseline line segment
     */
    public LineSegment getBaseline() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return getUnscaledBaselineWithOffset(0 + gs.getTextRise()).transformBy(textToUserSpaceTransformMatrix);
    }

    public LineSegment getUnscaledBaseline() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return getUnscaledBaselineWithOffset(0 + gs.getTextRise());
    }

    /**
     * Gets the ascentline for the text (i.e. the line that represents the topmost extent that a string of the current font could have)
     * This value includes the Rise of the draw operation - see {@link #getRise()} for the amount added by Rise
     *
     * @return the ascentline line segment
     */
    public LineSegment getAscentLine() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return getUnscaledBaselineWithOffset(getAscentDescent()[0] + gs.getTextRise()).transformBy(textToUserSpaceTransformMatrix);
    }

    /**
     * Gets the descentline for the text (i.e. the line that represents the bottom most extent that a string of the current font could have).
     * This value includes the Rise of the draw operation - see {@link #getRise()} for the amount added by Rise
     *
     * @return the descentline line segment
     */
    public LineSegment getDescentLine() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return getUnscaledBaselineWithOffset(getAscentDescent()[1] + gs.getTextRise()).transformBy(textToUserSpaceTransformMatrix);
    }

    /**
     * Getter for the font
     *
     * @return the font
     */
    public PdfFont getFont() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getFont();
    }

    /**
     * The rise represents how far above the nominal baseline the text should be rendered.  The {@link #getBaseline()}, {@link #getAscentLine()} and {@link #getDescentLine()} methods already include Rise.
     * This method is exposed to allow listeners to determine if an explicit rise was involved in the computation of the baseline (this might be useful, for example, for identifying superscript rendering)
     *
     * @return The Rise for the text draw operation, in user space units (Ts value, scaled to user space)
     */
    public float getRise() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        if (gs.getTextRise() == 0) return 0; // optimize the common case

        return convertHeightFromTextSpaceToUserSpace(gs.getTextRise());
    }

    /**
     * Provides detail useful if a listener needs access to the position of each individual glyph in the text render operation
     *
     * @return A list of {@link TextRenderInfo} objects that represent each glyph used in the draw operation. The next effect is if there was a separate Tj opertion for each character in the rendered string
     */
    public List<TextRenderInfo> getCharacterRenderInfos() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        List<TextRenderInfo> rslt = new ArrayList<>(string.getValue().length());
        PdfString[] strings = splitString(string);
        float totalWidth = 0;
        for (PdfString str : strings) {
            float[] widthAndWordSpacing = getWidthAndWordSpacing(str);
            TextRenderInfo subInfo = new TextRenderInfo(this, str, totalWidth);
            rslt.add(subInfo);
            totalWidth += (widthAndWordSpacing[0] * gs.getFontSize() + gs.getCharSpacing() + widthAndWordSpacing[1]) * (gs.getHorizontalScaling() / 100f);
        }
        for (TextRenderInfo tri : rslt)
            tri.getUnscaledWidth();
        return rslt;
    }

    /**
     * @return The width, in user space units, of a single space character in the current font
     */
    public float getSingleSpaceWidth() {
        return convertWidthFromTextSpaceToUserSpace(getUnscaledFontSpaceWidth());
    }

    /**
     * @return the text render mode that should be used for the text.  From the
     * PDF specification, this means:
     * <ul>
     * <li>0 = Fill text</li>
     * <li>1 = Stroke text</li>
     * <li>2 = Fill, then stroke text</li>
     * <li>3 = Invisible</li>
     * <li>4 = Fill text and add to path for clipping</li>
     * <li>5 = Stroke text and add to path for clipping</li>
     * <li>6 = Fill, then stroke text and add to path for clipping</li>
     * <li>7 = Add text to padd for clipping</li>
     * </ul>
     */
    public int getTextRenderMode() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getTextRenderingMode();
    }

    /**
     * @return the current fill color.
     */
    public Color getFillColor() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getFillColor();
    }

    /**
     * @return the current stroke color.
     */
    public Color getStrokeColor() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getStrokeColor();
    }

    public float getFontSize() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getFontSize();
    }

    public float getHorizontalScaling() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getHorizontalScaling();
    }

    public float getCharSpacing() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getCharSpacing();
    }

    public float getWordSpacing() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getWordSpacing();
    }

    public float getLeading() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        return gs.getLeading();
    }

    /**
     * Gets /ActualText tag entry value if this text chunk is marked content.
     *
     * @return /ActualText value or <code>null</code> if none found
     */
    public String getActualText() {
        String lastActualText = null;
        for (CanvasTag tag : canvasTagHierarchy) {
            lastActualText = tag.getActualText();
            if (lastActualText != null) {
                break;
            }
        }
        return lastActualText;
    }

    /**
     * Gets /E tag (expansion text) entry value if this text chunk is marked content.
     *
     * @return /E value or <code>null</code> if none found
     */
    public String getExpansionText() {
        String expansionText = null;
        for (CanvasTag tag : canvasTagHierarchy) {
            expansionText = tag.getExpansionText();
            if (expansionText != null) {
                break;
            }
        }
        return expansionText;
    }

    /**
     * Determines if the text represented by this {@link TextRenderInfo} instance is written in a text showing operator
     * wrapped by /ReversedChars marked content sequence
     *
     * @return <code>true</code> if this text block lies within /ReversedChars block, <code>false</code> otherwise
     */
    public boolean isReversedChars() {
        for (CanvasTag tag : canvasTagHierarchy) {
            if (tag != null) {
                if (PdfName.ReversedChars.equals(tag.getRole())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets hierarchy of the canvas tags that wraps given text.
     *
     * @return list of the wrapping canvas tags. The first tag is the innermost (nearest to the text).
     */
    public List<CanvasTag> getCanvasTagHierarchy() {
        return canvasTagHierarchy;
    }

    /**
     * @return the unscaled (i.e. in Text space) width of the text
     */
    public float getUnscaledWidth() {
        if (Float.isNaN(unscaledWidth))
            unscaledWidth = getPdfStringWidth(string, false);
        return unscaledWidth;
    }

    public boolean isGraphicsStatePreserved() {
        return graphicsStateIsPreserved;
    }

    public void preserveGraphicsState() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        this.graphicsStateIsPreserved = true;
        gs = new CanvasGraphicsState(gs);
    }

    public void releaseGraphicsState() {
        if (!graphicsStateIsPreserved) {
            gs = null;
        }
    }

    private LineSegment getUnscaledBaselineWithOffset(float yOffset) {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        // we need to correct the width so we don't have an extra character and word spaces at the end.  The extra character and word spaces
        // are important for tracking relative text coordinate systems, but should not be part of the baseline
        String unicodeStr = string.toUnicodeString();

        float correctedUnscaledWidth = getUnscaledWidth() - (gs.getCharSpacing() +
                (unicodeStr.length() > 0 && unicodeStr.charAt(unicodeStr.length() - 1) == ' ' ? gs.getWordSpacing() : 0)) * (gs.getHorizontalScaling() / 100f);

        return new LineSegment(new Vector(0, yOffset, 1), new Vector(correctedUnscaledWidth, yOffset, 1));
    }

    /**
     * @param width the width, in text space
     * @return the width in user space
     */
    private float convertWidthFromTextSpaceToUserSpace(float width) {
        LineSegment textSpace = new LineSegment(new Vector(0, 0, 1), new Vector(width, 0, 1));
        LineSegment userSpace = textSpace.transformBy(textToUserSpaceTransformMatrix);
        return userSpace.getLength();
    }

    /**
     * @param height the height, in text space
     * @return the height in user space
     */
    private float convertHeightFromTextSpaceToUserSpace(float height) {
        LineSegment textSpace = new LineSegment(new Vector(0, 0, 1), new Vector(0, height, 1));
        LineSegment userSpace = textSpace.transformBy(textToUserSpaceTransformMatrix);
        return userSpace.getLength();
    }

    /**
     * Calculates the width of a space character.  If the font does not define
     * a width for a standard space character \u0020, we also attempt to use
     * the width of \u00A0 (a non-breaking space in many fonts)
     *
     * @return the width of a single space character in text space units
     */
    private float getUnscaledFontSpaceWidth() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        char charToUse = ' ';
        if (gs.getFont().getWidth(charToUse) == 0) {
            return gs.getFont().getFontProgram().getAvgWidth() / 1000f;
        } else {
            return getStringWidth(String.valueOf(charToUse));
        }
    }

    /**
     * Gets the width of a String in text space units
     *
     * @param string the string that needs measuring
     * @return the width of a String in text space units
     */
    private float getStringWidth(String string) {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        float totalWidth = 0;
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            float w = (float) (gs.getFont().getWidth(c) * fontMatrix[0]);
            float wordSpacing = c == 32 ? gs.getWordSpacing() : 0f;
            totalWidth += (w * gs.getFontSize() + gs.getCharSpacing() + wordSpacing) * gs.getHorizontalScaling() / 100f;
        }
        return totalWidth;
    }

    /**
     * Gets the width of a PDF string in text space units
     *
     * @param string the string that needs measuring
     * @return the width of a String in text space units
     */
    private float getPdfStringWidth(PdfString string, boolean singleCharString) {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        if (singleCharString) {
            float[] widthAndWordSpacing = getWidthAndWordSpacing(string);
            return (widthAndWordSpacing[0] * gs.getFontSize() + gs.getCharSpacing() + widthAndWordSpacing[1]) * gs.getHorizontalScaling() / 100f;
        } else {
            float totalWidth = 0;
            for (PdfString str : splitString(string)) {
                totalWidth += getPdfStringWidth(str, true);
            }
            return totalWidth;
        }
    }

    /**
     * Calculates width and word spacing of a single character PDF string.
     * IMPORTANT: Shall ONLY be used for a single character pdf strings.
     *
     * @param string a character to calculate width.
     * @return array of 2 items: first item is a character width, second item is a calculated word spacing.
     */
    private float[] getWidthAndWordSpacing(PdfString string) {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        float[] result = new float[2];

        result[0] = (float) ((gs.getFont().getContentWidth(string) * fontMatrix[0]));
        result[1] = " ".equals(string.getValue()) ? gs.getWordSpacing() : 0;
        return result;
    }

    /**
     * Converts a single character string to char code.
     *
     * @param string single character string to convert to.
     * @return char code.
     */
    private int getCharCode(String string) {
        try {
            byte[] b = string.getBytes("UTF-16BE");
            int value = 0;
            for (int i = 0; i < b.length - 1; i++) {
                value += b[i] & 0xff;
                value <<= 8;
            }
            if (b.length > 0) {
                value += b[b.length - 1] & 0xff;
            }
            return value;
        } catch (UnsupportedEncodingException e) {
        }
        return 0;
    }

    /**
     * Split PDF string into array of single character PDF strings.
     *
     * @param string PDF string to be splitted.
     * @return splitted PDF string.
     */
    private PdfString[] splitString(PdfString string) {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        List<PdfString> strings = new ArrayList<>();
        String stringValue = string.getValue();
        for (int i = 0; i < stringValue.length(); i++) {
            PdfString newString = new PdfString(stringValue.substring(i, i + 1), string.getEncoding());

            String text = gs.getFont().decode(newString);
            if (text.length() == 0 && i < stringValue.length() - 1) {
                newString = new PdfString(stringValue.substring(i, i + 2), string.getEncoding());
                i++;
            }
            strings.add(newString);
        }
        return strings.toArray(new PdfString[strings.size()]);
    }

    private float[] getAscentDescent() {
        // check if graphics state was released
        if (null == gs) {
            throw new IllegalStateException(LogMessageConstant.GRAPHICS_STATE_WAS_DELETED);
        }
        float ascent = gs.getFont().getFontProgram().getFontMetrics().getTypoAscender();
        float descent = gs.getFont().getFontProgram().getFontMetrics().getTypoDescender();

        // If descent is positive, we consider it a bug and fix it
        if (descent > 0) {
            descent = -descent;
        }

        float scale = ascent - descent < 700 ? ascent - descent : 1000;
        descent = descent / scale * gs.getFontSize();
        ascent = ascent / scale * gs.getFontSize();
        return new float[]{ascent, descent};
    }
}
