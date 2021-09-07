package com.hartwig.hmftools.virusinterpreter;

import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.metrics.WGSMetrics;
import com.hartwig.hmftools.common.metrics.WGSMetricsFile;
import com.hartwig.hmftools.common.purple.purity.PurityContext;
import com.hartwig.hmftools.common.purple.purity.PurityContextFile;
import com.hartwig.hmftools.common.virus.AnnotatedVirus;
import com.hartwig.hmftools.common.virus.ImmutableAnnotatedVirus;
import com.hartwig.hmftools.common.virus.VirusBreakend;
import com.hartwig.hmftools.common.virus.VirusBreakendQCStatus;
import com.hartwig.hmftools.virusinterpreter.algo.VirusBlacklistModel;
import com.hartwig.hmftools.virusinterpreter.algo.VirusWhitelistModel;
import com.hartwig.hmftools.virusinterpreter.taxonomy.TaxonomyDb;

import org.jetbrains.annotations.NotNull;

public class VirusInterpreterAlgo {

    @NotNull
    private final TaxonomyDb taxonomyDb;
    @NotNull
    private final VirusWhitelistModel virusWhitelistModel;
    @NotNull
    private final VirusBlacklistModel virusBlacklistModel;

    public VirusInterpreterAlgo(@NotNull final TaxonomyDb taxonomyDb, @NotNull final VirusWhitelistModel virusWhitelistModel,
            @NotNull final VirusBlacklistModel virusBlacklistModel) {
        this.taxonomyDb = taxonomyDb;
        this.virusWhitelistModel = virusWhitelistModel;
        this.virusBlacklistModel = virusBlacklistModel;
    }

    @NotNull
    public List<AnnotatedVirus> analyze(@NotNull List<VirusBreakend> virusBreakends, @NotNull String purplePurityTsv,
            @NotNull String purpleQcFile, @NotNull String tumorSampleWGSMetricsFile) throws IOException {
        double expectedClonalCoverage = calculateExpectedClonalCoverage(purplePurityTsv, purpleQcFile, tumorSampleWGSMetricsFile);

        List<AnnotatedVirus> annotatedViruses = Lists.newArrayList();
        for (VirusBreakend virusBreakend : virusBreakends) {
            String interpretation = virusWhitelistModel.interpretVirusSpecies(virusBreakend.taxidSpecies());

            int taxid = virusBreakend.referenceTaxid();
            annotatedViruses.add(ImmutableAnnotatedVirus.builder()
                    .taxid(taxid)
                    .name(taxonomyDb.lookupName(taxid))
                    .qcStatus(virusBreakend.qcStatus())
                    .integrations(virusBreakend.integrations())
                    .interpretation(interpretation)
                    .percentageCovered(virusBreakend.coverage())
                    .coverage(virusBreakend.meanDepth())
                    .expectedClonalCoverage(expectedClonalCoverage)
                    .reported(report(virusBreakend, expectedClonalCoverage))
                    .reportedSummary(virusWhitelistModel.displayVirusOnSummaryReport(taxid))
                    .build());
        }

        return annotatedViruses;
    }

    private static double calculateExpectedClonalCoverage(@NotNull String purplePurityTsv, @NotNull String purpleQcFile,
            @NotNull String tumorSampleWGSMetricsFile) throws IOException {
        PurityContext purityContext = PurityContextFile.readWithQC(purpleQcFile, purplePurityTsv);
        double ploidy = purityContext.bestFit().ploidy();
        double purity = purityContext.bestFit().purity();

        WGSMetrics metrics = WGSMetricsFile.read(tumorSampleWGSMetricsFile);
        double tumorMeanCoverage = metrics.meanCoverage();
        return tumorMeanCoverage * purity / ploidy;
    }

    private boolean report(@NotNull VirusBreakend virusBreakend, double expectedClonalCoverage) {
        double viralPercentageCovered = virusBreakend.coverage();
        double viralCoverage = virusBreakend.meanDepth();

        if (virusWhitelistModel.hasInterpretation(virusBreakend.taxidSpecies())) {
            if (virusBreakend.qcStatus() == VirusBreakendQCStatus.LOW_VIRAL_COVERAGE) {
                return false;
            }

            if (virusBreakend.integrations() == 0) {
                Integer minimalCoverage = virusWhitelistModel.nonIntegratedMinimalCoverage(virusBreakend.taxidSpecies());
                if (minimalCoverage != null) {
                    if (viralPercentageCovered <= minimalCoverage && viralCoverage <= expectedClonalCoverage) {
                        return false;
                    }
                }
            }

            if (virusBreakend.integrations() >= 1) {
                Integer minimalCoverage = virusWhitelistModel.integratedMinimalCoverage(virusBreakend.taxidSpecies());
                if (minimalCoverage != null) {
                    if (viralPercentageCovered <= minimalCoverage && viralCoverage <= expectedClonalCoverage) {
                        return false;
                    }
                }
            }
        }

        return !virusBlacklistModel.isBlacklisted(virusBreakend);
    }
}
