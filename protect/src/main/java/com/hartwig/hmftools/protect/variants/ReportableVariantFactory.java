package com.hartwig.hmftools.protect.variants;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hartwig.hmftools.common.drivercatalog.DriverCatalog;
import com.hartwig.hmftools.common.variant.Hotspot;
import com.hartwig.hmftools.common.variant.SomaticVariant;
import com.hartwig.hmftools.common.variant.germline.ReportableGermlineVariant;
import com.hartwig.hmftools.protect.variants.germline.DriverGermlineVariant;
import com.hartwig.hmftools.protect.variants.germline.FilterGermlineVariants;
import com.hartwig.hmftools.protect.variants.germline.GermlineReportingModel;
import com.hartwig.hmftools.protect.variants.somatic.DriverSomaticVariant;

import org.jetbrains.annotations.NotNull;

public final class ReportableVariantFactory {

    private ReportableVariantFactory() {
    }

    @NotNull
    public static List<ReportableVariant> reportableGermlineVariants(@NotNull List<ReportableGermlineVariant> variants,
            @NotNull Set<String> genesWithSomaticInactivationEvent) {
        final Predicate<ReportableGermlineVariant> secondGermlineHit =
                variant -> variants.stream().anyMatch(x -> !x.equals(variant) && x.gene().equals(variant.gene()));

        final Predicate<ReportableGermlineVariant> report =
                variant -> FilterGermlineVariants.isPresentInTumor(variant) && (variant.biallelic()
                        || genesWithSomaticInactivationEvent.contains(variant.gene()) || secondGermlineHit.test(variant) || variant.gene()
                        .equals("KIT"));

        return variants.stream().filter(report).map(x -> fromGermlineVariant(x).driverLikelihood(1).build()).collect(Collectors.toList());
    }

    @NotNull
    public static List<ReportableVariant> reportableSomaticVariants(List<DriverCatalog> driverCatalog, List<SomaticVariant> variants) {
        final Map<String, DriverCatalog> driverCatalogMap = driverCatalog.stream().collect(Collectors.toMap(DriverCatalog::gene, x -> x));

        final List<ReportableVariant> result = Lists.newArrayList();
        for (SomaticVariant variant : variants) {
            if (variant.reported()) {
                DriverCatalog geneDriver = driverCatalogMap.get(variant.gene());
                ReportableVariant reportable = fromSomaticVariant(variant).driverLikelihood(geneDriver.driverLikelihood()).build();
                result.add(reportable);
            }
        }

        return result;
    }

    @NotNull
    public static List<ReportableVariant> mergeSomaticAndGermlineVariants(@NotNull List<ReportableVariant> germline,
            @NotNull List<ReportableVariant> somatic) {
        List<ReportableVariant> result = Lists.newArrayList();

        Map<String, Double> maxLikelihood = Maps.newHashMap();
        for (ReportableVariant variant : germline) {
            maxLikelihood.merge(variant.gene(), variant.driverLikelihood(), Math::max);
        }

        for (ReportableVariant variant : somatic) {
            maxLikelihood.merge(variant.gene(), variant.driverLikelihood(), Math::max);
        }

        for (ReportableVariant variant : germline) {
            result.add(ImmutableReportableVariant.builder().from(variant).driverLikelihood(maxLikelihood.get(variant.gene())).build());
        }

        for (ReportableVariant variant : somatic) {
            result.add(ImmutableReportableVariant.builder().from(variant).driverLikelihood(maxLikelihood.get(variant.gene())).build());
        }

        return result;
    }

    @NotNull
    static List<ReportableVariant> mergeSomaticAndGermlineVariants(@NotNull List<DriverSomaticVariant> somaticVariantsReport,
            @NotNull List<DriverGermlineVariant> germlineVariantsToReport, @NotNull GermlineReportingModel germlineReportingModel) {
        List<ReportableVariant> allReportableVariants = Lists.newArrayList();
        for (DriverSomaticVariant somaticDriverVariant : somaticVariantsReport) {
            double adjustedDriverLikelihood = somaticDriverVariant.driverLikelihood();
            for (DriverGermlineVariant germlineVariant : germlineVariantsToReport) {
                if (germlineVariant.variant().gene().equals(somaticDriverVariant.variant().gene())) {
                    adjustedDriverLikelihood = Math.max(adjustedDriverLikelihood, germlineVariant.driverLikelihood());
                }
            }

            allReportableVariants.add(fromSomaticVariant(somaticDriverVariant.variant()).driverLikelihood(adjustedDriverLikelihood)
                    .build());
        }

        for (DriverGermlineVariant driverGermlineVariant : germlineVariantsToReport) {
            double adjustedDriverLikelihood = driverGermlineVariant.driverLikelihood();
            for (DriverSomaticVariant somaticVariant : somaticVariantsReport) {
                if (somaticVariant.variant().gene().equals(driverGermlineVariant.variant().gene())) {
                    adjustedDriverLikelihood = Math.max(adjustedDriverLikelihood, somaticVariant.driverLikelihood());
                }
            }

            allReportableVariants.add(fromGermlineVariant(driverGermlineVariant.variant()).driverLikelihood(adjustedDriverLikelihood)
                    .build());
        }

        return allReportableVariants;
    }

    @NotNull
    private static ImmutableReportableVariant.Builder fromGermlineVariant(@NotNull ReportableGermlineVariant variant) {
        return ImmutableReportableVariant.builder()
                .gene(variant.gene())
                .position(variant.position())
                .chromosome(variant.chromosome())
                .ref(variant.ref())
                .alt(variant.alt())
                .canonicalCodingEffect(variant.codingEffect())
                .canonicalHgvsCodingImpact(variant.hgvsCoding())
                .canonicalHgvsProteinImpact(variant.hgvsProtein())
                .totalReadCount(variant.totalReadCount())
                .alleleReadCount(variant.alleleReadCount())
                .gDNA(toGDNA(variant.chromosome(), variant.position()))
                .totalCopyNumber(variant.adjustedCopyNumber())
                .alleleCopyNumber(calcAlleleCopyNumber(variant.adjustedCopyNumber(), variant.adjustedVaf()))
                .hotspot(Hotspot.NON_HOTSPOT)
                .clonalLikelihood(1D)
                .source(ReportableVariantSource.GERMLINE)
                .biallelic(variant.biallelic());
    }

    @NotNull
    private static ImmutableReportableVariant.Builder fromSomaticVariant(@NotNull SomaticVariant variant) {
        return ImmutableReportableVariant.builder()
                .gene(variant.gene())
                .position(variant.position())
                .chromosome(variant.chromosome())
                .ref(variant.ref())
                .alt(variant.alt())
                .canonicalCodingEffect(variant.canonicalCodingEffect())
                .canonicalHgvsCodingImpact(variant.canonicalHgvsCodingImpact())
                .canonicalHgvsProteinImpact(variant.canonicalHgvsProteinImpact())
                .totalReadCount(variant.totalReadCount())
                .alleleReadCount(variant.alleleReadCount())
                .gDNA(toGDNA(variant.chromosome(), variant.position()))
                .totalCopyNumber(variant.adjustedCopyNumber())
                .alleleCopyNumber(calcAlleleCopyNumber(variant.adjustedCopyNumber(), variant.adjustedVAF()))
                .hotspot(variant.hotspot())
                .source(ReportableVariantSource.SOMATIC)
                .clonalLikelihood(variant.clonalLikelihood())
                .biallelic(variant.biallelic());
    }

    @NotNull
    private static String toGDNA(@NotNull String chromosome, long position) {
        return chromosome + ":" + position;
    }

    private static double calcAlleleCopyNumber(double adjustedCopyNumber, double adjustedVAF) {
        return adjustedCopyNumber * Math.max(0, Math.min(1, adjustedVAF));
    }
}
