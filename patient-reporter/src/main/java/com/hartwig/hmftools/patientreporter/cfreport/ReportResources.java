package com.hartwig.hmftools.patientreporter.cfreport;

import com.hartwig.hmftools.patientreporter.PatientReporterApplication;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.layout.Style;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Shared info, settings and resources for the report
 */
public final class ReportResources {

    private static final Logger LOGGER = LogManager.getLogger(CFReportWriter.class);

    public static final String HARTWIG_NAME = "Hartwig Medical Foundation";
    public static final String HARTWIG_ADDRESS = HARTWIG_NAME + ", Science Park 408, 1098XH Amsterdam";
    public static final String CONTACT_EMAIL_GENERAL = "info@hartwigmedicalfoundation.nl";
    public static final String CONTACT_EMAIL_QA = "qualitysystem@hartwigmedicalfoundation.nl";

    // PDF Document metadata
    public static final String METADATA_TITLE = "HMF Sequencing Report v" + PatientReporterApplication.VERSION;
    public static final String METADATA_AUTHOR = HARTWIG_NAME;
    public static final String DATE_TIME_FORMAT = "dd-MMM-yyyy";
    public static final String REPORT_DATE = new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date());

    // Page margins for normal content (so excluding header and footer) in pt
    public static final float PAGE_MARGIN_TOP = 150; // Top margin also excludes the chapter title, which is rendered in the header
    public static final float PAGE_MARGIN_LEFT = 55.5f;
    public static final float PAGE_MARGIN_RIGHT = 29;
    public static final float PAGE_MARGIN_BOTTOM = 62;


    public static final float CONTENT_WIDTH_NARROW = 330; // Width of the content on a narrow page (page with full sidepanel)
    public static final float CONTENT_WIDTH_WIDE = 510; // Width of the content on a narrow page (page without full sidepanel)

    // Color palette
    public static final DeviceRgb PALETTE_WHITE = new DeviceRgb(255, 255, 255);
    public static final DeviceRgb PALETTE_BLACK = new DeviceRgb(0, 0, 0);
    public static final DeviceRgb PALETTE_BLUE = new DeviceRgb(38, 90, 166);
    public static final DeviceRgb PALETTE_MID_BLUE = new DeviceRgb(110, 139, 189);
    public static final DeviceRgb PALETTE_RED = new DeviceRgb(232, 60, 55);
    public static final DeviceRgb PALETTE_CYAN = new DeviceRgb(0, 179, 233);
    public static final DeviceRgb PALETTE_DARK_GREY = new DeviceRgb(39, 47, 50);
    public static final DeviceRgb PALETTE_MID_GREY = new DeviceRgb(101, 106, 108);
    public static final DeviceRgb PALETTE_LIGHT_GREY = new DeviceRgb(205, 206, 207);
    public static final DeviceRgb PALETTE_PINK = new DeviceRgb(230, 21, 124);

    // Fonts
    private static String FONT_REGULAR_PATH = "fonts/nimbus-sans/NimbusSansL-Regular.ttf";
    private static String FONT_BOLD_PATH = "fonts/nimbus-sans/NimbusSansL-Bold.ttf";
    private static PdfFont fontRegular = null;
    private static PdfFont fontBold = null;

    private ReportResources() {}

    @NotNull
    public static final Text styledText(@NotNull String text, @NotNull Style style) {
        return new Text(text)
            .addStyle(style);
    }

    @NotNull
    public static final Paragraph styledParagraph(@NotNull String text, @NotNull Style style) {
        return styledParagraph(style)
                .add(text);
    }

    @NotNull
    public static final Paragraph styledParagraph(@NotNull Style style) {
        return new Paragraph()
                .addStyle(style);
    }

    public static final PdfFont getFontRegular() {
        if (fontRegular == null) {
            fontRegular = loadFont(FONT_REGULAR_PATH);
        }
        return fontRegular;
    }

    public static final PdfFont getFontBold() {
        if (fontBold == null) {
            fontBold = loadFont(FONT_BOLD_PATH);
        }
        return fontBold;
    }

    /**
     * Chapter title text style
     * @return
     */
    public static final Style chapterTitleStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(16)
                .setFontColor(ReportResources.PALETTE_BLUE)
                .setMarginTop(0);
    }

    /**
     * Section title text style
     * @return
     */
    public static final Style sectionTitleStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(11)
                .setFontColor(ReportResources.PALETTE_BLUE);
    }

    /**
     * Content table header style
     * @return
     */
    public static final Style tableHeaderStyle() {
        return new Style()
                .setFont(getFontRegular())
                .setFontSize(7)
                .setFontColor(ReportResources.PALETTE_MID_GREY);

    }

    /**
     * Content table body text style
     * @return
     */
    public static final Style tableContentStyle() {
        return new Style()
                .setFont(getFontRegular())
                .setFontSize(8)
                .setFontColor(ReportResources.PALETTE_DARK_GREY);
    }

    /**
     * Body text style for content on summary page
     * @return
     */
    public static final Style bodyTextStyle() {
        return new Style()
                .setFont(getFontRegular())
                .setFontSize(8)
                .setFontColor(ReportResources.PALETTE_BLACK);
    }

    /**
     * Heading style for content on "Report explanation" and "Sample details & disclaimers" page
     * @return
     */
    public static final Style smallBodyHeadingStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(10)
                .setFontColor(ReportResources.PALETTE_BLACK);
    }

    /**
     * Body text style for content on "Report explanation" and "Sample details & disclaimers" page
     * @return
     */
    public static final Style smallBodyTextStyle() {
        return new Style()
                .setFont(getFontRegular())
                .setFontSize(7)
                .setFontColor(ReportResources.PALETTE_BLACK);
    }

    /**
     * Emphasized body text style for content on "Report explanation" and "Sample details & disclaimers" page
     * @return
     */
    public static final Style smallBodyBoldTextStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(7)
                .setFontColor(ReportResources.PALETTE_BLACK);
    }

    /**
     * Text style for legends and footnotes
     * @return
     */
    public static final Style subTextStyle() {
        return new Style()
                .setFont(getFontRegular())
                .setFontSize(7)
                .setFontColor(ReportResources.PALETTE_BLACK);
    }

    /**
     * Text style for legends and footnotes
     * @return
     */
    public static final Style subTextBoldStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(7)
                .setFontColor(ReportResources.PALETTE_BLACK);
    }

    /**
     * Text style for data highlight (e.g. big numbers)
     * @return
     */
    public static final Style dataHighlightStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(11)
                .setFontColor(ReportResources.PALETTE_BLUE);
    }

    /**
     * Text style for data highlight (e.g. big numbers) when data
     * is not available
     *
     * @return
     */
    public static final Style dataHighlightNaStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(7)
                .setFontColor(ReportResources.PALETTE_BLUE);
    }

    /**
     * Text style for the page numbers
     * @return
     */
    public static final Style pageNumberStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(8)
                .setFontColor(ReportResources.PALETTE_BLUE);
    }

    /**
     * Text style for the labels in the side panel
     * @return
     */
    public static final Style sidepanelLabelStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(7)
                .setFontColor(ReportResources.PALETTE_WHITE);
    }

    /**
     * Text style for the content in the side panel
     * @return
     */
    public static final Style sidepanelValueStyle() {
        return new Style()
                .setFont(getFontBold())
                .setFontSize(11)
                .setFontColor(ReportResources.PALETTE_WHITE);
    }

    /**
     * Load image data from resource stream. Returns null when loading fails
     *
     * @param resourcePath
     * @return
     */
    public static final ImageData loadImageData(@NotNull String resourcePath) {

        try {

            byte[] data = loadResourceData(resourcePath);
            if (data == null) {
                throw new Exception("Failed to load image data from " + resourcePath);
            }

            return ImageDataFactory.create(data, true);

        } catch (Exception e) {

            LOGGER.warn(e.getMessage());
            return null;

        }

    }

    /**
     * Load byte array from resource
     *
     * @param resourcePath
     * @return
     * @throws IOException
     */
    public static final byte[] loadResourceData(String resourcePath) throws IOException {

        byte[] data = null;
        InputStream is = new ReportResources().getClass().getClassLoader().getResourceAsStream(resourcePath);
        data = new byte[is.available()];
        is.read(data);

        return data;

    }

    private static final PdfFont loadFont(String resourcePath) {
        try {
            return PdfFontFactory.createFont(resourcePath, PdfEncodings.IDENTITY_H);
        } catch (Exception e) {
            LOGGER.warn(e.getMessage());
            return null;
        }
    }

}
