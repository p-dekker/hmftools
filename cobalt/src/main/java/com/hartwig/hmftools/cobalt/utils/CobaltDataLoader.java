package com.hartwig.hmftools.cobalt.utils;

import static com.hartwig.hmftools.cobalt.CobaltConfig.CB_LOGGER;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.hartwig.hmftools.common.cobalt.CobaltRatio;
import com.hartwig.hmftools.common.cobalt.CobaltRatioFile;
import com.hartwig.hmftools.common.genome.chromosome.Chromosome;
import com.hartwig.hmftools.common.genome.chromosome.HumanChromosome;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;
import com.hartwig.hmftools.common.purple.Gender;

public class CobaltDataLoader
{
    public static void addCobaltSampleData(
            final RefGenomeVersion refGenVersion, final Gender amberGender, final String cobaltFilename,
            final Map<String,List<RegionData>> chrRegionData)
    {
        try
        {
            CB_LOGGER.info("reading Cobalt ratios from {}", cobaltFilename);
            Map<Chromosome,List<CobaltRatio>> chrRatios = CobaltRatioFile.readWithGender(cobaltFilename, amberGender, true);

            for(Map.Entry<String,List<RegionData>> entry : chrRegionData.entrySet())
            {
                String chrStr = entry.getKey();
                List<RegionData> regions = entry.getValue();

                HumanChromosome chromosome = HumanChromosome.fromString(chrStr);
                List<CobaltRatio> cobaltRatios = chrRatios.get(chromosome);

                if(cobaltRatios == null)
                    continue;

                double wgsGcRatio = wgsGcRatio(amberGender, chrStr);

                int cobaltIndex = 0;

                for(RegionData region : regions)
                {
                    CobaltRatio cobaltRatio = null;

                    while(true)
                    {
                        if(cobaltIndex >= cobaltRatios.size())
                            break;

                        cobaltRatio = cobaltRatios.get(cobaltIndex);

                        if(cobaltRatio.position() == region.Position)
                            break;
                        else if(cobaltRatio.position() > region.Position)
                            break;

                        ++cobaltIndex;
                    }

                    if(cobaltRatio != null && cobaltRatio.position() == region.Position)
                    {
                        region.addSampleRegionData(new SampleRegionData(cobaltRatio.tumorReadCount(), cobaltRatio.tumorGCRatio(), wgsGcRatio));
                    }
                    else
                    {
                        region.addSampleRegionData(new SampleRegionData(0, 0, wgsGcRatio));
                    }
                }
            }
        }
        catch(IOException e)
        {
            CB_LOGGER.error("sample({}) failed to read Cobalt data: {}", cobaltFilename, e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static double wgsGcRatio(final Gender amberGender, final String chromosome)
    {
        if(chromosome.equals("X"))
            return amberGender == Gender.FEMALE ? 1 : 0.5;
        else if(chromosome.equals("Y"))
            return amberGender == Gender.FEMALE ? 0 : 0.5;
        else
            return 1;
    }
}
