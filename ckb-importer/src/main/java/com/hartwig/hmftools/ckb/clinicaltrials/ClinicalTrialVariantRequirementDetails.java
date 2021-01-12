package com.hartwig.hmftools.ckb.clinicaltrials;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value.Immutable
@Value.Style(passAnnotations = { NotNull.class, Nullable.class })
public abstract class ClinicalTrialVariantRequirementDetails {

    @NotNull
    public abstract ClinicalTrialMolecularProfile molecularProfile();

    @NotNull
    public abstract String requirementType();
}
