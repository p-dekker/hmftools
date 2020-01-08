package com.hartwig.hmftools.sage.phase;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.position.GenomePosition;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.config.SageConfig;
import com.hartwig.hmftools.sage.select.TierSelector;
import com.hartwig.hmftools.sage.variant.SageVariant;
import com.hartwig.hmftools.sage.vcf.SageVCF;

import org.jetbrains.annotations.NotNull;

class SnvSnvMerge implements Consumer<SageVariant> {

    private static final int BUFFER = 2;

    private final boolean enabled;
    private final MnvFactory factory;
    private final Consumer<SageVariant> consumer;
    private final List<SageVariant> list = Lists.newLinkedList();
    private final TierSelector tierSelector;

    SnvSnvMerge(final SageConfig config, @NotNull final Consumer<SageVariant> consumer, @NotNull final MnvFactory factory,
            @NotNull final List<GenomeRegion> panel, @NotNull final List<VariantHotspot> hotspots) {
        this.enabled = config.mnvDetection();
        this.consumer = consumer;
        this.factory = factory;
        this.tierSelector = new TierSelector(panel, hotspots);
    }

    //    SnvSnvMerge(@NotNull final Consumer<SageVariant> consumer, MnvFactory2 factory) {
    //        this.enabled = true;
    //        this.consumer = consumer;
    //        this.factory = factory;
    //    }

    @Override
    public void accept(@NotNull final SageVariant newEntry) {
        flush(newEntry);
        if (enabled && isPhasedSnv(newEntry)) {

            for (int i = 0; i < list.size(); i++) {
                final SageVariant oldEntry = list.get(i);
                if (isMnv(oldEntry, newEntry)) {
                    boolean bothEntriesPass = newEntry.isPassing() && oldEntry.isPassing();
                    final VariantHotspot mnvCandidate = factory.merge(oldEntry.primaryTumor(), newEntry.primaryTumor());
                    if (bothEntriesPass || tierSelector.isHotspot(mnvCandidate)) {
                        SageVariant mnv = factory.mnv(newEntry.localPhaseSet(), mnvCandidate);

                        newEntry.filters().add(SageVCF.MERGE_FILTER);
                        oldEntry.filters().add(SageVCF.MERGE_FILTER);
                        if (oldEntry.isSynthetic()) {
                            list.set(i, mnv);
                        } else {
                            list.add(i, mnv);
                            i++;
                        }
                    }
                }
            }
        }

        list.add(newEntry);
    }

    private void flush(@NotNull final GenomePosition position) {
        final Iterator<SageVariant> iterator = list.iterator();
        while (iterator.hasNext()) {
            final SageVariant entry = iterator.next();
            long entryEnd = entry.position() + entry.normal().ref().length() - 1;
            if (!entry.chromosome().equals(position.chromosome()) || entryEnd < position.position() - BUFFER) {
                iterator.remove();
                consumer.accept(entry);
            } else {
                return;
            }
        }
    }

    public void flush() {
        list.forEach(consumer);
        list.clear();
    }

    private boolean isPhasedSnv(@NotNull final SageVariant newEntry) {
        return newEntry.localPhaseSet() > 0 && !newEntry.isIndel() && !newEntry.filters().contains(SageVCF.MERGE_FILTER);
    }

    private boolean isMnv(@NotNull final SageVariant existingEntry, @NotNull final SageVariant newEntry) {
        long existingEntryEndPosition = existingEntry.position() + existingEntry.normal().ref().length() - 1;
        return isPhasedSnv(existingEntry) && existingEntry.localPhaseSet() == newEntry.localPhaseSet()
                && newEntry.position() - existingEntryEndPosition <= BUFFER;
    }
}
