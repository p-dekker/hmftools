package com.hartwig.hmftools.common.genome.genepanel;

import static org.junit.Assert.assertEquals;

import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.hartwig.hmftools.common.genome.region.GenomeRegion;
import com.hartwig.hmftools.common.genome.region.HmfExonRegion;
import com.hartwig.hmftools.common.genome.region.HmfTranscriptRegion;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class HmfExonPanelBedTest {

    @Test
    public void testReverseTranscript() {
        final HmfTranscriptRegion transcript = HmfGenePanelSupplier.allGenesMap37().get("TP53");
        HmfExonRegion firstExon = transcript.exome().get(0);
        HmfExonRegion secondExon = transcript.exome().get(1);
        HmfExonRegion thirdExon = transcript.exome().get(2);
        HmfExonRegion finalCodingExon = transcript.exome().get(transcript.exome().size() - 2);

        List<? extends GenomeRegion> regions =
                HmfExonPanelBed.createRegions(false, Sets.newHashSet("TP53"), Lists.newArrayList(transcript, transcript));

        assertRegion(regions.get(0), transcript.codingStart(), firstExon.end() + 2);
        assertRegion(regions.get(1), secondExon.start() - 5, secondExon.start() - 5);
        assertRegion(regions.get(2), secondExon.start() - 2, secondExon.end() + 2);
        assertRegion(regions.get(3), thirdExon.start() - 5, thirdExon.start() - 5);

        assertRegion(regions.get(13), 7579307, 7579307);
        assertRegion(regions.get(18), finalCodingExon.start() - 2, transcript.codingEnd());
        assertRegion(regions.get(19), finalCodingExon.end() + 1, finalCodingExon.end() + 2);
    }

    private void assertRegion(@NotNull final GenomeRegion victim, long expectedStart, long expectedEnd) {
        assertEquals(expectedStart, victim.start());
        assertEquals(expectedEnd, victim.end());

    }

}
