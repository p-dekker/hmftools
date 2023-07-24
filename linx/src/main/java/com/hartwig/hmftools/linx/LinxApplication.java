package com.hartwig.hmftools.linx;

import static java.lang.Math.min;

import static com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache.addEnsemblDir;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.addLoggingOptions;
import static com.hartwig.hmftools.common.utils.config.ConfigUtils.setLogLevel;
import static com.hartwig.hmftools.common.utils.file.FileWriterUtils.checkCreateOutputDir;
import static com.hartwig.hmftools.linx.LinxConfig.LNX_LOGGER;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.addDatabaseCmdLineArgs;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.createDatabaseAccess;
import static com.hartwig.hmftools.patientdb.dao.DatabaseAccess.hasDatabaseConfig;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.ensemblcache.EnsemblDataCache;
import com.hartwig.hmftools.common.utils.PerformanceCounter;
import com.hartwig.hmftools.common.utils.TaskExecutor;
import com.hartwig.hmftools.common.utils.config.ConfigBuilder;
import com.hartwig.hmftools.common.utils.config.ConfigUtils;
import com.hartwig.hmftools.common.utils.version.VersionInfo;
import com.hartwig.hmftools.linx.fusion.FusionConfig;
import com.hartwig.hmftools.linx.fusion.FusionResources;
import com.hartwig.hmftools.patientdb.dao.DatabaseAccess;

import org.jetbrains.annotations.NotNull;

public class LinxApplication
{
    public LinxApplication(final ConfigBuilder configBuilder)
    {
        LinxConfig config = new LinxConfig(configBuilder);

        if(!checkCreateOutputDir(config.OutputDataPath))
        {
            LNX_LOGGER.error("failed to create output directory({})", config.OutputDataPath);
            return;
        }

        long startTime = System.currentTimeMillis();

        List<String> samplesList = config.getSampleIds();

        DatabaseAccess dbAccess = null;

        if(hasDatabaseConfig(configBuilder))
        {
            dbAccess = createDatabaseAccess(configBuilder);
        }
        else
        {
            if(!config.hasValidSampleDataSource(configBuilder))
            {
                System.exit(1);
            }
        }

        if(samplesList.isEmpty())
        {
            LNX_LOGGER.info("not samples loaded, exiting");
            System.exit(1);
        }

        LNX_LOGGER.info("running {} analysis for {}",
                config.IsGermline ? "germline SV" : "SV",
                config.hasMultipleSamples() ? String.format("%d samples", samplesList.size()) : samplesList.get(0));

        FusionResources fusionResources = new FusionResources(configBuilder);

        if(config.RunFusions && !fusionResources.knownFusionCache().hasValidData())
        {
            LNX_LOGGER.info("invalid fusion config, exiting");
            System.exit(1);
        }

        EnsemblDataCache ensemblDataCache = new EnsemblDataCache(configBuilder);

        if(ensemblDataCache != null)
        {
            boolean reqProteinDomains = config.RunFusions;
            boolean reqSplicePositions = config.RunFusions;
            boolean canonicalOnly = config.IsGermline;

            ensemblDataCache.setRequiredData(true, reqProteinDomains, reqSplicePositions, canonicalOnly);

            boolean ensemblLoadOk = false;

            if(!config.RestrictedGeneIds.isEmpty())
            {
                ensemblDataCache.setRestrictedGeneIdList(config.RestrictedGeneIds);
                ensemblLoadOk = ensemblDataCache.load(false);
            }
            else
            {
                ensemblLoadOk = ensemblDataCache.load(false);
            }

            if(!ensemblLoadOk)
            {
                LNX_LOGGER.error("Ensembl data cache load failed, exiting");
                System.exit(1);
            }

            if(config.RunDrivers)
                ensemblDataCache.createGeneNameIdMap();
        }

        CohortDataWriter cohortDataWriter = new CohortDataWriter(config, ensemblDataCache);

        SvAnnotators svAnnotators = new SvAnnotators(config, ensemblDataCache, dbAccess);

        List<SampleAnalyser> sampleAnalysers = Lists.newArrayList();

        if(config.Threads > 1)
        {
            List<List<String>> saSampleLists = Lists.newArrayList();

            int threads = min(config.Threads, samplesList.size());

            for(int i = 0; i < threads; ++i)
            {
                sampleAnalysers.add(new SampleAnalyser(
                        i, config, dbAccess, svAnnotators, ensemblDataCache, fusionResources, cohortDataWriter));

                saSampleLists.add(Lists.newArrayList());
            }

            int saIndex = 0;
            for(final String sampleId : samplesList)
            {
                saSampleLists.get(saIndex).add(sampleId);
                ++saIndex;

                if(saIndex >= threads)
                    saIndex = 0;
            }

            for(int i = 0; i < threads; ++i)
            {
                sampleAnalysers.get(i).setSampleIds(saSampleLists.get(i));
            }

            final List<Callable> callableList = sampleAnalysers.stream().collect(Collectors.toList());
            TaskExecutor.executeTasks(callableList, callableList.size());
        }
        else
        {
            SampleAnalyser sampleAnalyser = new SampleAnalyser(
                    0, config, dbAccess, svAnnotators, ensemblDataCache, fusionResources, cohortDataWriter);

            sampleAnalysers.add(sampleAnalyser);
            sampleAnalyser.setSampleIds(config.getSampleIds());
            sampleAnalyser.processSamples();
        }

        cohortDataWriter.close();

        if(config.hasMultipleSamples())
        {
            // combine and log performance counters
            Map<String,PerformanceCounter> combinedPerfCounters = sampleAnalysers.get(0).getPerfCounters();

            for(int i = 1; i < sampleAnalysers.size(); ++i)
            {
                Map<String,PerformanceCounter> saPerfCounters = sampleAnalysers.get(i).getPerfCounters();

                for(Map.Entry<String,PerformanceCounter> entry : combinedPerfCounters.entrySet())
                {
                    PerformanceCounter combinedPc = entry.getValue();
                    PerformanceCounter saPc = saPerfCounters.get(entry.getKey());

                    if(combinedPc != null)
                        combinedPc.merge(saPc);
                }
            }

            for(Map.Entry<String,PerformanceCounter> entry : combinedPerfCounters.entrySet())
            {
                entry.getValue().logStats();
            }
        }

        if(config.isSingleSample())
        {
            final VersionInfo version = new VersionInfo("linx.version");
            try { version.write(config.OutputDataPath); } catch(IOException e) {}
        }

        if(config.isSingleSample())
        {
            LNX_LOGGER.info("Linx complete for {}", samplesList.get(0));
        }
        else
        {
            double runTime = (System.currentTimeMillis() - startTime) / 1000.0;

            LNX_LOGGER.info("SV analysis complete for {} samples, run time({})s",
                    samplesList.size(), String.format("%.3f", runTime));
        }
    }

    public static void logVersion()
    {
        final VersionInfo version = new VersionInfo("linx.version");
        LNX_LOGGER.info("Linx version: {}", version.version());
    }

    public static void main(@NotNull final String[] args)
    {
        ConfigBuilder configBuilder = new ConfigBuilder();
        LinxConfig.addConfig(configBuilder);
        FusionConfig.addConfig(configBuilder);

        addDatabaseCmdLineArgs(configBuilder, false);
        addEnsemblDir(configBuilder);
        ConfigUtils.addLoggingOptions(configBuilder);

        configBuilder.checkAndParseCommandLine(args);

        logVersion();

        setLogLevel(configBuilder);

        new LinxApplication(configBuilder);
    }
}
