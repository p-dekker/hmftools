package com.hartwig.hmftools.bamtools.markdups;

import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;

public class PartitionDataStore
{
    private final Map<String,PartitionData> mPartitionDataMap;

    public PartitionDataStore()
    {
        mPartitionDataMap = Maps.newHashMap();
    }

    public synchronized PartitionData getOrCreatePartitionData(final String chrPartition)
    {
        PartitionData partitionCache = mPartitionDataMap.get(chrPartition);

        if(partitionCache == null)
        {
            partitionCache = new PartitionData(chrPartition);
            mPartitionDataMap.put(chrPartition, partitionCache);
        }

        return partitionCache;
    }

    public List<PartitionData> partitions() {return mPartitionDataMap.values().stream().collect(Collectors.toList()); }

    public String toString() { return format("partitions(%d)", mPartitionDataMap.size()); }
}
