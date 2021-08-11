package com.hartwig.hmftools.orange.algo;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hartwig.hmftools.common.chord.ChordAnalysis;
import com.hartwig.hmftools.common.doid.DoidNode;
import com.hartwig.hmftools.common.linx.LinxData;
import com.hartwig.hmftools.common.protect.ProtectEvidence;
import com.hartwig.hmftools.common.purple.PurpleData;
import com.hartwig.hmftools.common.virus.VirusInterpreterData;
import com.hartwig.hmftools.orange.cuppa.CuppaData;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class OrangeReport {

    @NotNull
    public abstract String sampleId();

    @NotNull
    public abstract String pipelineVersion();

    @NotNull
    public abstract Set<DoidNode> configuredPrimaryTumor();

    @NotNull
    public abstract String cuppaPrimaryTumor();

    @NotNull
    public abstract List<CuppaData> cuppaData();

    @NotNull
    public abstract PurpleData purple();

    @NotNull
    public abstract LinxData linx();

    @NotNull
    public abstract VirusInterpreterData virusInterpreter();

    @NotNull
    public abstract ChordAnalysis chord();

    @NotNull
    public abstract List<ProtectEvidence> protect();

    @NotNull
    public abstract Map<String, Double> germlineMVLHPerGene();

    @NotNull
    public abstract OrangePlots plots();

}
