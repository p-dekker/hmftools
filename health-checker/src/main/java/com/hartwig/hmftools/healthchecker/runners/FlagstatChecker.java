package com.hartwig.hmftools.healthchecker.runners;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.utils.io.reader.LineReader;
import com.hartwig.hmftools.healthchecker.result.ImmutableQCValue;
import com.hartwig.hmftools.healthchecker.result.QCValue;
import com.hartwig.hmftools.healthchecker.result.QCValueType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlagstatChecker implements HealthChecker {

    @NotNull
    private final String refFlagstat;
    @NotNull
    private final String tumFlagstat;

    public FlagstatChecker(@NotNull final String refFlagstat, @Nullable final String tumFlagstat) {
        this.refFlagstat = refFlagstat;
        this.tumFlagstat = tumFlagstat;
    }

    public static String divideTwoStrings(String string1, String string2) {
        return String.valueOf(Double.parseDouble(string1) / Double.parseDouble(string2));
    }

    public static String flagstatMappingProportion(String flagstatFile) throws IOException {
        // Example flagstat line: 323329219 + 0 in total (QC-passed reads + QC-failed reads)
        // List<String> lines = Files.readAllLines(new File(flagstatFile).toPath());
        List<String> total_line = LineReader.build().readLines(new File(flagstatFile).toPath(), x -> x.contains("in total"));
        assert total_line.size() == 1;
        String total = total_line.get(0).split(" ")[0];

        List<String> mapped_line  = LineReader.build().readLines(new File(flagstatFile).toPath(), x -> x.contains("mapped ("));
        assert mapped_line.size() == 1;
        String mapped = mapped_line.get(0).split(" ")[0];

        return divideTwoStrings(mapped, total);
    }

    @NotNull
    @Override
    public List<QCValue> run() throws IOException {

        String refProportion = flagstatMappingProportion(refFlagstat);
        String tumProportion = flagstatMappingProportion(tumFlagstat);

        return Lists.newArrayList(
            ImmutableQCValue.of(QCValueType.REF_PROPORTION_MAPPED, refProportion),
            ImmutableQCValue.of(QCValueType.TUM_PROPORTION_MAPPED, tumProportion)
        );
    }
}
