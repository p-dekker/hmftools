package com.hartwig.hmftools.common.variant.consensus;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.function.Predicate;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.slicing.GenomeRegion;
import com.hartwig.hmftools.common.slicing.Slicer;
import com.hartwig.hmftools.common.slicing.SlicerFactory;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.VariantType;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class ConsensusRuleTest {

    private static final String CHROMOSOME = "1";

    @Test
    public void consensusRuleWorks() {
        final Slicer highConfidence = SlicerFactory.fromSingleGenomeRegion(region(100, 1000));
        final Slicer extremeConfidence = SlicerFactory.fromSingleGenomeRegion(region(500, 600));

        final ConsensusRule rule = ConsensusRule.fromSlicers(highConfidence, extremeConfidence);

        final List<SomaticVariant> variants = Lists.newArrayList(cosmicSNPVariantOnPositionWithCallers(300, 2),
                // KODU: Include
                dbsnpSNPVariantOnPositionWithCallers(400, 2), // KODU: Exclude
                dbsnpSNPVariantOnPositionWithCallers(550, 2), // KODU: Include
                dbsnpSNPVariantOnPositionWithCallers(2000, 4), // KODU: Include
                indelVariantOnPositionWithCallers(550, 1), // KODU: Include
                indelVariantOnPositionWithCallers(200, 1), // KODU: Exclude
                indelVariantOnPositionWithCallers(250, 2) // KODU: Include
        );

        final List<SomaticVariant> filtered = rule.removeUnreliableVariants(variants);
        assertEquals(5, filtered.size());
    }

    @Test
    public void updateFilterFlagWorks() {
        final String filtered = "FILTERED";
        final String pass = "PASS";

        final SomaticVariant variant1 = new SomaticVariant.Builder().filter(pass).chromosome("1").build();
        final SomaticVariant variant2 = new SomaticVariant.Builder().filter(filtered).chromosome("1").build();
        final SomaticVariant variant3 = new SomaticVariant.Builder().filter(pass).chromosome("2").build();

        final Predicate<SomaticVariant> filter = variant -> variant.chromosome().equals("2");

        final ConsensusRule consensusRule = new ConsensusRule(filter);

        final List<SomaticVariant> adjustedVariants = consensusRule.updateFilterFlagForUnreliableVariants(
                Lists.newArrayList(variant1, variant2, variant3));

        assertEquals(ConsensusRule.CONSENSUS_FILTERED, adjustedVariants.get(0).filter());
        assertEquals(filtered, adjustedVariants.get(1).filter());
        assertEquals(pass, adjustedVariants.get(2).filter());
    }

    @NotNull
    private static GenomeRegion region(final long start, final long end) {
        return new GenomeRegion(CHROMOSOME, start, end);
    }

    @NotNull
    private static SomaticVariant cosmicSNPVariantOnPositionWithCallers(long position, int numCallers) {
        final List<String> callers = Lists.newArrayList();
        for (int i = 0; i < numCallers; i++) {
            callers.add("any");
        }
        return new SomaticVariant.Builder().type(VariantType.SNP).chromosome(CHROMOSOME).position(position).callers(
                callers).cosmicID("any_id").build();
    }

    @NotNull
    private static SomaticVariant dbsnpSNPVariantOnPositionWithCallers(long position, int numCallers) {
        final List<String> callers = Lists.newArrayList();
        for (int i = 0; i < numCallers; i++) {
            callers.add("any");
        }
        return new SomaticVariant.Builder().type(VariantType.SNP).chromosome(CHROMOSOME).position(position).callers(
                callers).dnsnpID("any_id").build();
    }

    @NotNull
    private static SomaticVariant indelVariantOnPositionWithCallers(long position, int numCallers) {
        final List<String> callers = Lists.newArrayList();
        for (int i = 0; i < numCallers; i++) {
            callers.add("any");
        }
        return new SomaticVariant.Builder().type(VariantType.INDEL).chromosome(CHROMOSOME).position(position).callers(
                callers).build();
    }
}