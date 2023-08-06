package com.hartwig.hmftools.neo.missense;

import static com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache.addEnsemblDir;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.addRefGenomeConfig;
import static com.hartwig.hmftools.common.utils.TaskExecutor.addThreadOptions;
import static com.hartwig.hmftools.common.utils.TaskExecutor.parseThreads;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.GENE_ID_FILE;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.GENE_ID_FILE_DESC;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.addLoggingOptions;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.loadGeneIdsFile;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.OUTPUT_ID;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.addOutputOptions;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.parseOutputDir;
import static com.hartwig.hmftools.neo.bind.BindConstants.MIN_PEPTIDE_LENGTH;
import static com.hartwig.hmftools.neo.bind.BindConstants.REF_PEPTIDE_LENGTH;
import static com.hartwig.hmftools.neo.bind.FlankCounts.FLANK_AA_COUNT;
import static com.hartwig.hmftools.neo.scorer.NeoScorerConfig.COHORT_TPM_MEDIANS_FILE;
import static com.hartwig.hmftools.neo.scorer.NeoScorerConfig.COHORT_TPM_MEDIANS_FILE_DESC;
import static com.hartwig.hmftools.neo.scorer.NeoScorerConfig.LIKELIHOOD_THRESHOLD;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.neo.bind.ScoreConfig;

public class MissenseConfig
{
    public final List<String> GeneIds;
    public final List<String> Alleles;

    public final int[] PeptideLengths;
    public final int FlankLength;

    public final double LikelihoodCutoff;
    public final boolean KeepDuplicates;
    public final String OutputDir;
    public final String OutputId;
    public final int Threads;

    private static final String SPECIFIC_ALLELES = "specific_alleles";
    private static final String KEEP_DUPLICATES = "keep_duplicates";

    public MissenseConfig(final ConfigBuilder configBuilder)
    {
        PeptideLengths = new int[] { MIN_PEPTIDE_LENGTH, REF_PEPTIDE_LENGTH };
        FlankLength = FLANK_AA_COUNT;

        GeneIds = loadGeneIdsFile(configBuilder.getValue(GENE_ID_FILE));
        LikelihoodCutoff = configBuilder.getDecimal(LIKELIHOOD_THRESHOLD);
        KeepDuplicates = configBuilder.hasFlag(KEEP_DUPLICATES);
        Threads = parseThreads(configBuilder);
        OutputDir = parseOutputDir(configBuilder);
        OutputId = configBuilder.getValue(OUTPUT_ID);

        Alleles = Lists.newArrayList();
        if(configBuilder.hasValue(SPECIFIC_ALLELES))
        {
            Arrays.stream(configBuilder.getValue(SPECIFIC_ALLELES).split(",", -1)).forEach(x -> Alleles.add(x));
        }
    }

    public static void registerConfig(final ConfigBuilder configBuilder)
    {
        addEnsemblDir(configBuilder);
        ScoreConfig.registerConfig(configBuilder);
        addRefGenomeConfig(configBuilder, true);
        configBuilder.addPath(GENE_ID_FILE, true, GENE_ID_FILE_DESC);
        configBuilder.addConfigItem(SPECIFIC_ALLELES, false, "Specific alleles to score, must be from training set");

        configBuilder.addDecimal(
                LIKELIHOOD_THRESHOLD, "Rank threshold to write full peptide data, default 0 (not applied)", 0.02);

        configBuilder.addPath(COHORT_TPM_MEDIANS_FILE, false, COHORT_TPM_MEDIANS_FILE_DESC);
        configBuilder.addFlag(KEEP_DUPLICATES, "Keep duplicate peptides");
        addThreadOptions(configBuilder);
        addOutputOptions(configBuilder);
        addLoggingOptions(configBuilder);
    }

    public MissenseConfig(int peptideLength, int flankLength)
    {
        PeptideLengths = new int[] { peptideLength };
        FlankLength = flankLength;
        GeneIds = Lists.newArrayList();
        Alleles = Lists.newArrayList();
        LikelihoodCutoff = 0;
        Threads = 1;
        KeepDuplicates = true;
        OutputId = null;
        OutputDir = null;
    }
}
