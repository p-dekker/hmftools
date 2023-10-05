package com.hartwig.hmftools.common.region;

import static java.lang.Math.min;

import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeFunctions.stripChrPrefix;
import static com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion.V37;

import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeCoordinates;
import com.hartwig.hmftools.common.genome.refgenome.RefGenomeVersion;

public class PartitionUtils
{
    public static List<ChrBaseRegion> partitionChromosome(
            final String chromosome, final RefGenomeVersion refGenomeVersion, final List<ChrBaseRegion> specificRegions, int partitionSize)
    {
        if(!specificRegions.isEmpty())
        {
            List<ChrBaseRegion> partitions = Lists.newArrayList();

            for(ChrBaseRegion region : specificRegions)
            {
                if(region.Chromosome.equals(chromosome))
                {
                    if(region.baseLength() <= partitionSize)
                        partitions.add(region);
                    else
                        partitions.addAll(buildPartitions(chromosome, region.start() ,region.end(), partitionSize));
                }
            }

            return partitions;
        }

        RefGenomeCoordinates refGenomeCoords = refGenomeVersion == V37 ? RefGenomeCoordinates.COORDS_37 : RefGenomeCoordinates.COORDS_38;
        int chromosomeLength = refGenomeCoords.length(stripChrPrefix(chromosome));
        return buildPartitions(chromosome, 1, chromosomeLength, partitionSize);
    }

    public static List<ChrBaseRegion> buildPartitions(final String chromosome, int minPosition, int maxPosition, int partitionSize)
    {
        final List<ChrBaseRegion> partitions = Lists.newArrayList();

        for(int i = 0; ; i++)
        {
            int start = minPosition + i * partitionSize;
            int end = min(start + partitionSize - 1, maxPosition);
            partitions.add(new ChrBaseRegion(chromosome, start, end));

            if(end >= maxPosition)
                break;
        }

        return partitions;
    }
}
