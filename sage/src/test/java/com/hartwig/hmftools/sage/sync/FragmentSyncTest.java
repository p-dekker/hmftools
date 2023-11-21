package com.hartwig.hmftools.sage.sync;

import static com.hartwig.hmftools.common.samtools.CigarUtils.cigarFromStr;
import static com.hartwig.hmftools.common.test.GeneTestUtils.CHR_1;
import static com.hartwig.hmftools.common.test.MockRefGenome.generateRandomBases;
import static com.hartwig.hmftools.common.test.MockRefGenome.getNextBase;
import static com.hartwig.hmftools.sage.common.TestUtils.QUALITY_CALCULATOR;
import static com.hartwig.hmftools.sage.common.TestUtils.RECALIBRATION;
import static com.hartwig.hmftools.sage.common.TestUtils.TEST_CONFIG;
import static com.hartwig.hmftools.sage.common.TestUtils.createReadContext;
import static com.hartwig.hmftools.sage.common.TestUtils.createSamRecord;
import static com.hartwig.hmftools.sage.sync.FragmentSyncUtils.compatibleCigars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static htsjdk.samtools.CigarOperator.D;
import static htsjdk.samtools.CigarOperator.I;
import static htsjdk.samtools.CigarOperator.M;
import static htsjdk.samtools.CigarOperator.N;
import static htsjdk.samtools.CigarOperator.S;

import com.hartwig.hmftools.common.variant.hotspot.ImmutableVariantHotspotImpl;
import com.hartwig.hmftools.common.variant.hotspot.VariantHotspot;
import com.hartwig.hmftools.sage.common.IndexedBases;
import com.hartwig.hmftools.sage.common.ReadContext;
import com.hartwig.hmftools.sage.common.TestUtils;
import com.hartwig.hmftools.sage.common.VariantTier;
import com.hartwig.hmftools.sage.evidence.ReadContextCounter;
import com.hartwig.hmftools.sage.quality.QualityCalculator;

import org.apache.logging.log4j.util.Strings;
import org.junit.Test;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.SAMRecord;

public class FragmentSyncTest
{
    private static final String REF_BASES = "X" + generateRandomBases(100);

    @Test
    public void testCombinedRecords()
    {
        String readId = "READ_01";
        String chromosome = "1";

        // first a basic match
        SAMRecord first = createSamRecord(readId, chromosome, 1, REF_BASES.substring(1, 21), "20M");

        SAMRecord second = createSamRecord(readId, chromosome, 5, REF_BASES.substring(5, 25), "20M");

        SAMRecord combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(1, combined.getAlignmentStart());
        assertEquals(24, combined.getAlignmentEnd());
        assertEquals(REF_BASES.substring(1, 25), combined.getReadString());
        assertEquals("24M", combined.getCigarString());

        // soft-clips extending each end by varying amounts
        first = createSamRecord(readId, chromosome, 6, REF_BASES.substring(1, 21), "5S10M5S");

        second = createSamRecord(readId, chromosome, 12, REF_BASES.substring(10, 30), "2S16M2S");

        combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(6, combined.getAlignmentStart());
        assertEquals(27, combined.getAlignmentEnd());
        assertEquals(REF_BASES.substring(1, 30), combined.getReadString());
        assertEquals("5S22M2S", combined.getCigarString());

        // a delete
        String firstBases = REF_BASES.substring(1, 11) + REF_BASES.substring(12, 22);
        first = createSamRecord(readId, chromosome, 1, firstBases, "10M1D10M");

        String secondBases = REF_BASES.substring(6, 11) + REF_BASES.substring(12, 27);
        second = createSamRecord(readId, chromosome, 6, secondBases, "5M1D15M");

        combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(1, combined.getAlignmentStart());
        assertEquals(26, combined.getAlignmentEnd());
        String combinedBases = REF_BASES.substring(1, 11) + REF_BASES.substring(12, 27);
        assertEquals(combinedBases, combined.getReadString());
        assertEquals("10M1D15M", combined.getCigarString());

        // with a longer delete
        firstBases = REF_BASES.substring(1, 11) + REF_BASES.substring(16, 26);
        first = createSamRecord(readId, chromosome, 1, firstBases, "10M5D10M");

        secondBases = REF_BASES.substring(6, 11) + REF_BASES.substring(16, 31);
        second = createSamRecord(readId, chromosome, 6, secondBases, "5M5D15M");

        combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(1, combined.getAlignmentStart());
        assertEquals(30, combined.getAlignmentEnd());
        combinedBases = REF_BASES.substring(1, 11) + REF_BASES.substring(16, 31);
        assertEquals(combinedBases, combined.getReadString());
        assertEquals("10M5D15M", combined.getCigarString());

        // multiple deletes
        firstBases = REF_BASES.substring(1, 11) + REF_BASES.substring(16, 26) + REF_BASES.substring(28, 38) + REF_BASES.substring(41, 46);
        first = createSamRecord(readId, chromosome, 1, firstBases, "10M5D10M2D10M3D5M");

        secondBases = REF_BASES.substring(6, 11) + REF_BASES.substring(16, 26) + REF_BASES.substring(28, 38) + REF_BASES.substring(41, 51);
        second = createSamRecord(readId, chromosome, 6, secondBases, "5M5D10M2D10M3D10M");

        combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(1, combined.getAlignmentStart());
        assertEquals(50, combined.getAlignmentEnd());
        combinedBases = REF_BASES.substring(1, 11) + REF_BASES.substring(16, 26) + REF_BASES.substring(28, 38) + REF_BASES.substring(41, 51);
        assertEquals(combinedBases, combined.getReadString());
        assertEquals("10M5D10M2D10M3D10M", combined.getCigarString());

        // with an insert
        firstBases = REF_BASES.substring(1, 11) + "CCC" + REF_BASES.substring(11, 21);
        first = createSamRecord(readId, chromosome, 1, firstBases, "10M3I10M");

        secondBases = REF_BASES.substring(6, 11) + "CCC" + REF_BASES.substring(11, 26);
        second = createSamRecord(readId, chromosome, 6, secondBases, "5M3I15M");

        combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(1, combined.getAlignmentStart());
        assertEquals(25, combined.getAlignmentEnd());
        combinedBases = REF_BASES.substring(1, 11) + "CCC" + REF_BASES.substring(11, 26);
        assertEquals(combinedBases, combined.getReadString());
        assertEquals("10M3I15M", combined.getCigarString());

        // more complicated example
        firstBases = REF_BASES.substring(1, 11) + "CCC" + REF_BASES.substring(11, 21) + REF_BASES.substring(26, 36) + "AA"
                + REF_BASES.substring(36, 46);

        first = createSamRecord(readId, chromosome, 1, firstBases, "10M3I10M5D10M2I5M5S");

        secondBases = REF_BASES.substring(6, 11) + "CCC" + REF_BASES.substring(11, 21) + REF_BASES.substring(26, 36) + "AA"
                + REF_BASES.substring(36, 51);
        second = createSamRecord(readId, chromosome, 6, secondBases, "5M3I10M5D10M2I15M");

        combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(1, combined.getAlignmentStart());
        assertEquals(50, combined.getAlignmentEnd());
        combinedBases = REF_BASES.substring(1, 11) + "CCC" + REF_BASES.substring(11, 21) + REF_BASES.substring(26, 36) + "AA"
                + REF_BASES.substring(36, 51);

        assertEquals(combinedBases, combined.getReadString());
        assertEquals("10M3I10M5D10M2I15M", combined.getCigarString());
    }

    @Test
    public void testSoftClips()
    {
        String readId = "READ_01";
        String chromosome = "1";

        SAMRecord first = createSamRecord(
                readId, chromosome, 20, REF_BASES.substring(20, 35), "10M5S");

        SAMRecord second = createSamRecord(
                readId, chromosome, 20, REF_BASES.substring(17, 30), "3S10M");

        SAMRecord combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(20, combined.getAlignmentStart());
        assertEquals(29, combined.getAlignmentEnd());
        assertEquals(REF_BASES.substring(17, 35), combined.getReadString());
        assertEquals("3S10M5S", combined.getCigarString());
    }

    @Test
    public void testReadStrandMismatches()
    {
        // one read supports the variant, one doesn't and this factors into the read strand determination
        int position = 20;

        String refBase = REF_BASES.substring(position, position + 1);
        String altBase = getNextBase(refBase);

        final VariantHotspot hotspot = ImmutableVariantHotspotImpl.builder()
                .chromosome(CHR_1).ref(refBase).alt(altBase).position(position).build();

        String readBases = REF_BASES.substring(8, position) + altBase + REF_BASES.substring(position + 1, 33);
        final ReadContext readContext = createReadContext(position, 12, 10, 14, readBases, Strings.EMPTY);

        final IndexedBases REF_INDEXED_BASES = new IndexedBases(1, 0, REF_BASES.getBytes());
        final QualityCalculator QUALITY_CALCULATOR = new QualityCalculator(TEST_CONFIG.Quality, RECALIBRATION, REF_INDEXED_BASES);

        final ReadContextCounter readContextCounter = new ReadContextCounter(
                1, hotspot, readContext, VariantTier.PANEL, 100, 0,
                TEST_CONFIG, QUALITY_CALCULATOR, null);

        String readId = "READ_01";

        SAMRecord first = createSamRecord(
                readId, CHR_1, 10, REF_BASES.substring(10, 40), "30M");
        first.getBaseQualities()[10] = (byte)11;

        SAMRecord second = createSamRecord(
                readId, CHR_1, 10, REF_BASES.substring(10, position) + altBase + REF_BASES.substring(position + 1, 40), "30M");
        second.setReadNegativeStrandFlag(true);

        SAMRecord consensusRead = formFragmentRead(first, second);

        readContextCounter.processRead(consensusRead, 1, new FragmentData(first, second));
        readContextCounter.processRead(consensusRead, 1, new FragmentData(first, second));
        readContextCounter.processRead(consensusRead, 1, new FragmentData(first, second));

        // swap and re-process
        first.setReadNegativeStrandFlag(true);
        second.setReadNegativeStrandFlag(false);

        readContextCounter.processRead(consensusRead, 1, new FragmentData(first, second));

        assertEquals(4, readContextCounter.readStrandBias().depth());
        assertEquals(0.25, readContextCounter.readStrandBias().bias(), 0.01);
    }

    @Test
    public void testSplitReads()
    {
        String readId = "READ_01";
        String chromosome = "1";

        // first a basic match
        SAMRecord first = createSamRecord(
                readId, chromosome, 20, REF_BASES.substring(20, 30) + REF_BASES.substring(60, 80), "10M30N20M");

        SAMRecord second = createSamRecord(
                readId, chromosome, 10, REF_BASES.substring(10, 30) + REF_BASES.substring(60, 70), "20M30N10M");

        SAMRecord combined = formFragmentRead(first, second);
        assertNotNull(combined);
        assertEquals(10, combined.getAlignmentStart());
        assertEquals(79, combined.getAlignmentEnd());
        assertEquals(REF_BASES.substring(10, 30) + REF_BASES.substring(60, 80), combined.getReadString());
        assertEquals("20M30N20M", combined.getCigarString());
    }

    @Test
    public void testMismatches()
    {
        String readId = "READ_01";
        String chromosome = "1";

        // first a basic match
        SAMRecord first = createSamRecord(
                readId, chromosome, 1, REF_BASES.substring(1, 11) + "C" + REF_BASES.substring(11, 21), "10M1I10M");

        SAMRecord second = createSamRecord(readId, chromosome, 5, REF_BASES.substring(5, 25), "20M");

        FragmentSyncOutcome syncOutcome = FragmentSync.formFragmentRead(first, second);
        assertEquals(FragmentSyncType.CIGAR_MISMATCH, syncOutcome.SyncType);

        // off by 1
        first = createSamRecord(
                readId, chromosome, 1, REF_BASES.substring(1, 11) + "C" + REF_BASES.substring(11, 21), "10M1I10M");

        second = createSamRecord(
                readId, chromosome, 2, REF_BASES.substring(1, 12) + "C" + REF_BASES.substring(12, 21), "10M1I10M");

        syncOutcome = FragmentSync.formFragmentRead(first, second);
        assertEquals(FragmentSyncType.CIGAR_MISMATCH, syncOutcome.SyncType);

        // too many mismatches
        first = createSamRecord(readId, chromosome, 1, REF_BASES.substring(1, 21), "20M");
        second = createSamRecord(readId, chromosome, 1, REF_BASES.substring(2, 22), "20M");

        syncOutcome = FragmentSync.formFragmentRead(first, second);
        assertEquals(FragmentSyncType.BASE_MISMATCH, syncOutcome.SyncType);

        // non-overlapping but different INDELs
        first = createSamRecord(
                readId, chromosome, 1, REF_BASES.substring(1, 6) + "C" + REF_BASES.substring(6, 41), "5M1I35M");

        second = createSamRecord(
                readId, chromosome, 30, REF_BASES.substring(30, 40) + REF_BASES.substring(45, 75), "10M5D30M");

        syncOutcome = FragmentSync.formFragmentRead(first, second);
        assertEquals(FragmentSyncType.NO_OVERLAP_CIGAR_DIFF, syncOutcome.SyncType);
    }

    @Test
    public void testTruncatedFragments()
    {
        String readId = "READ_01";
        String chromosome = "1";

        SAMRecord first = createSamRecord(
                readId, chromosome, 5, REF_BASES.substring(5, 35), "30M");
        first.setInferredInsertSize(25);
        first.setReadNegativeStrandFlag(true);

        SAMRecord second = createSamRecord(readId, chromosome, 10, REF_BASES.substring(10, 40), "30M");
        second.setInferredInsertSize(-25);

        FragmentSyncOutcome syncOutcome = FragmentSync.formFragmentRead(first, second);
        assertEquals(25, syncOutcome.CombinedRecord.getInferredInsertSize());
        assertEquals(10, syncOutcome.CombinedRecord.getAlignmentStart());
        assertEquals(34, syncOutcome.CombinedRecord.getAlignmentEnd());
        assertEquals("25M", syncOutcome.CombinedRecord.getCigarString());
        assertEquals(25, syncOutcome.CombinedRecord.getBaseQualities().length);

        String readBases = syncOutcome.CombinedRecord.getReadString();
        assertEquals(REF_BASES.substring(10, 35), readBases);
        assertEquals(FragmentSyncType.COMBINED, syncOutcome.SyncType);

        // test again with soft-clips
        // actual alignment is 11 -> 42 = 22 bases
        int fragLength = 22;
        first = createSamRecord(readId, chromosome, 11, REF_BASES.substring(9, 50), "2S35M4S");
        first.setInferredInsertSize(fragLength);

        second = createSamRecord(
                readId, chromosome, 11, REF_BASES.substring(5, 45), "6S32M2S");
        second.setInferredInsertSize(-fragLength);
        second.setReadNegativeStrandFlag(true);

        syncOutcome = FragmentSync.formFragmentRead(first, second);
        assertEquals(fragLength, syncOutcome.CombinedRecord.getInferredInsertSize());
        assertEquals(11, syncOutcome.CombinedRecord.getAlignmentStart());
        assertEquals(44, syncOutcome.CombinedRecord.getAlignmentEnd());
        assertEquals("2S34M", syncOutcome.CombinedRecord.getCigarString());
        assertEquals(36, syncOutcome.CombinedRecord.getBaseQualities().length);

        readBases = syncOutcome.CombinedRecord.getReadString();
        assertEquals(REF_BASES.substring(9, 45), readBases);
        assertEquals(FragmentSyncType.COMBINED, syncOutcome.SyncType);

        // test again with soft-clips on the 3' ends and a DEL

        fragLength = 43 - 8 + 1;
        first = createSamRecord(
                readId, chromosome, 8, REF_BASES.substring(8, 23) + REF_BASES.substring(28, 45), "15M5D15M2S");
        first.setInferredInsertSize(fragLength);

        second = createSamRecord(readId, chromosome, 11, REF_BASES.substring(5, 23) + REF_BASES.substring(28, 43), "6S12M5D15M");
        second.setInferredInsertSize(-fragLength);
        second.setReadNegativeStrandFlag(true);

        syncOutcome = FragmentSync.formFragmentRead(first, second);
        assertEquals(fragLength, syncOutcome.CombinedRecord.getInferredInsertSize());
        assertEquals(8, syncOutcome.CombinedRecord.getAlignmentStart());
        assertEquals(42, syncOutcome.CombinedRecord.getAlignmentEnd());
        assertEquals("15M5D15M", syncOutcome.CombinedRecord.getCigarString());
        assertEquals(30, syncOutcome.CombinedRecord.getBaseQualities().length);

        readBases = syncOutcome.CombinedRecord.getReadString();
        assertEquals(REF_BASES.substring(8, 23) + REF_BASES.substring(28, 43), readBases);
        assertEquals(FragmentSyncType.COMBINED, syncOutcome.SyncType);
    }

    @Test
    public void testCompatibleCigars()
    {
        Cigar first = new Cigar();
        first.add(new CigarElement(10, S));
        first.add(new CigarElement(30, M));
        first.add(new CigarElement(2, I));
        first.add(new CigarElement(20, M));
        first.add(new CigarElement(5, D));
        first.add(new CigarElement(40, M));
        first.add(new CigarElement(8, S));

        assertTrue(compatibleCigars(first, first));

        // other diffs are not permitted
        Cigar second = new Cigar();
        second.add(new CigarElement(30, M));
        second.add(new CigarElement(3, I));
        second.add(new CigarElement(20, M));
        second.add(new CigarElement(5, D));
        second.add(new CigarElement(40, M));

        assertFalse(compatibleCigars(first, second));

        second.add(new CigarElement(30, M));
        second.add(new CigarElement(13, D));
        second.add(new CigarElement(40, M));

        assertFalse(compatibleCigars(first, second));

        // can differ in soft-clips and aligned lengths
        first = new Cigar();
        first.add(new CigarElement(10, S));
        first.add(new CigarElement(30, M));
        first.add(new CigarElement(12, S));

        second = new Cigar();
        second.add(new CigarElement(8, S));
        second.add(new CigarElement(40, M));
        second.add(new CigarElement(2, S));

        assertTrue(compatibleCigars(first, first));

        second = new Cigar();
        second.add(new CigarElement(40, M));

        assertTrue(compatibleCigars(first, first));

        // can differ in soft-clips and aligned lengths
        first = new Cigar();
        first.add(new CigarElement(40, M));
        first.add(new CigarElement(120, N));
        first.add(new CigarElement(30, M));

        second = new Cigar();
        second.add(new CigarElement(30, M));
        second.add(new CigarElement(120, N));
        second.add(new CigarElement(40, M));

        assertTrue(compatibleCigars(first, first));
    }

    @Test
    public void testUtils()
    {
        CigarBaseCounts baseCounts = new CigarBaseCounts(cigarFromStr("100M"));
        assertEquals(100, baseCounts.AlignedBases);

        baseCounts = new CigarBaseCounts(cigarFromStr("4S50M5D50M12I30M10S"));
        assertEquals(135, baseCounts.AlignedBases);
        assertEquals(7, baseCounts.AdjustedBases);
        assertEquals(4, baseCounts.SoftClipStart);
        assertEquals(10, baseCounts.SoftClipEnd);
    }

    private static SAMRecord formFragmentRead(final SAMRecord first, final SAMRecord second)
    {
        return FragmentSync.formFragmentRead(first, second).CombinedRecord;
    }

}
