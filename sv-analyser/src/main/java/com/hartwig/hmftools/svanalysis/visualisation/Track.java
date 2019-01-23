package com.hartwig.hmftools.svanalysis.visualisation;

import com.hartwig.hmftools.common.region.GenomeRegion;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class Track implements GenomeRegion {

    public abstract int clusterId();

    public abstract int chainId();

    public abstract int track();

    public abstract boolean openStart();

    public abstract boolean openEnd();
}
