package com.hartwig.hmftools.common.lims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.LocalDate;
import java.util.Map;

import com.google.common.collect.Maps;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class LimsTest {

    private static final String SAMPLE = "CPCT02991111T";

    @Test
    public void canReadFromLimsJson() {
        final String arrivalDate = "2017-05-01";
        final String samplingDate = "2017-04-15";
        final String tumorPercentage = "40";
        final String labSopVersions = "PREP1V2-QC1V2-SEQ1V2";

        final LimsJsonData data = ImmutableLimsJsonData.builder().sampleId(SAMPLE)
                .samplingDateString(samplingDate)
                .arrivalDateString(arrivalDate).tumorPercentageString(tumorPercentage)
                .labSopVersions(labSopVersions)
                .build();

        final Lims lims = buildTestLimsWithJsonData(SAMPLE, data);

        assertEquals(1, lims.sampleCount());

        assertEquals(LimsTestUtil.toDate(arrivalDate), lims.arrivalDateForSample(SAMPLE));
        assertEquals(LimsTestUtil.toDate(samplingDate), lims.samplingDateForSample(SAMPLE));
        final Double tumorPerc = lims.tumorPercentageForSample(SAMPLE);
        assertNotNull(tumorPerc);
        assertEquals(0.4, tumorPerc, 1.0E-10);
        assertEquals(labSopVersions, lims.labProceduresForSample(SAMPLE));

        assertNull(lims.arrivalDateForSample("DoesNotExist"));
        assertNull(lims.samplingDateForSample("DoesNotExist"));
        assertNull(lims.tumorPercentageForSample("DoesNotExist"));
        assertEquals("N/A", lims.labProceduresForSample("DoesNotExist"));
    }

    @Test
    public void fallBackOnPreLIMSArrivalDateWorks() {
        final LocalDate date = LimsTestUtil.toDate("2017-10-03");

        final Lims lims = buildTestLimsWithPreLIMSArrivalDateForSample(SAMPLE, date);

        assertEquals(date, lims.arrivalDateForSample(SAMPLE));
        assertNull(lims.samplingDateForSample(SAMPLE));
    }

    @Test
    public void invalidDataYieldsNull() {
        final LimsJsonData data = ImmutableLimsJsonData.builder().sampleId(SAMPLE)
                .samplingDateString("IsNotADate")
                .arrivalDateString("IsNotADate").tumorPercentageString("IsNotANumber")
                .labSopVersions("anything")
                .build();

        final Lims lims = buildTestLimsWithJsonData(SAMPLE, data);

        assertNull(lims.arrivalDateForSample(SAMPLE));
        assertNull(lims.samplingDateForSample(SAMPLE));
        assertNull(lims.tumorPercentageForSample(SAMPLE));
        assertEquals("N/A", lims.labProceduresForSample(SAMPLE));
    }

    @NotNull
    private static Lims buildTestLimsWithJsonData(@NotNull final String sample, @NotNull final LimsJsonData data) {
        final Map<String, LimsJsonData> dataPerSample = Maps.newHashMap();
        dataPerSample.put(sample, data);
        final Map<String, LocalDate> preLIMSArrivalDates = Maps.newHashMap();

        return new Lims(dataPerSample, preLIMSArrivalDates);
    }

    @NotNull
    private static Lims buildTestLimsWithPreLIMSArrivalDateForSample(@NotNull final String sample, @NotNull final LocalDate date) {
        final Map<String, LimsJsonData> dataPerSample = Maps.newHashMap();
        final Map<String, LocalDate> preLIMSArrivalDates = Maps.newHashMap();
        preLIMSArrivalDates.put(sample, date);

        return new Lims(dataPerSample, preLIMSArrivalDates);
    }
}