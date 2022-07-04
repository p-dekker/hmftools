package com.hartwig.hmftools.svprep;

import static com.hartwig.hmftools.common.test.MockRefGenome.generateRandomBases;
import static com.hartwig.hmftools.svprep.SvPrepTestUtils.CHR_1;
import static com.hartwig.hmftools.svprep.SvPrepTestUtils.createSamRecord;
import static com.hartwig.hmftools.svprep.SvPrepTestUtils.readIdStr;

import static junit.framework.TestCase.assertEquals;

import com.hartwig.hmftools.common.utils.sv.ChrBaseRegion;

import org.junit.Test;

public class PartitionBucketsTest
{
    private static final String REF_BASES = generateRandomBases(500);

    @Test
    public void testBucketJunctions()
    {
        ChrBaseRegion partitionRegion = new ChrBaseRegion(CHR_1, 1, 5000);
        PartitionBuckets partitionBuckets = new PartitionBuckets(partitionRegion, 5000, 1000);

        // create junctions in the first and second buckets
        int readId = 0;

        ReadRecord read1 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 800, REF_BASES.substring(0, 100), "30S70M"));

        ReadRecord read2 = ReadRecord.from(createSamRecord(
                readIdStr(readId), CHR_1, 820, REF_BASES.substring(20, 120), "100M"));

        ReadGroup readGroup1 = new ReadGroup(read1);
        readGroup1.addRead(read2);

        partitionBuckets.findBucket(readGroup1.minStartPosition()).addReadGroup(readGroup1);

        ReadRecord suppRead1 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 800, REF_BASES.substring(0, 73), "3S70M"));

        partitionBuckets.findBucket(suppRead1.start()).addSupportingRead(suppRead1);

        // spanning read but with the junction still in the first bucket
        ReadRecord read3 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 950, REF_BASES.substring(0, 100), "30S70M"));

        ReadRecord read4 = ReadRecord.from(createSamRecord(
                readIdStr(readId), CHR_1, 980, REF_BASES.substring(20, 120), "100M"));

        ReadGroup readGroup2 = new ReadGroup(read3);
        readGroup2.addRead(read4);

        partitionBuckets.findBucket(readGroup2.minStartPosition()).addReadGroup(readGroup2);

        ReadRecord suppRead2 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 950, REF_BASES.substring(0, 73), "3S70M"));

        partitionBuckets.findBucket(suppRead2.start()).addSupportingRead(suppRead2);

        // a read group starting in the first bucket but with the junction in the next
        ReadRecord read5 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 950, REF_BASES.substring(20, 120), "100M"));

        ReadRecord read6 = ReadRecord.from(createSamRecord(
                readIdStr(readId), CHR_1, 980, REF_BASES.substring(0, 100), "70M30S"));

        ReadGroup readGroup3 = new ReadGroup(read5);
        readGroup3.addRead(read6);

        partitionBuckets.findBucket(readGroup3.minStartPosition()).addReadGroup(readGroup3);

        ReadRecord suppRead3 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 990, REF_BASES.substring(0, 63), "60M3S"));

        partitionBuckets.findBucket(suppRead3.start()).addSupportingRead(suppRead3);

        // and a junction in the next bucket but with a supporting read in the previous
        ReadRecord read7 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 1010, REF_BASES.substring(10, 90), "50M30S"));

        ReadRecord read8 = ReadRecord.from(createSamRecord(
                readIdStr(readId), CHR_1, 1010, REF_BASES.substring(0, 50), "50M"));

        ReadGroup readGroup4 = new ReadGroup(read7);
        readGroup3.addRead(read8);

        partitionBuckets.findBucket(readGroup4.minStartPosition()).addReadGroup(readGroup4);

        ReadRecord suppRead4 = ReadRecord.from(createSamRecord(
                readIdStr(++readId), CHR_1, 990, REF_BASES.substring(0, 73), "70M3S"));

        partitionBuckets.findBucket(readGroup1.minStartPosition()).addSupportingRead(suppRead4);

        assertEquals(2, partitionBuckets.getBucketCount());
        SvBucket bucket1 = partitionBuckets.getBuckets().get(0);
        bucket1.assignJunctionReads();
        partitionBuckets.transferToNext(bucket1);

        assertEquals(2, bucket1.junctions().size());
        assertEquals(1, bucket1.junctions().get(0).supportingReadCount());
        assertEquals(1, bucket1.junctions().get(1).supportingReadCount());

        assertEquals(2, partitionBuckets.getBucketCount());

        SvBucket bucket2 = partitionBuckets.getBuckets().get(1);
        assertEquals(1, bucket2.supportingReads().size()); //  only one unassigned, transferred from the previous bucket
        bucket2.assignJunctionReads();

        assertEquals(2, bucket2.junctions().size());
        assertEquals(1, bucket2.junctions().get(0).supportingReadCount());
        assertEquals(1, bucket2.junctions().get(1).supportingReadCount());
    }
}
