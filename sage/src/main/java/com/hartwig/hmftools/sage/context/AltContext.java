package com.hartwig.hmftools.sage.context;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import com.hartwig.hmftools.common.hotspot.VariantHotspot;

import org.jetbrains.annotations.NotNull;

public class AltContext implements VariantHotspot {

    private final RefContext refContext;
    private final String alt;
    private final List<ReadContextCounter> readContextCounters = Lists.newArrayList();

    private int altReads;

    public AltContext(final String sample, final VariantHotspot hotspot) {
        refContext = new RefContext(sample, hotspot.chromosome(), hotspot.position(), hotspot.ref());
        this.alt = hotspot.alt();
    }

    AltContext(final RefContext refContext, final String alt) {
        this.refContext = refContext;
        this.alt = alt;
    }

    public void incrementAltRead() {
        this.altReads++;
    }

    public void addReadContext(@NotNull final ReadContext readContext) {
        if (readContext.isComplete()) {
            boolean readContextMatch = false;
            for (ReadContextCounter counter : readContextCounters) {
                readContextMatch |= counter.incrementCounters(position(), readContext.readBytePosition(), readContext.readBytes());
            }

            if (!readContextMatch) {
                readContextCounters.add(new ReadContextCounter(this, readContext));
            }
        }
    }

    @NotNull
    public ReadContextCounter primaryReadContext() {
        readContextCounters.sort(Comparator.comparingInt(ReadContextCounter::full).reversed());
        return readContextCounters.isEmpty()
                ? new ReadContextCounter(this, new ReadContext(0, alt().getBytes(), 0, ref().getBytes()))
                : readContextCounters.get(0);
    }

    public void setPrimaryReadContext(@NotNull final ReadContextCounter readContext) {
        readContextCounters.clear();
        readContextCounters.add(readContext);
    }

    @NotNull
    @Override
    public String ref() {
        return refContext.ref();
    }

    @NotNull
    @Override
    public String alt() {
        return alt;
    }

    @NotNull
    @Override
    public String chromosome() {
        return refContext.chromosome();
    }

    @Override
    public long position() {
        return refContext.position();
    }

    public int altSupport() {
        return altReads;
    }

    public int refSupport() {
        return refContext.refReads();
    }

    public int readDepth() {
        return refContext.readDepth();
    }

    public int subprimeReadDepth() {
        return refContext.subprimeReadDepth();
    }

    @NotNull
    public String sample() {
        return refContext.sample();
    }

    @Override
    public boolean equals(@Nullable Object another) {
        if (this == another) {
            return true;
        }
        return another instanceof VariantHotspot && equalTo((VariantHotspot) another);
    }

    private boolean equalTo(VariantHotspot another) {
        return ref().equals(another.ref()) && alt().equals(another.alt()) && chromosome().equals(another.chromosome())
                && position() == another.position();
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + ref().hashCode();
        h += (h << 5) + alt.hashCode();
        h += (h << 5) + chromosome().hashCode();
        h += (h << 5) + Longs.hashCode(position());
        return h;
    }

}
