package com.hartwig.hmftools.bachelor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import nl.hartwigmedicalfoundation.bachelor.Effect;
import nl.hartwigmedicalfoundation.bachelor.GeneIdentifier;
import nl.hartwigmedicalfoundation.bachelor.Program;
import nl.hartwigmedicalfoundation.bachelor.ProgramBlacklist;
import nl.hartwigmedicalfoundation.bachelor.ProgramPanel;
import nl.hartwigmedicalfoundation.bachelor.ProgramWhitelist;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;

class BachelorEligibility {

    private static final Logger LOGGER = LogManager.getLogger(BachelorEligibility.class);

    private final Map<String, Predicate<VariantModel>> programs = Maps.newHashMap();

    private BachelorEligibility() {
    }

    static BachelorEligibility fromMap(final Map<String, Program> input) {
        final BachelorEligibility result = new BachelorEligibility();

        for (final Program program : input.values()) {

            final Multimap<String, String> geneToEnsemblMap = HashMultimap.create();

            // load panels, potentially multiple

            final List<Predicate<VariantModel>> panelPredicates = Lists.newArrayList();
            for (final ProgramPanel panel : program.getPanel()) {
                final boolean allGene = panel.getAllGenes() != null;
                final List<GeneIdentifier> genes = panel.getGene();
                final List<String> effects = panel.getSnpEffect().stream().map(Effect::value).collect(Collectors.toList());

                genes.forEach(g -> geneToEnsemblMap.put(g.getName(), g.getEnsembl()));

                final Predicate<VariantModel> panelPredicate = v -> allGene
                        ? v.Annotations.stream().anyMatch(a -> a.Effects.stream().anyMatch(effects::contains))
                        : genes.stream()
                                .anyMatch(p -> v.Annotations.stream()
                                        .anyMatch(a -> a.Transcript.equals(p.getEnsembl()) && a.Effects.stream()
                                                .anyMatch(effects::contains)));
                panelPredicates.add(panelPredicate);
            }

            final Predicate<VariantModel> inPanel = v -> panelPredicates.stream().anyMatch(p -> p.test(v));

            // blacklist

            final List<ProgramBlacklist.Exclusion> blacklist =
                    program.getBlacklist() != null ? program.getBlacklist().getExclusion() : Lists.newArrayList();

            final Predicate<VariantModel> inBlacklist = v -> blacklist.stream().anyMatch(b -> {
                for (final SnpEff annotation : v.Annotations) {
                    final boolean transcriptMatches = geneToEnsemblMap.values().contains(annotation.Transcript);
                    if (transcriptMatches && !annotation.HGVSp.isEmpty()) {
                        if (b.getHGVSP() != null && b.getHGVSP().equals(annotation.HGVSp)) {
                            return true;
                        } else if (b.getHGVSC() != null && b.getHGVSC().equals(annotation.HGVSc)) {
                            return true;
                        } else if (b.getMinCodon() != null && b.getMinCodon().intValue() <= annotation.ProteinPosition.get(0)) {
                            return true;
                        }
                    }
                }
                return false;
            });

            // whitelist

            final Multimap<String, String> whitelist = HashMultimap.create();
            final Set<String> dbSNP = Sets.newHashSet();
            if (program.getWhitelist() != null) {
                for (final Object o : program.getWhitelist().getVariantOrDbSNP()) {
                    if (o instanceof ProgramWhitelist.Variant) {
                        final ProgramWhitelist.Variant v = (ProgramWhitelist.Variant) o;
                        for (final String transcript : geneToEnsemblMap.get(v.getGene().getName())) {
                            whitelist.put(transcript, v.getHGVSP());
                        }
                    } else if (o instanceof String) {
                        dbSNP.add((String) o);
                    }
                }
            }
            final Predicate<VariantModel> inWhitelist = v -> v.dbSNP.stream().anyMatch(dbSNP::contains) || v.Annotations.stream()
                    .anyMatch(a -> !a.HGVSp.isEmpty() && whitelist.get(a.Transcript).contains(a.HGVSp));

            final Predicate<VariantModel> predicate = v -> inPanel.test(v) ? !inBlacklist.test(v) : inWhitelist.test(v);
            result.programs.put(program.getName(), predicate);
        }

        return result;
    }

    @NotNull
    Collection<EligibilityReport> processVCF(final String patient, final String sample, final String tag, final VCFFileReader reader) {

        final Map<String, ImmutableEligibilityReport.Builder> results = Maps.newHashMap();
        for (final VariantContext variant : reader) {

            if (variant.isFiltered()) {
                continue;
            }

            // we will skip when an ALT is not present in the sample
            final Genotype genotype = variant.getGenotype(sample);
            if (genotype == null || !(genotype.isHomVar() || genotype.isHet())) {
                continue;
            }

            // TODO: do we need to verify specific ALTS have specific SnpEff effects

            final VariantModel model = VariantModel.from(variant);

            final List<String> matchingPrograms = programs.entrySet()
                    .stream()
                    .filter(program -> program.getValue().test(model))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            for (final String p : matchingPrograms) {
                results.computeIfAbsent(p, k -> ImmutableEligibilityReport.builder().patient(patient).program(p).tag(tag))
                        .addVariants(model);
            }
        }

        return results.values().stream().map(ImmutableEligibilityReport.Builder::build).collect(Collectors.toList());
    }
}
