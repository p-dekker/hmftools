package com.hartwig.hmftools.common.hotspot;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.hartwig.hmftools.common.numeric.Doubles;
import com.hartwig.hmftools.common.position.GenomePosition;
import com.hartwig.hmftools.common.position.GenomePositions;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFilterHeaderLine;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

public class HotspotEvidenceVCF {

    private final static String PASS = "PASS";
    private final static String ALLELIC_FREQUENCY = "AF";
    private final static String HOTSPOT_FLAG = "HOTSPOT";
    private final static String GERMLINE_INDEL = "GERMLINE_INDEL";
    private final static String GERMLINE_HET_LIKELIHOOD = "GHBL";
    private final static String LOW_CONFIDENCE = "LOW_CONFIDENCE";

    private final VCFHeader header;
    private final String tumorSample;
    private final String normalSample;

    private final double maxNormalHetLikelihood;
    private final int minTumorReads;
    private final double minHotspotVAF;
    private final double minInframeVAF;
    private final int minHotspotQuality;
    private final int minInframeQuality;

    public HotspotEvidenceVCF(@NotNull final String normalSample, @NotNull final String tumorSample, final double maxNormalHetLikelihood,
            final int minTumorReads, final double minHotspotVAF, final double minInframeVAF, final int minHotspotQuality,
            final int minInframeQuality) {
        this.tumorSample = tumorSample;
        this.normalSample = normalSample;
        this.maxNormalHetLikelihood = maxNormalHetLikelihood;
        this.minTumorReads = minTumorReads;
        this.minHotspotVAF = minHotspotVAF;
        this.minInframeVAF = minInframeVAF;
        this.minHotspotQuality = minHotspotQuality;
        this.minInframeQuality = minInframeQuality;

        this.header = header(normalSample, tumorSample);
    }

    public void write(@NotNull final String filename, @NotNull final List<HotspotEvidence> evidenceList) {
        final VariantContextWriter writer =
                new VariantContextWriterBuilder().setOutputFile(filename).modifyOption(Options.INDEX_ON_THE_FLY, false).build();
        writer.setHeader(header);
        writer.writeHeader(header);

        final ListMultimap<GenomePosition, HotspotEvidence> evidenceMap = Multimaps.index(evidenceList, GenomePositions::create);
        for (GenomePosition site : evidenceMap.keySet()) {
            final List<HotspotEvidence> evidence = evidenceMap.get(site);
            final VariantContext context = create(evidence);
            writer.add(context);
        }

        writer.close();
    }

    private boolean lowConfidence(@NotNull HotspotEvidence hotspotEvidence) {
        if (hotspotEvidence.normalAltCount() > 1 || hotspotEvidence.tumorAltCount() < minTumorReads) {
            return true;
        }

        if (hotspotEvidence.type() == HotspotEvidenceType.INFRAME) {
            return hotspotEvidence.qualityScore() < minInframeQuality || Doubles.lessThan(hotspotEvidence.vaf(), minInframeVAF);
        }

        if (hotspotEvidence.type() == HotspotEvidenceType.KNOWN) {
            return hotspotEvidence.qualityScore() < minHotspotQuality || Doubles.lessThan(hotspotEvidence.vaf(), minHotspotVAF);
        }

        return false;
    }

    private static boolean germlineIndel(@NotNull HotspotEvidence hotspotEvidence) {
        return hotspotEvidence.isIndel() && hotspotEvidence.normalIndelCount() > 0;
    }

    @VisibleForTesting
    @NotNull
    VariantContext create(@NotNull final Collection<HotspotEvidence> evidence) {
        assert (!evidence.isEmpty());

        List<HotspotEvidence> sortedEvidence = Lists.newArrayList(evidence);
        sortedEvidence.sort(HotspotEvidenceVCF::compareEvidence);

        final HotspotEvidence hotspotEvidence = sortedEvidence.get(0);

        final Allele ref = Allele.create(hotspotEvidence.ref(), true);
        final Allele alt = Allele.create(hotspotEvidence.alt(), false);
        final List<Allele> alleles = Lists.newArrayList(ref, alt);

        final Genotype tumor = new GenotypeBuilder(tumorSample).DP(hotspotEvidence.tumorReads())
                .AD(new int[] { hotspotEvidence.tumorRefCount(), hotspotEvidence.tumorAltCount() })
                .alleles(alleles)
                .make();

        final Genotype normal = new GenotypeBuilder(normalSample).DP(hotspotEvidence.normalReads())
                .AD(new int[] { hotspotEvidence.normalRefCount(), hotspotEvidence.normalAltCount() })
                .alleles(alleles)
                .make();

        final VariantContextBuilder builder = new VariantContextBuilder().chr(hotspotEvidence.chromosome())
                .start(hotspotEvidence.position())
                .attribute(HOTSPOT_FLAG, hotspotEvidence.type().toString().toLowerCase())
                .attribute(ALLELIC_FREQUENCY, round(hotspotEvidence.vaf()))
                .computeEndFromAlleles(alleles, (int) hotspotEvidence.position())
                .source(hotspotEvidence.type().toString())
                .genotypes(tumor, normal)
                .alleles(alleles);

        boolean lowConfidence = lowConfidence(hotspotEvidence);
        if (hotspotEvidence.normalAltCount() == 1) {
            double normalHetLikelihood = heterozygousLikelihood(hotspotEvidence.normalReads());
            builder.attribute(GERMLINE_HET_LIKELIHOOD, round(normalHetLikelihood));
            lowConfidence |= Doubles.greaterThan(normalHetLikelihood, maxNormalHetLikelihood);
        }

        if (lowConfidence) {
            builder.filter(LOW_CONFIDENCE);
        } else if (germlineIndel(hotspotEvidence)) {
            builder.filter(GERMLINE_INDEL);
        } else {
            builder.filter(PASS);
        }

        final VariantContext context = builder.make();
        context.getCommonInfo().setLog10PError(hotspotEvidence.qualityScore() / -10d);
        return context;
    }

    private static int compareEvidence(@NotNull final HotspotEvidence o1, @NotNull final HotspotEvidence o2) {
        int normalEvidence = Integer.compare(o1.normalAltCount(), o2.normalAltCount());
        return normalEvidence == 0 ? -Integer.compare(o1.qualityScore(), o2.qualityScore()) : normalEvidence;
    }

    static double heterozygousLikelihood(int readDepth) {
        return new BinomialDistribution(readDepth, 0.5).cumulativeProbability(1);
    }

    private static double round(double number) {
        double multiplier = Math.pow(10, 3);
        return Math.round(number * multiplier) / multiplier;
    }

    @NotNull
    private static VCFHeader header(@NotNull final String normalSample, @NotNull final String tumorSample) {
        VCFHeader header = new VCFHeader(Collections.emptySet(), Lists.newArrayList(normalSample, tumorSample));
        header.addMetaDataLine(new VCFFormatHeaderLine("GT", 1, VCFHeaderLineType.String, "Genotype"));
        header.addMetaDataLine(new VCFFormatHeaderLine("DP", 1, VCFHeaderLineType.Integer, "Read Depth"));
        header.addMetaDataLine(new VCFFormatHeaderLine("AD", VCFHeaderLineCount.R, VCFHeaderLineType.Integer, "Allelic Depth"));

        header.addMetaDataLine(new VCFInfoHeaderLine(ALLELIC_FREQUENCY,
                VCFHeaderLineCount.A,
                VCFHeaderLineType.Float,
                "Allelic Frequency"));
        header.addMetaDataLine(new VCFInfoHeaderLine(HOTSPOT_FLAG, 1, VCFHeaderLineType.String, "Hotspot Type: known, inframe"));
        header.addMetaDataLine(new VCFInfoHeaderLine(GERMLINE_HET_LIKELIHOOD,
                1,
                VCFHeaderLineType.Float,
                "Binomial estimation of likelihood that a single read in the germline is heterozygous"));

        header.addMetaDataLine(new VCFFilterHeaderLine(PASS, "All filters passed"));
        header.addMetaDataLine(new VCFFilterHeaderLine(LOW_CONFIDENCE,
                "Set if excessive germline reads or insufficient quality or tumor reads"));
        header.addMetaDataLine(new VCFFilterHeaderLine(GERMLINE_INDEL, "Set if inframe indel has any germline indels at that site."));

        return header;
    }

}
