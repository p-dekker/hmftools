package com.hartwig.hmftools.bamtools.metrics;

import static com.hartwig.hmftools.bamtools.common.CommonUtils.BAM_FILE;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.BT_LOGGER;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.DEFAULT_CHR_PARTITION_SIZE;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.ITEM_DELIM;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.LOG_READ_IDS;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.PARTITION_SIZE;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.SAMPLE;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.addCommonCommandOptions;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.checkFileExists;
import static com.hartwig.hmftools.bamtools.common.CommonUtils.loadSpecificRegionsConfig;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeSource.REF_GENOME;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion.V37;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.OUTPUT_DIR;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.OUTPUT_ID;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.checkAddDirSeparator;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.parseOutputDir;
import static com.hartwig.hmftools.common.utils.TaskExecutor.parseThreads;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.mergeChrBaseRegionOverlaps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.hartwig.hmftools.bamtools.common.CommonUtils;
import com.hartwig.hmftools.common.genome.bed.BedFileReader;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

public class MetricsConfig
{
    public final String SampleId;
    public final String BamFile;
    public final String RefGenomeFile;
    public final RefGenomeVersion RefGenVersion;

    public final int MapQualityThreshold;
    public final int BaseQualityThreshold;
    public final int MaxCoverage;

    public final int PartitionSize;

    public final List<ChrBaseRegion> UnmappableRegions;

    // metrics capture config
    public final boolean ExcludeZeroCoverage;
    public final boolean WriteOldStyle;

    public final String OutputDir;
    public final String OutputId;

    public final int Threads;

    // debug
    public final List<String> SpecificChromosomes;
    public final List<String> LogReadIds;
    public final List<ChrBaseRegion> SpecificRegions;
    public final boolean PerfDebug;

    private boolean mIsValid;

    private static final String MAP_QUAL_THRESHOLD = "map_qual_threshold";
    private static final String BASE_QUAL_THRESHOLD = "base_qual_threshold";
    private static final String MAX_COVERAGE = "max_coverage";
    private static final String EXCLUDE_ZERO_COVERAGE = "exclude_zero_coverage";
    private static final String WRITE_OLD_STYLE = "write_old_style";

    public static final String PERF_DEBUG = "perf_debug";

    private static final int DEFAULT_MAP_QUAL_THRESHOLD = 20;
    private static final int DEFAULT_BASE_QUAL_THRESHOLD = 10;
    private static final int DEFAULT_MAX_COVERAGE = 250;

    public MetricsConfig(final CommandLine cmd)
    {
        mIsValid = true;

        SampleId = cmd.getOptionValue(SAMPLE);
        BamFile = cmd.getOptionValue(BAM_FILE);
        RefGenomeFile = cmd.getOptionValue(REF_GENOME);

        if(cmd.hasOption(OUTPUT_DIR))
        {
            OutputDir = parseOutputDir(cmd);
        }
        else
        {
            OutputDir = checkAddDirSeparator(Paths.get(BamFile).getParent().toString());
        }

        OutputId = cmd.getOptionValue(OUTPUT_ID);

        if(BamFile == null || OutputDir == null || RefGenomeFile == null)
        {
            BT_LOGGER.error("missing config: bam({}) refGenome({}) outputDir({})",
                    BamFile != null, RefGenomeFile != null, OutputDir != null);
            mIsValid = false;
        }

        RefGenVersion = RefGenomeVersion.from(cmd);

        BT_LOGGER.info("refGenome({}), bam({})", RefGenVersion, BamFile);
        BT_LOGGER.info("output({})", OutputDir);

        PartitionSize = Integer.parseInt(cmd.getOptionValue(PARTITION_SIZE, String.valueOf(DEFAULT_CHR_PARTITION_SIZE)));
        MapQualityThreshold = Integer.parseInt(cmd.getOptionValue(MAP_QUAL_THRESHOLD, String.valueOf(DEFAULT_MAP_QUAL_THRESHOLD)));
        BaseQualityThreshold = Integer.parseInt(cmd.getOptionValue(BASE_QUAL_THRESHOLD, String.valueOf(DEFAULT_BASE_QUAL_THRESHOLD)));
        MaxCoverage = Integer.parseInt(cmd.getOptionValue(MAX_COVERAGE, String.valueOf(DEFAULT_MAX_COVERAGE)));
        ExcludeZeroCoverage = cmd.hasOption(EXCLUDE_ZERO_COVERAGE);
        WriteOldStyle = cmd.hasOption(WRITE_OLD_STYLE);

        UnmappableRegions = Lists.newArrayList();
        loadUnmappableRegions();

        SpecificChromosomes = Lists.newArrayList();
        SpecificRegions = Lists.newArrayList();

        mIsValid &= loadSpecificRegionsConfig(cmd, SpecificChromosomes, SpecificRegions);

        if(mIsValid && !SpecificRegions.isEmpty())
        {
            mergeChrBaseRegionOverlaps(SpecificRegions, true);
        }

        LogReadIds = cmd.hasOption(LOG_READ_IDS) ?
                Arrays.stream(cmd.getOptionValue(LOG_READ_IDS).split(ITEM_DELIM, -1)).collect(Collectors.toList()) : Lists.newArrayList();

        Threads = parseThreads(cmd);

        PerfDebug = cmd.hasOption(PERF_DEBUG);
    }

    public boolean isValid()
    {
        if(!mIsValid)
            return false;

        mIsValid = checkFileExists(BamFile) && checkFileExists(RefGenomeFile);
        return mIsValid;
    }

    private void loadUnmappableRegions()
    {
        String filename = RefGenVersion.is37() ? "/genome_unmappable_regions.37.bed" : "/genome_unmappable_regions.38.bed";

        final InputStream inputStream = MetricsConfig.class.getResourceAsStream(filename);

        try
        {
            UnmappableRegions.addAll(
                    BedFileReader.loadBedFile(new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.toList())));
        }
        catch(Exception e)
        {
            BT_LOGGER.error("failed to load unmapped regions file({}): {}", filename, e.toString());
            System.exit(1);
        }
    }

    public String formFilename(final String fileType)
    {
        return CommonUtils.formFilename(SampleId, BamFile, OutputDir, OutputId, fileType);
    }

    public static Options createCmdLineOptions()
    {
        final Options options = new Options();

        addCommonCommandOptions(options);

        options.addOption(PARTITION_SIZE, true, "Partition size, default: " + DEFAULT_CHR_PARTITION_SIZE);
        options.addOption(MAP_QUAL_THRESHOLD, true, "Map quality threshold, default: " + DEFAULT_MAP_QUAL_THRESHOLD);
        options.addOption(BASE_QUAL_THRESHOLD, true, "Base quality threshold, default: " + DEFAULT_BASE_QUAL_THRESHOLD);
        options.addOption(MAX_COVERAGE, true, "Max coverage, default: " + DEFAULT_MAX_COVERAGE);
        options.addOption(EXCLUDE_ZERO_COVERAGE, false, "Exclude bases with zero coverage");
        options.addOption(WRITE_OLD_STYLE, false, "Write data in same format as Picard CollectWgsMetrics");
        options.addOption(LOG_READ_IDS, true, "Log specific read IDs, separated by ';'");
        options.addOption(PERF_DEBUG, false, "Detailed performance tracking and logging");

        return options;
    }

    @VisibleForTesting
    public MetricsConfig(int maxCoveage)
    {
        mIsValid = true;

        SampleId = "SAMPLE_ID";
        BamFile = null;
        RefGenomeFile = null;
        RefGenVersion = V37;
        OutputDir = null;
        OutputId = null;

        PartitionSize = DEFAULT_CHR_PARTITION_SIZE;
        MapQualityThreshold = DEFAULT_MAP_QUAL_THRESHOLD;
        BaseQualityThreshold = DEFAULT_BASE_QUAL_THRESHOLD;
        MaxCoverage = maxCoveage;
        ExcludeZeroCoverage = false;
        WriteOldStyle = false;

        SpecificChromosomes = Collections.emptyList();
        SpecificRegions = Collections.emptyList();
        LogReadIds = Collections.emptyList();
        UnmappableRegions = Collections.emptyList();

        Threads = 0;
        PerfDebug = false;
    }
}
