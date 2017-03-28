package com.hartwig.hmftools.healthchecker.context;

import org.jetbrains.annotations.NotNull;

public interface RunContext {

    @NotNull
    String runDirectory();

    @NotNull
    String setName();

    @NotNull
    String refSample();

    @NotNull
    String tumorSample();

    boolean isSomaticRun();
}
