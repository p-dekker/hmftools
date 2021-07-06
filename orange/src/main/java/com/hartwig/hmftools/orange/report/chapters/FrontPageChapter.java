package com.hartwig.hmftools.orange.report.chapters;

import java.net.MalformedURLException;
import java.util.Set;
import java.util.StringJoiner;

import com.hartwig.hmftools.common.doid.DoidNode;
import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.report.components.TableUtil;
import com.itextpdf.io.IOException;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.HorizontalAlignment;

import org.jetbrains.annotations.NotNull;

public class FrontPageChapter implements ReportChapter {

    @NotNull
    private final OrangeReport report;

    public FrontPageChapter(@NotNull final OrangeReport report) {
        this.report = report;
    }

    @NotNull
    @Override
    public String name() {
        return "Front Page";
    }

    @Override
    public void render(@NotNull Document document) {
        Table primaryTumorTable = TableUtil.createReportContentTable(new float[] { 1, 1 },
                new Cell[] { TableUtil.createHeaderCell("Configured Primary Tumor"), TableUtil.createHeaderCell("Cuppa Primary Tumor") });

        primaryTumorTable.addCell(TableUtil.createContentCell(toConfiguredTumorType(report.configuredPrimaryTumor())));
        primaryTumorTable.addCell(TableUtil.createContentCell(report.cuppaPrimaryTumor()));
        document.add(TableUtil.createWrappingReportTable(primaryTumorTable));

        Table purpleFitTable = TableUtil.createReportContentTable(new float[] { 1, 1, 1, 1 },
                new Cell[] { TableUtil.createHeaderCell("QC"), TableUtil.createHeaderCell("Purity"),
                        TableUtil.createHeaderCell("Ploidy"),
                        TableUtil.createHeaderCell("Fit Method") });

        purpleFitTable.addCell(TableUtil.createContentCell("PASS"));
        purpleFitTable.addCell(TableUtil.createContentCell("99% (97%-100%)"));
        purpleFitTable.addCell(TableUtil.createContentCell("3.1 (3.1 - 3.15)"));
        purpleFitTable.addCell(TableUtil.createContentCell("NORMAL"));
        document.add(purpleFitTable);

        String circosPath = report.plots().purpleCircosPlot();
        try {
            Image circosImage = new Image(ImageDataFactory.create(circosPath));
            circosImage.setMaxHeight(400);
            circosImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
            circosImage.setMarginBottom(8);
            document.add(circosImage);
        } catch (MalformedURLException e) {
            throw new IOException("Failed to read circos plot at " + circosPath);
        }
    }

    @NotNull
    private static String toConfiguredTumorType(@NotNull Set<DoidNode> nodes) {
        StringJoiner joiner = new StringJoiner(", ");

        for (DoidNode node : nodes) {
            joiner.add(node.doidTerm() + " (DOID " + node.doid() + ")");
        }

        return joiner.toString();
    }
}
