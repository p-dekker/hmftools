package com.hartwig.hmftools.esvee.read;

import static java.lang.Math.max;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.samtools.CigarUtils.cigarElementsFromStr;
import static com.hartwig.hmftools.common.samtools.CigarUtils.cigarStringFromElements;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.NUM_MUTATONS_ATTRIBUTE;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.SUPPLEMENTARY_ATTRIBUTE;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.getMateAlignmentEnd;
import static com.hartwig.hmftools.common.samtools.SupplementaryReadData.extractAlignment;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.NEG_ORIENT;
import static com.hartwig.hmftools.common.utils.sv.SvCommonUtils.POS_ORIENT;
import static com.hartwig.hmftools.esvee.SvConstants.BAM_HEADER_SAMPLE_ID_TAG;
import static com.hartwig.hmftools.esvee.read.ReadUtils.copyArray;

import static htsjdk.samtools.CigarOperator.S;
import static htsjdk.samtools.util.StringUtil.bytesToString;

import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;
import com.hartwig.hmftools.esvee.common.AssemblySupport;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.SAMRecord;

public class Read
{
    private final SAMRecord mRecord;

    // cached state and adjusted properties of the read
    private String mCigarString;
    private List<CigarElement> mCigarElements;

    private int mAlignmentStart;
    private int mAlignmentEnd;
    private int mUnclippedStart;
    private int mUnclippedEnd;
    private Integer mNumberOfEvents;
    private Integer mMateAlignmentEnd;
    private byte[] mBases;
    private byte[] mBaseQuals;

    // fragment state
    private Read mMateRead;
    private Read mSupplementaryRead;
    private boolean mSuppDataExtracted;
    private SupplementaryReadData mSupplementaryData;

    private boolean mIsReference;

    public Read(final SAMRecord record)
    {
        mRecord = record;

        mCigarString = record.getCigarString();
        mCigarElements = cigarElementsFromStr(mCigarString);

        setBoundaries(mRecord.getAlignmentStart());
        mNumberOfEvents = null;
        mBases = null;
        mBaseQuals = null;
        mMateAlignmentEnd = null;
        mIsReference = false;
        mMateRead = null;
        mSupplementaryRead = null;
        mSuppDataExtracted = false;
        mSupplementaryData = null;
    }

    private void setBoundaries(int newReadStart)
    {
        mAlignmentStart = newReadStart;
        mUnclippedStart = mAlignmentStart;

        if(mCigarElements.isEmpty())
        {
            // undefined for unmapped reads
            mAlignmentEnd = mAlignmentStart;
            mUnclippedEnd = mAlignmentStart;
            return;
        }

        int currentPosition = mAlignmentStart;

        for(int i = 0; i < mCigarElements.size(); ++i)
        {
            CigarElement element = mCigarElements.get(i);

            if(i == 0 && element.getOperator() == S)
                mUnclippedStart -= element.getLength();

            if(element.getOperator().consumesReferenceBases())
                currentPosition += element.getLength();

            if(i == mCigarElements.size() - 1)
            {
                mAlignmentEnd = currentPosition - 1;

                mUnclippedEnd = element.getOperator() == S ? mAlignmentEnd + element.getLength() : mAlignmentEnd;
            }
        }
    }

    public SAMRecord bamRecord() { return mRecord; }

    public void setMateRead(final Read mate)
    {
        mMateRead = mate;
        mMateAlignmentEnd = mate.alignmentEnd();
    }

    public boolean hasMateSet() { return mMateRead != null; }
    public Read mateRead() { return mMateRead; }

    public String getName() { return mRecord.getReadName(); }

    public String chromosome() { return mRecord.getReferenceName(); }

    public List<CigarElement> cigarElements() { return mCigarElements; }
    public String cigarString() { return mCigarString; }
    private void updateCigarString() { mCigarString = cigarStringFromElements(mCigarElements); }

    @Deprecated
    public Cigar getCigar() { return mRecord.getCigar(); }

    public int alignmentStart() { return mAlignmentStart; }
    public int alignmentEnd() { return mAlignmentEnd; }

    public int unclippedStart()  { return mUnclippedStart; }
    public int unclippedEnd() { return mUnclippedEnd; }

    // convenience
    public boolean isLeftClipped() { return mUnclippedStart < mAlignmentStart; }
    public boolean isRightClipped() { return mUnclippedEnd > mAlignmentEnd; }
    public int leftClipLength() { return mAlignmentStart - mUnclippedStart; }
    public int rightClipLength() { return mUnclippedEnd - mAlignmentEnd; }

    public byte[] getBases() { return mBases != null ? mBases : mRecord.getReadBases(); }
    public byte[] getBaseQuality() { return mBaseQuals != null ? mBaseQuals : mRecord.getBaseQualities(); }
    public int basesLength() { return mBases != null ? mBases.length : mRecord.getReadBases().length; }
    public int insertSize() { return mRecord.getInferredInsertSize(); }

    // flags
    public int getFlags() { return mRecord.getFlags(); }
    public boolean isUnmapped() { return mRecord.getReadUnmappedFlag(); }
    public boolean isPairedRead() { return mRecord.getReadPairedFlag(); }
    public boolean isFirstOfPair() { return mRecord.getReadPairedFlag() && mRecord.getFirstOfPairFlag(); }

    public boolean positiveStrand() { return !mRecord.getReadNegativeStrandFlag(); }
    public boolean negativeStrand() { return mRecord.getReadNegativeStrandFlag(); }
    public byte orientation() { return mRecord.getReadNegativeStrandFlag() ? NEG_ORIENT : POS_ORIENT; }

    public boolean firstInPair() { return mRecord.getReadPairedFlag() && mRecord.getFirstOfPairFlag(); }
    public boolean secondInPair() { return mRecord.getReadPairedFlag() && mRecord.getSecondOfPairFlag(); }

    public int mappingQuality() { return mRecord.getMappingQuality(); }

    public String mateChromosome() { return isMateMapped() ? mRecord.getMateReferenceName() : null; }
    public int mateAlignmentStart() { return mRecord.getMateAlignmentStart(); }

    public int mateAlignmentEnd()
    {
        if(mMateAlignmentEnd != null)
            return mMateAlignmentEnd;

        if(isMateUnmapped())
            return alignmentEnd();

        if(mMateRead != null)
            return mMateRead.alignmentEnd();

        mMateAlignmentEnd = getMateAlignmentEnd(mRecord);
        return mMateAlignmentEnd;
    }

    public boolean isMateMapped() { return mRecord.getReadPairedFlag() && !mRecord.getMateUnmappedFlag(); }
    public boolean isMateUnmapped() { return mRecord.getReadPairedFlag() && mRecord.getMateUnmappedFlag(); }

    public boolean matePositiveStrand() { return !mRecord.getMateNegativeStrandFlag(); }
    public boolean mateNegativeStrand() { return mRecord.getMateNegativeStrandFlag(); }

    public boolean hasSupplementary() { return supplementaryData() != null; }

    public void setSupplementaryRead(final Read mate) { mSupplementaryRead = mate; }
    public Read supplementaryRead() { return mSupplementaryRead; }

    public void makeReadLinks(final Read other)
    {
        if(mRecord.getSupplementaryAlignmentFlag() != other.bamRecord().getSupplementaryAlignmentFlag()
        && firstInPair() == other.firstInPair())
        {
            mSupplementaryRead = other;
            other.setSupplementaryRead(this);
        }
        else if(mRecord.getSupplementaryAlignmentFlag() == other.bamRecord().getSupplementaryAlignmentFlag()
            && firstInPair() != other.firstInPair())
        {
            mMateRead = other;
            other.setMateRead(this);
        }
    }

    public SupplementaryReadData supplementaryData()
    {
        if(!mSuppDataExtracted)
        {
            mSuppDataExtracted = true;
            mSupplementaryData = extractAlignment(mRecord);
        }

        return mSupplementaryData;
    }

    public static final int INVALID_INDEX = -1;

    public int getReadIndexAtReferencePosition(final int refPosition)
    {
        return getReadIndexAtReferencePosition(refPosition, false);
    }

    public int getReadIndexAtReferencePosition(final int refPosition, boolean allowExtrapolation)
    {
        // finds the read index given a reference position, and extrapolates outwards from alignments as required
        if(refPosition <= mAlignmentStart)
        {
            if(!allowExtrapolation && refPosition < mAlignmentStart)
                return INVALID_INDEX;

            int baseDiff = mAlignmentStart - refPosition;
            int softClipBases = mAlignmentStart - mUnclippedStart;
            return baseDiff <= softClipBases ? softClipBases - baseDiff : INVALID_INDEX;
        }
        else if(refPosition >= mAlignmentEnd)
        {
            if(!allowExtrapolation && refPosition > mAlignmentEnd)
                return INVALID_INDEX;

            int baseDiff = refPosition - mAlignmentEnd;
            int softClipBases = mUnclippedEnd - mAlignmentEnd;
            return baseDiff <= softClipBases ? basesLength() - (softClipBases - baseDiff) - 1 : INVALID_INDEX;
        }

        // cannot use standard method since CIGAR and coords may have been adjusted
        int readIndex = 0;
        int currentPos = mAlignmentStart;
        for(CigarElement element : mCigarElements)
        {
            if(!element.getOperator().consumesReferenceBases())
            {
                readIndex += element.getLength();
                continue;
            }

            if(currentPos == refPosition)
                break;

            if(!element.getOperator().consumesReadBases())
            {
                // for a D or N where the position is inside it, return the read index for the start of the element
                if(refPosition >= currentPos && refPosition < currentPos + element.getLength())
                    return readIndex - 1;

                currentPos += element.getLength();
            }
            else
            {
                // pos = 100, element = 10M, covering pos 100-109, read index 4 (say after 4S), ref pos at last base of element = 109
                if(refPosition >= currentPos && refPosition < currentPos + element.getLength())
                    return readIndex + refPosition - currentPos;

                currentPos += element.getLength();
                readIndex += element.getLength();
            }
        }

        return readIndex;
    }

    public Object getAttribute(final String name) { return mRecord.getAttribute(name); }

    public int numberOfEvents()
    {
        if(mNumberOfEvents != null)
            return mNumberOfEvents;

        Object numOfEvents = mRecord.getAttribute(NUM_MUTATONS_ATTRIBUTE);

        mNumberOfEvents = numOfEvents != null ? (int)numOfEvents : 0;
        return mNumberOfEvents;
    }

    public boolean matchesFragment(final Read other) { return this == other || getName().equals(other.getName()); }

    public String toString()
    {
        return format("id(%s) coords(%s:%d-%d) cigar(%s) mate(%s:%d) flags(%d)",
                getName(), chromosome(), mAlignmentStart, mAlignmentEnd, mCigarString,
                mateChromosome(), mateAlignmentStart(), mRecord.getFlags());
    }

    public String sampleName() { return mRecord.getHeader().getAttribute(BAM_HEADER_SAMPLE_ID_TAG); }
    public boolean isReference() { return mIsReference; }
    public void markReference() { mIsReference = true; }

    @VisibleForTesting
    public String getBasesString() { return bytesToString(getBases()); }

    public void trimBases(int count, boolean fromStart)
    {
        int remainingBases = count;
        int newBaseLength = max(basesLength() - count, 1);
        byte[] newBases = new byte[newBaseLength];
        byte[] newBaseQuals = new byte[newBaseLength];

        int newReadStart = mAlignmentStart;

        if(fromStart)
        {
            while(remainingBases > 0)
            {
                CigarElement element = mCigarElements.get(0);

                if(element.getLength() <= remainingBases)
                {
                    mCigarElements.remove(0);
                    remainingBases -= element.getLength();

                    if(element.getOperator().consumesReferenceBases())
                        newReadStart += element.getLength();
                }
                else
                {
                    mCigarElements.set(0, new CigarElement(element.getLength() - remainingBases, element.getOperator()));

                    if(element.getOperator().consumesReferenceBases())
                        newReadStart += remainingBases;

                    remainingBases = 0;
                }
            }

            copyArray(getBases(), newBases, count, 0);
            copyArray(getBaseQuality(), newBaseQuals, count, 0);
        }
        else
        {
            while(remainingBases > 0)
            {
                int lastIndex = mCigarElements.size() - 1;
                CigarElement element = mCigarElements.get(lastIndex);

                if(element.getLength() <= remainingBases)
                {
                    mCigarElements.remove(lastIndex);
                    remainingBases -= element.getLength();
                }
                else
                {
                    mCigarElements.set(lastIndex, new CigarElement(element.getLength() - remainingBases, element.getOperator()));
                    remainingBases = 0;
                }
            }

            copyArray(getBases(), newBases, 0, 0);
            copyArray(getBaseQuality(), newBaseQuals, 0, 0);
        }

        mBases = newBases;
        mBaseQuals = newBaseQuals;

        updateCigarString();
        setBoundaries(newReadStart);
    }

    public void convertEdgeIndelToSoftClip(int leftSoftClipBases, int rightSoftClipBases)
    {
        // convert elements and recompute read state
        int newReadStart = mAlignmentStart;

        if(leftSoftClipBases > 0)
        {
            newReadStart += mCigarElements.get(0).getLength(); // moves by the M alignment at the first position
            mCigarElements.remove(0);
            mCigarElements.set(0, new CigarElement(leftSoftClipBases, S));
        }

        if(rightSoftClipBases > 0)
        {
            mCigarElements.remove(mCigarElements.size() - 1);
            mCigarElements.set(mCigarElements.size() - 1, new CigarElement(rightSoftClipBases, S));
        }

        updateCigarString();
        setBoundaries(newReadStart);
    }
}
