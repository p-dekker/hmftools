package com.hartwig.hmftools.markdups;

import static java.lang.Math.abs;
import static java.lang.String.format;

import static com.hartwig.hmftools.common.samtools.SamRecordUtils.UMI_CONSENSUS_ATTRIBUTE;
import static com.hartwig.hmftools.markdups.MarkDupsConfig.MD_LOGGER;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.DUPLICATE;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.SUPPLEMENTARY;
import static com.hartwig.hmftools.markdups.common.FragmentStatus.UNSET;
import static com.hartwig.hmftools.markdups.common.FragmentUtils.readToString;
import static com.hartwig.hmftools.markdups.ReadOutput.DUPLICATES;
import static com.hartwig.hmftools.markdups.ReadOutput.MISMATCHES;
import static com.hartwig.hmftools.markdups.ReadOutput.NONE;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.MATE_CIGAR_ATTRIBUTE;
import static com.hartwig.hmftools.common.samtools.SamRecordUtils.SUPPLEMENTARY_ATTRIBUTE;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.closeBufferedWriter;
import static com.hartwig.hmftools.common.utils.FileWriterUtils.createBufferedWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.samtools.SupplementaryReadData;
import com.hartwig.hmftools.markdups.common.Fragment;
import com.hartwig.hmftools.markdups.common.FragmentStatus;
import com.hartwig.hmftools.markdups.umi.UmiGroup;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;

public class RecordWriter
{
    private final MarkDupsConfig mConfig;

    private final BamWriter mBamWriter;
    private final BamWriter mCandidateBamWriter;
    private final BamWriter mSupplementaryBamWriter;
    private final BufferedWriter mReadWriter;
    private final BufferedWriter mResolvedReadWriter;

    private boolean mCacheReads;
    private final Set<SAMRecord> mReadsWritten; // debug only

    private class BamWriter
    {
        private final String mFilename;
        private final SAMFileWriter mBamWriter;
        private int mWriteCount;

        public BamWriter(final String filename)
        {
            mFilename = filename;
            mBamWriter = filename != null ? initialiseBam(filename) : null;
            mWriteCount = 0;
        }

        public void writeRecord(final SAMRecord read)
        {
            ++mWriteCount;

            if(mBamWriter != null)
                mBamWriter.addAlignment(read);
        }

        public String filename() { return mFilename; }
        public int writeCount() { return mWriteCount; }

        public void close()
        {
            if(mBamWriter != null)
                mBamWriter.close();
        }
    }

    public RecordWriter(final MarkDupsConfig config)
    {
        mConfig = config;
        mCacheReads = config.runReadChecks();

        if(mConfig.WriteBam)
        {
            String bamFilename = formBamFilename("mark_dups");
            MD_LOGGER.info("writing new BAM file: {}", bamFilename);
            mBamWriter = new BamWriter(bamFilename);
        }
        else
        {
            mBamWriter = new BamWriter(null);
        }

        if(mConfig.UseInterimFiles)
        {
            mCandidateBamWriter = new BamWriter(formBamFilename("candidate"));
            mSupplementaryBamWriter = new BamWriter(formBamFilename("supplementary"));
            mResolvedReadWriter = initialiseResolvedReadWriter();
        }
        else
        {
            mCandidateBamWriter = null;
            mSupplementaryBamWriter = null;
            mResolvedReadWriter = null;
        }

        mReadWriter = initialiseReadWriter();
        mReadsWritten = Sets.newHashSet();
    }

    private SAMFileWriter initialiseBam(final String filename)
    {
        SamReader samReader = SamReaderFactory.makeDefault().referenceSequence(new File(mConfig.RefGenomeFile)).open(new File(mConfig.BamFile));

        SAMFileHeader fileHeader = samReader.getFileHeader().clone();
        fileHeader.setSortOrder(SAMFileHeader.SortOrder.unsorted);

        return new SAMFileWriterFactory().makeBAMWriter(fileHeader, false, new File(filename));
    }

    private String formBamFilename(final String type)
    {
        String filename = mConfig.OutputDir + mConfig.SampleId + "." + type;

        if(mConfig.OutputId != null)
            filename += "." + mConfig.OutputId;

        filename += ".bam";
        return filename;
    }

    public int recordWriteCount() { return mBamWriter.writeCount(); }

    public synchronized void writeFragments(final List<Fragment> fragments) { fragments.forEach(x -> doWriteFragment(x)); }
    public synchronized void writeFragment(final Fragment fragment) { doWriteFragment(fragment); }

    public synchronized void writeUmiReads(final UmiGroup umiGroup, final List<SAMRecord> completeReads)
    {
        for(SAMRecord read : completeReads)
        {
            if(read.hasAttribute(UMI_CONSENSUS_ATTRIBUTE))
            {
                mBamWriter.writeRecord(read);

                if(mCacheReads)
                        mReadsWritten.add(read);

                continue;
            }

            Fragment fragment = new Fragment(read);
            fragment.setUmiId(umiGroup.umiId());
            fragment.setStatus(DUPLICATE);
            doWriteFragment(fragment);
        }
    }

    private void doWriteFragment(final Fragment fragment)
    {
        if(fragment.readsWritten())
        {
            MD_LOGGER.error("fragment({}) reads already written", fragment);
            return;
        }

        fragment.setReadWritten();
        fragment.reads().forEach(x -> writeRead(x, fragment));
    }

    private void writeRead(final SAMRecord read, final Fragment fragment)
    {
        if(mCacheReads)
        {
            if(mReadsWritten.contains(read))
            {
                MD_LOGGER.error("read({}) already written", readToString(read));
            }
            else
            {
                mReadsWritten.add(read);
            }
        }

        writeReadData(read, fragment);

        read.setDuplicateReadFlag(fragment.status() == DUPLICATE); // overwrite any existing status

        mBamWriter.writeRecord(read);
    }

    private BufferedWriter initialiseReadWriter()
    {
        if(mConfig.LogReadType == NONE)
            return null;

        try
        {
            String filename = mConfig.formFilename("reads");
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("ReadId,Chromosome,PosStart,PosEnd,Cigar");
            writer.write(",InsertSize,MateChr,MatePosStart,Duplicate,CalcDuplicate,MateCigar,Coords");

            if(mConfig.UMIs.Enabled)
                writer.write(",UmiId");

            writer.write(",AvgBaseQual,MapQual,SuppData,Flags,FirstInPair,ReadReversed,Unmapped,MateUnmapped,Supplementary,Secondary");

            writer.newLine();

            return writer;
        }
        catch(IOException e)
        {
            MD_LOGGER.error(" failed to create read writer: {}", e.toString());
        }

        return null;
    }

    private void writeReadData(final SAMRecord read, final Fragment fragment)
    {
        if(mReadWriter == null)
            return;

        if(mConfig.LogReadType == DUPLICATES)
        {
            if(!read.getDuplicateReadFlag() && !fragment.status().isDuplicate())
                return;
        }
        else if(mConfig.LogReadType == MISMATCHES)
        {
            if(fragment.status() != UNSET)
            {
                if(read.getDuplicateReadFlag() == (fragment.status() == DUPLICATE))
                    return;
            }
        }

        try
        {
            mReadWriter.write(format("%s,%s,%d,%d,%s",
                    read.getReadName(), read.getContig(), read.getAlignmentStart(), read.getAlignmentEnd(), read.getCigar()));

            SupplementaryReadData suppData = SupplementaryReadData.from(read.getStringAttribute(SUPPLEMENTARY_ATTRIBUTE));

            mReadWriter.write(format(",%d,%s,%d,%s,%s,%s,%s",
                    abs(read.getInferredInsertSize()), read.getMateReferenceName(), read.getMateAlignmentStart(),
                    read.getDuplicateReadFlag(), fragment.status(), read.hasAttribute(MATE_CIGAR_ATTRIBUTE), fragment.coordinates().Key));

            if(mConfig.UMIs.Enabled)
            {
                mReadWriter.write(format(",%s", fragment.umiId() != null ? fragment.umiId() : ""));
            }

            mReadWriter.write(format(",%.2f,%d,%s,%d",
                    fragment.averageBaseQual(), read.getMappingQuality(), suppData != null ? suppData.asCsv() : "N/A", read.getFlags()));

            mReadWriter.write(format(",%s,%s,%s,%s,%s,%s",
                    read.getFirstOfPairFlag(), read.getReadNegativeStrandFlag(), read.getReadUnmappedFlag(),
                    read.getMateUnmappedFlag(), read.getSupplementaryAlignmentFlag(), read.isSecondaryAlignment()));

            mReadWriter.newLine();
        }
        catch(IOException e)
        {
            MD_LOGGER.error(" failed to write read data: {}", e.toString());
        }
    }

    // interim files & caching routines, not in use
    public synchronized void writeCachedFragment(final Fragment fragment) { doWriteCachedFragment(fragment); }

    private void doWriteCachedFragment(final Fragment fragment)
    {
        if(!mConfig.UseInterimFiles)
            return;

        if(fragment.status() == SUPPLEMENTARY)
        {
            fragment.reads().forEach(x -> mSupplementaryBamWriter.writeRecord(x));
        }
        else
        {
            fragment.reads().forEach(x -> mCandidateBamWriter.writeRecord(x));
        }
    }

    private BufferedWriter initialiseResolvedReadWriter()
    {
        try
        {
            String filename = mConfig.formFilename("resolved_fragments");
            BufferedWriter writer = createBufferedWriter(filename, false);

            writer.write("ReadId,Status,RemotePartition");
            writer.newLine();

            return writer;
        }
        catch(IOException e)
        {
            MD_LOGGER.error(" failed to create resolved fragments writer: {}", e.toString());
        }

        return null;
    }

    public void writeResolvedReadData(final String readId, final FragmentStatus status, final String chrPartition)
    {
        if(mResolvedReadWriter == null)
            return;

        try
        {
            mResolvedReadWriter.write(format("%s,%s,%s", readId, status, chrPartition));
            mResolvedReadWriter.newLine();
        }
        catch(IOException e)
        {
            MD_LOGGER.error(" failed to write resolved fragment: {}", e.toString());
        }
    }

    public void closeInterimFiles()
    {
        if(mCandidateBamWriter != null)
        {
            MD_LOGGER.info("{} candidate reads written", mCandidateBamWriter.writeCount());
            mCandidateBamWriter.close();
        }

        if(mSupplementaryBamWriter != null)
        {
            MD_LOGGER.info("{} supplementary reads written", mSupplementaryBamWriter.writeCount());
            mSupplementaryBamWriter.close();
        }

        closeBufferedWriter(mResolvedReadWriter);
    }

    public void close()
    {
        MD_LOGGER.info("{} records written to BAM", mBamWriter.writeCount());
        mBamWriter.close();

        closeBufferedWriter(mReadWriter);
    }

    public Set<SAMRecord> readsWritten() { return mReadsWritten; }

    @VisibleForTesting
    public void setCacheReads() { mCacheReads = true;}
}
