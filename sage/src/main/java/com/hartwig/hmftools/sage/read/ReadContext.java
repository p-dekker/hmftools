package com.hartwig.hmftools.sage.read;

import com.google.common.annotations.VisibleForTesting;

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;

import htsjdk.samtools.SAMRecord;

public class ReadContext {

    private final int position;
    private final int distance;
    private final String distanceCigar;
    private final String repeat;
    private final int repeatCount;
    private final String microhomology;

    private final IndexedBases readBases;

//    private final int refIndex;
//    private final byte refBases[];

    public ReadContext(final String repeat, final int refPosition, final int readIndex, final int leftCentreIndex,
            final int rightCentreIndex, final int flankSize, final byte[] readBases) {
        assert (leftCentreIndex >= 0);
        assert (rightCentreIndex >= leftCentreIndex);

        this.position = refPosition;
        this.distance = 0;
        this.distanceCigar = Strings.EMPTY;
        this.repeat = repeat;
        this.microhomology = Strings.EMPTY;
        this.repeatCount = 0;
        this.readBases = new IndexedBases(refPosition, readIndex, leftCentreIndex, rightCentreIndex, flankSize, readBases);
    }

    ReadContext(final String microhomology, int repeatCount, final String repeat, final int refPosition, final int readIndex,
            final int leftCentreIndex, final int rightCentreIndex, final int flankSize, IndexedBases refBases, @NotNull final SAMRecord record) {
        assert (leftCentreIndex >= 0);
        assert (rightCentreIndex >= leftCentreIndex);

        this.position = refPosition;
        this.repeat = repeat;
        this.repeatCount = repeatCount;
        this.microhomology = microhomology;

        int recordLeftFlankStartIndex = Math.max(0, leftCentreIndex - flankSize);
        int recordRightFlankEndIndex = Math.min(record.getReadBases().length - 1, rightCentreIndex + flankSize);


        ReadContextDistance distance = new ReadContextDistance(recordLeftFlankStartIndex, recordRightFlankEndIndex, record, refBases);
        this.distance = distance.distance();
        this.distanceCigar = distance.cigar();
        this.readBases = IndexedBases.resize(refPosition, readIndex, leftCentreIndex, rightCentreIndex, flankSize, record.getReadBases());
    }

    public int position() {
        return position;
    }

    int distanceFromReadEdge(int readIndex, SAMRecord record) {
        int leftOffset = this.readIndex() - readBases.leftCentreIndex();
        int rightOffset = readBases.rightCentreIndex() - this.readIndex();

        int leftIndex = readIndex - leftOffset;
        int rightIndex = readIndex + rightOffset;

        return Math.min(leftIndex, record.getReadBases().length - rightIndex - 1);
    }

    public boolean isComplete() {
        return readBases.flanksComplete();
    }

    public boolean isFullMatch(@NotNull final ReadContext other) {
        return isComplete() && other.isComplete() && centreMatch(other.readIndex(), other.readBases())
                && leftFlankMatchingBases(other.readIndex(), other.readBases()) == flankSize()
                && rightFlankMatchingBases(other.readIndex(), other.readBases()) == flankSize();
    }

    int minCentreQuality(int readIndex, SAMRecord record) {
        int leftOffset = this.readIndex() - readBases.leftCentreIndex();
        int rightOffset = readBases.rightCentreIndex() - this.readIndex();

        int leftIndex = readIndex - leftOffset;
        int rightIndex = readIndex + rightOffset;

        int quality = Integer.MAX_VALUE;
        for (int i = leftIndex; i <= rightIndex; i++) {
            quality = Math.min(quality, record.getBaseQualities()[i]);
        }
        return quality;
    }

    public boolean phased(int offset, @NotNull final ReadContext other) {
        return readBases.phased(offset, other.readBases);
    }

    public boolean isCentreCovered(int otherReadIndex, byte[] otherBases) {
        return readBases.isCentreCovered(otherReadIndex, otherBases);
    }

    @NotNull
    public ReadContextMatch matchAtPosition(int otherReadIndex, byte[] otherBases) {
        return readBases.matchAtPosition(otherReadIndex, otherBases);
    }

    @VisibleForTesting
    boolean centreMatch(int otherRefIndex, byte[] otherBases) {
        return readBases.centreMatch(otherRefIndex, otherBases);
    }

    @VisibleForTesting
    int leftFlankMatchingBases(int otherRefIndex, byte[] otherBases) {
        return readBases.leftFlankMatchingBases(otherRefIndex, otherBases);
    }

    @VisibleForTesting
    int rightFlankMatchingBases(int otherRefIndex, byte[] otherBases) {
        return readBases.rightFlankMatchingBases(otherRefIndex, otherBases);
    }


    public int leftFlankStartIndex() {
        return Math.max(0, readBases.leftCentreIndex() - flankSize());
    }


    public int rightFlankEndIndex() {
        return Math.min(readBases().length - 1, readBases.rightCentreIndex() + flankSize());
    }


    @Override
    public String toString() {
        return readBases.toString();
    }

    public int distance() {
        return distance;
    }

    public String distanceCigar() {
        return distanceCigar;
    }

    @VisibleForTesting
    @NotNull
    String centerBases() {
        return readBases.centerString();
    }

    @NotNull
    public String microhomology() {
        return microhomology;
    }

    @NotNull
    public String repeat() {
        return repeat;
    }

    public int repeatCount() {
        return repeatCount;
    }

    public byte[] readBases() {
        return readBases.bases();
    }

    private int readIndex() {
        return readBases.index();
    }

    @NotNull
    public String mnvAdditionalAlt(int length) {
        return new String(readBases(), readIndex() - length + 1, length);
    }

    public int flankSize() {
        return readBases.flankSize();
    }

}
