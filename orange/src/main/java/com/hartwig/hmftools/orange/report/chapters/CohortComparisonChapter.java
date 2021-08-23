package com.hartwig.hmftools.orange.report.chapters;

import com.hartwig.hmftools.orange.algo.OrangeReport;
import com.hartwig.hmftools.orange.report.ReportResources;
import com.hartwig.hmftools.orange.report.util.ImageUtil;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.property.HorizontalAlignment;

import org.jetbrains.annotations.NotNull;

public class CohortComparisonChapter implements ReportChapter {

    @NotNull
    private final OrangeReport report;

    public CohortComparisonChapter(@NotNull final OrangeReport report) {
        this.report = report;
    }

    @NotNull
    @Override
    public String name() {
        return "Cohort Comparison";
    }

    @NotNull
    @Override
    public PageSize pageSize() {
        return PageSize.A4.rotate();
    }

    @Override
    public void render(@NotNull final Document document) {
        document.add(new Paragraph(name()).addStyle(ReportResources.chapterTitleStyle()));

        addCuppaSummaryPlot(document);
        addCuppaFeaturePlot(document);
    }

    private void addCuppaSummaryPlot(@NotNull Document document) {
        Image cuppaSummaryImage = ImageUtil.build(report.plots().cuppaSummaryPlot());
        cuppaSummaryImage.setMaxWidth(740);
        cuppaSummaryImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
        document.add(cuppaSummaryImage);
    }

    private void addCuppaFeaturePlot(@NotNull Document document) {
        if (report.plots().cuppaFeaturePlot() != null) {
            Image cuppaFeatureImage = ImageUtil.build(report.plots().cuppaFeaturePlot());
            cuppaFeatureImage.setMaxWidth(740);
            cuppaFeatureImage.setHorizontalAlignment(HorizontalAlignment.CENTER);
            document.add(cuppaFeatureImage);
        }
    }
}
