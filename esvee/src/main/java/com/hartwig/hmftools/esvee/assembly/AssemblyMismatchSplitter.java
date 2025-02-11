package com.hartwig.hmftools.esvee.assembly;

import static java.lang.String.format;

import static com.hartwig.hmftools.esvee.SvConstants.LOW_BASE_QUAL_THRESHOLD;
import static com.hartwig.hmftools.esvee.SvConstants.PRIMARY_ASSEMBLY_MIN_READ_SUPPORT;
import static com.hartwig.hmftools.esvee.SvConstants.PRIMARY_ASSEMBLY_MIN_MISMATCH_TOTAL_QUAL;
import static com.hartwig.hmftools.esvee.SvConstants.PRIMARY_ASSEMBLY_MAX_BASE_MISMATCH;
import static com.hartwig.hmftools.esvee.common.AssemblyUtils.basesMatch;
import static com.hartwig.hmftools.esvee.common.AssemblyUtils.buildFromJunctionReads;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.esvee.common.AssemblySupport;
import com.hartwig.hmftools.esvee.common.BaseMismatch;
import com.hartwig.hmftools.esvee.common.BaseMismatches;
import com.hartwig.hmftools.esvee.common.JunctionAssembly;
import com.hartwig.hmftools.esvee.read.Read;

public class AssemblyMismatchSplitter
{
    private final JunctionAssembly mSequence;

    public AssemblyMismatchSplitter(final JunctionAssembly sequence)
    {
        mSequence = sequence;
    }

    public List<JunctionAssembly> splitOnMismatches(int minSequenceLength)
    {
        int permittedMismatches = PRIMARY_ASSEMBLY_MAX_BASE_MISMATCH;
        int minReadSupport = PRIMARY_ASSEMBLY_MIN_READ_SUPPORT;

        // every remaining mismatch should have 2+ (or whatever configured) supporting reads
        // build unique collections of mismatches for each long enough read
        List<Read> noMismatchReads = Lists.newArrayList();
        Set<Read> longMismatchReads = Sets.newHashSet();

        for(AssemblySupport support : mSequence.support())
        {
            if(support.junctionMismatches() == 0)
            {
                noMismatchReads.add(support.read());
            }
            else
            {
                if(support.readRangeLength() >= minSequenceLength)
                    longMismatchReads.add(support.read());
            }
        }

        List<SequenceMismatches> uniqueSequenceMismatches = findOtherSequences(longMismatchReads);

        List<JunctionAssembly> finalSequences = Lists.newArrayList();
        Set<Read> processedReads = Sets.newHashSet();

        if(noMismatchReads.size() >= minReadSupport)
        {
            // add the 'initial' sequence from reads without mismatches
            JunctionAssembly initialSequence = buildFromJunctionReads(mSequence.junction(), noMismatchReads, false);
            processedReads.addAll(noMismatchReads);
            finalSequences.add(initialSequence);
        }

        // and then add each unique mismatched sequence
        for(SequenceMismatches sequenceMismatches : uniqueSequenceMismatches)
        {
            List<Read> candidateReads = sequenceMismatches.Reads;

            // required for example 2+ reads and 2+ mismatches to add a unique sequence
            if(candidateReads.size() < minReadSupport || sequenceMismatches.Mismatches.size() <= permittedMismatches)
                continue;

            processedReads.addAll(candidateReads);

            JunctionAssembly mismatchSequence = buildFromJunctionReads(mSequence.junction(), candidateReads, false);
            finalSequences.add(mismatchSequence);
        }

        // remove any sequences which are contained by a longer sequence
        dedupByAssemblyContainsAnother(finalSequences);

        // test each read not already used to define the unique sequence against each final sequence
        for(AssemblySupport support : mSequence.support())
        {
            if(processedReads.contains(support.read()))
                continue;

            for(JunctionAssembly sequence : finalSequences)
            {
                if(sequence.checkReadMatches(support.read(), permittedMismatches))
                {
                    sequence.addJunctionRead(support.read(), false);
                }
            }
        }

        return finalSequences;
    }

    private List<SequenceMismatches> findOtherSequences(final Set<Read> longMismatchReads)
    {
        int minReadSupport = PRIMARY_ASSEMBLY_MIN_READ_SUPPORT;

        if(longMismatchReads.size() <= minReadSupport)
            return Collections.emptyList();

        int maxMismatchQual = PRIMARY_ASSEMBLY_MIN_MISMATCH_TOTAL_QUAL;

        // find all mismatches by read
        Map<Read,SequenceMismatches> readSequenceMismatches = Maps.newHashMap();

        for(Map.Entry<Integer, BaseMismatches> entry : mSequence.mismatches().indexedBaseMismatches().entrySet())
        {
            BaseMismatches baseMismatches = entry.getValue();
            int assemblyIndex = entry.getKey();

            for(int j = 0; j < baseMismatches.Mismatches.length; ++j)
            {
                if(baseMismatches.Mismatches[j] == null)
                    continue;

                BaseMismatch baseMismatch = baseMismatches.Mismatches[j];

                if(baseMismatch.Reads.size() < minReadSupport || baseMismatch.QualTotal < maxMismatchQual)
                    continue;

                Mismatch mismatch = new Mismatch(assemblyIndex, baseMismatch.base(), baseMismatch.MaxQual, baseMismatch.QualTotal);

                for(Read read : baseMismatch.Reads)
                {
                    if(!longMismatchReads.contains(read))
                        continue;

                    SequenceMismatches readMismatches = readSequenceMismatches.get(read);

                    if(readMismatches == null)
                    {
                        readMismatches = new SequenceMismatches(read, mismatch);
                        readSequenceMismatches.put(read, readMismatches);
                    }
                    else
                    {
                        readMismatches.Mismatches.add(mismatch);
                    }
                }
            }
        }

        // now check for identical or compatible (ie containing all) mismatches across the reads
        List<SequenceMismatches> uniqueSequenceMismatches = Lists.newArrayList();
        Set<Read> matchedReads = Sets.newHashSet();

        List<SequenceMismatches> sortedSequenceMismatches = readSequenceMismatches.values().stream()
                .sorted(Collections.reverseOrder(Comparator.comparingInt(x -> x.Mismatches.size()))).collect(Collectors.toList());

        for(SequenceMismatches readMismatches : sortedSequenceMismatches)
        {
            Read read = readMismatches.Reads.get(0);

            if(matchedReads.contains(read))
                continue;

            matchedReads.add(read);

            uniqueSequenceMismatches.add(readMismatches);

            for(SequenceMismatches otherReadMismatches : sortedSequenceMismatches)
            {
                Read otherRead = otherReadMismatches.Reads.get(0);

                if(matchedReads.contains(otherRead))
                    continue;

                if(readMismatches.matchesOrContains(otherReadMismatches))
                {
                    readMismatches.Reads.add(otherRead);
                    matchedReads.add(otherRead);
                }
            }
        }

        return uniqueSequenceMismatches;
    }

    private class Mismatch
    {
        public final int Index;
        public final byte Base;
        public final byte MaxQual;
        public final int TotalQual;

        public Mismatch(final int index, final byte base, final byte maxQual, final int totalQual)
        {
            Index = index;
            Base = base;
            MaxQual = maxQual;
            TotalQual = totalQual;
        }

        public boolean matches(final Mismatch other) { return Index == other.Index && Base == other.Base; }

        public String toString() { return format("%d: %c", Index, (char)Base); }
    }

    private class SequenceMismatches
    {
        public final List<Read> Reads;
        public final List<Mismatch> Mismatches;

        public SequenceMismatches(final Read read, final Mismatch mismatch)
        {
            Reads = Lists.newArrayList(read);
            Mismatches = Lists.newArrayList(mismatch);
        }

        public boolean matchesOrContains(final SequenceMismatches other)
        {
            if(other.Mismatches.size() != Mismatches.size())
                return false;

            for(int i = 0; i < Mismatches.size(); ++i)
            {
                // if(i >= other.Mismatches.size())
                //    return true;

                if(!Mismatches.get(i).matches(other.Mismatches.get(i)))
                    return false;
            }

            return true;
        }

        public String toString() { return format("reads(%d) mismatches(%d)", Reads.size(), Mismatches.size()); }
    }

    private static void dedupByAssemblyContainsAnother(final List<JunctionAssembly> assemblies)
    {
        Collections.sort(assemblies, Collections.reverseOrder(Comparator.comparingInt(x -> x.baseLength())));

        int i = 0;
        while(i < assemblies.size())
        {
            JunctionAssembly first = assemblies.get(i);

            int j = i + 1;
            while(j < assemblies.size())
            {
                JunctionAssembly second = assemblies.get(j);

                if(assemblyContainsAnother(first, second))
                {
                    assemblies.remove(j);
                    first.checkAddReadSupport(second);

                    continue;
                }

                ++j;
            }

            ++i;
        }
    }

    private static boolean assemblyContainsAnother(final JunctionAssembly first, final JunctionAssembly second)
    {
        // only checks the junction (non-ref) bases
        int firstIndexStart;
        int firstIndexEnd;

        if(first.junction().isForward())
        {
            firstIndexStart = first.junctionIndex();
            firstIndexEnd = first.baseLength() - 1;
        }
        else
        {
            firstIndexStart = 0;
            firstIndexEnd = first.junctionIndex();
        }

        int secondOffset = second.junctionIndex() - first.junctionIndex();

        for(int i = firstIndexStart; i <= firstIndexEnd; ++i)
        {
            int secondIndex = i + secondOffset;

            if(secondIndex < 0)
                continue;

            if(secondIndex >= second.baseLength())
                break;

            if(!basesMatch(
                    first.bases()[i], second.bases()[i + secondOffset],
                    first.baseQuals()[i], second.baseQuals()[i + secondOffset], LOW_BASE_QUAL_THRESHOLD))
            {
                return false;
            }
        }

        return true;
    }
}
