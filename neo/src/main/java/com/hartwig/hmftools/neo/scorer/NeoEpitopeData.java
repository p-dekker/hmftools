package com.hartwig.hmftools.neo.scorer;

import static java.lang.String.format;

import static com.hartwig.hmftools.common.fusion.FusionCommon.FS_DOWN;
import static com.hartwig.hmftools.common.fusion.FusionCommon.FS_PAIR;
import static com.hartwig.hmftools.common.fusion.FusionCommon.FS_UP;
import static com.hartwig.hmftools.common.rna.RnaExpressionMatrix.INVALID_EXP;
import static com.hartwig.hmftools.neo.scorer.NeoRnaData.NO_TPM_VALUE;
import static com.hartwig.hmftools.neo.scorer.TpmMediansCache.CANCER_VALUE;
import static com.hartwig.hmftools.neo.scorer.TpmMediansCache.PAN_CANCER_VALUE;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.neo.NeoEpitopeType;
import com.hartwig.hmftools.common.neo.RnaNeoEpitope;
import com.hartwig.hmftools.common.rna.RnaExpressionMatrix;

public class NeoEpitopeData
{
    public final int Id;
    public final NeoEpitopeType VariantType;
    public final String VariantInfo;
    public final String GeneId;
    public final String GeneName;
    public final String UpAminoAcids;
    public final String NovelAminoAcids;
    public final String DownAminoAcids;
    public final List<String>[] Transcripts;
    public final NeoRnaData RnaData;

    public final List<PeptideScoreData> mPeptides;

    public NeoEpitopeData(
            final int id, final NeoEpitopeType variantType, final String variantInfo, final String geneId, final String geneName,
            final String upAminoAcids, final String novelAminoAcids, final String downAminoAcids,
            final List<String> transNamesUp, final List<String> transNamesDown)
    {
        Id = id;
        VariantType = variantType;
        VariantInfo = variantInfo;
        GeneId = geneId;
        GeneName = geneName;
        UpAminoAcids = upAminoAcids;
        NovelAminoAcids = novelAminoAcids;
        DownAminoAcids = downAminoAcids;
        Transcripts = new List[FS_PAIR];
        Transcripts[FS_UP] = transNamesUp;
        Transcripts[FS_DOWN] = transNamesDown;

        RnaData = new NeoRnaData();

        mPeptides = Lists.newArrayList();
    }

    public List<PeptideScoreData> peptides() { return mPeptides; }

    public void addPeptides(final List<PeptideScoreData> peptides)
    {
        mPeptides.addAll(peptides);
    }

    public void setExpressionData(
            final SampleData sampleData, final Map<String,Double> sampleTPMs, final RnaExpressionMatrix transExpressionCache,
            final TpmMediansCache cohortTpmMediansCache)
    {
        if(transExpressionCache == null && sampleTPMs.isEmpty())
            return;

        double[] sampleExpression = {0, 0};
        double[] panCancerTpm = {0, 0};
        double[] cancerTpm = {0, 0};

        for(int fs = FS_UP; fs <= FS_DOWN; ++fs)
        {
            for(String transName : Transcripts[fs])
            {
                double transExpression = 0;

                if(!sampleTPMs.isEmpty())
                {
                    transExpression = sampleTPMs.get(transName);
                }
                else
                {
                    transExpression = transExpressionCache.getExpression(transName, sampleData.Id);
                }

                // distinguish non-existent expression vs zero TPM
                if(transExpression != INVALID_EXP)
                    sampleExpression[fs] += transExpression;

                double[] tpmValues = cohortTpmMediansCache.getTranscriptTpm(transName, sampleData.CancerType);

                if(tpmValues[PAN_CANCER_VALUE] != NO_TPM_VALUE)
                    panCancerTpm[fs] += tpmValues[PAN_CANCER_VALUE];

                if(tpmValues[CANCER_VALUE] != NO_TPM_VALUE)
                    cancerTpm[fs] += tpmValues[CANCER_VALUE];
            }
        }

        RnaData.setExpression(sampleExpression);
        RnaData.setCohortValues(cancerTpm, panCancerTpm);
    }

    public void setFusionRnaSupport(final List<RnaNeoEpitope> rnaNeoDataList)
    {
        if(!VariantType.isFusion())
            return;

        RnaNeoEpitope matchedRnaData = rnaNeoDataList.stream()
                .filter(x -> x.Id == Id && x.VariantInfo.equals(VariantInfo)).findFirst().orElse(null);

        if(matchedRnaData != null)
        {
            RnaData.setCoverage(matchedRnaData.FragmentCount, matchedRnaData.BaseDepth[FS_UP], matchedRnaData.BaseDepth[FS_DOWN]);
        }
    }

    public void setMutationRnaSupport(final List<SomaticVariant> somaticVariants)
    {
        if(!VariantType.isPointMutation())
            return;

        SomaticVariant matchedRnaData = somaticVariants.stream()
                .filter(x -> x.variantInfo().equals(VariantInfo)).findFirst().orElse(null);

        if(matchedRnaData != null)
        {
            RnaData.setCoverage(matchedRnaData.RnaFragments, matchedRnaData.RnaDepth, matchedRnaData.RnaDepth);
        }
    }

    public String toString()
    {
        return format("%d: %s:%s gene(%s)", Id, VariantType, VariantInfo, GeneName);
    }
}
