package com.hartwig.hmftools.orange.algo;

import java.util.List;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class OrangePlots {

    @NotNull
    public abstract String purpleComprehensiveCircosPlot();

    @NotNull
    public abstract String purpleClonalityPlot();

    @NotNull
    public abstract List<String> linxDriverPlots();
}
