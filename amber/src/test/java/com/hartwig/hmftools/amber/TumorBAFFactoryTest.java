package com.hartwig.hmftools.amber;

import static org.junit.Assert.assertEquals;

import com.hartwig.hmftools.common.samtools.SamRecordUtils;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import htsjdk.samtools.SAMRecord;

public class TumorBAFFactoryTest
{
    @Test
    public void useQualityOfBaseAfterDel()
    {
        int minQuality = SamRecordUtils.getBaseQuality('J');

        final SAMRecord lowQualDel = buildSamRecord(1000, "1M1D1M", "CT", "FI");
        final SAMRecord highQualDel = buildSamRecord(1000, "1M1D1M", "CT", "FJ");
        final TumorBAF victim = createDefault("5", 1001);

        new TumorBAFFactory(minQuality).addEvidence(victim, lowQualDel);
        assertEquals(0, victim.TumorReadDepth);

        new TumorBAFFactory(minQuality).addEvidence(victim, highQualDel);
        assertEquals(1, victim.TumorReadDepth);
    }

    private static TumorBAF createDefault(final String chromosome, final int position)
    {
        BaseDepth baseDepth = new BaseDepth(chromosome, position, "A", "T");
        baseDepth.ReadDepth = 6;
        baseDepth.RefSupport = 3;
        baseDepth.AltSupport = 3;
        return TumorBAFFactory.create(baseDepth);
    }

    private SAMRecord buildSamRecord(
            final int alignmentStart, @NotNull final String cigar, @NotNull final String readString, @NotNull final String qualities)
    {
        final SAMRecord record = new SAMRecord(null);
        record.setAlignmentStart(alignmentStart);
        record.setCigarString(cigar);
        record.setReadString(readString);
        record.setReadNegativeStrandFlag(false);
        record.setBaseQualityString(qualities);
        record.setMappingQuality(20);
        record.setDuplicateReadFlag(false);
        record.setReadUnmappedFlag(false);
        return record;
    }
}
