package com.hartwig.hmftools.common.variant.structural.linx;

import static java.util.stream.Collectors.toList;

import static com.hartwig.hmftools.common.variant.structural.linx.LinxCluster.DELIMITER;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;
import com.hartwig.hmftools.common.fusion.Transcript;

import org.immutables.value.Value;
import org.jetbrains.annotations.NotNull;

@Value.Immutable
public abstract class LinxFusion
{
    public abstract int fivePrimeBreakendId();
    public abstract int threePrimeBreakendId();
    public abstract String name();
    public abstract boolean reported();
    public abstract String reportedType();
    public abstract boolean phased();
    public abstract int chainLength();
    public abstract int chainLinks();
    public abstract boolean chainTerminated();
    public abstract String domainsKept();
    public abstract String domainsLost();
    public abstract int skippedExonsUp();
    public abstract int skippedExonsDown();
    public abstract int fusedExonUp();
    public abstract int fusedExonDown();

    // for patient report
    public abstract String geneStart();
    public abstract String geneContextStart();
    public abstract String geneTranscriptStart();
    public abstract String geneEnd();
    public abstract String geneContextEnd();
    public abstract String geneTranscriptEnd();
    public abstract Double junctionCopyNumber();

    private static final String FILE_EXTENSION = ".linx.fusion.tsv";

    @NotNull
    public static String generateFilename(@NotNull final String basePath, @NotNull final String sample)
    {
        return basePath + File.separator + sample + FILE_EXTENSION;
    }

    @NotNull
    public static List<LinxFusion> read(final String filePath) throws IOException
    {
        return fromLines(Files.readAllLines(new File(filePath).toPath()));
    }

    public static void write(@NotNull final String filename, @NotNull List<LinxFusion> fusions) throws IOException
    {
        Files.write(new File(filename).toPath(), toLines(fusions));
    }

    @NotNull
    private static List<String> toLines(@NotNull final List<LinxFusion> fusions)
    {
        final List<String> lines = Lists.newArrayList();
        lines.add(header());
        fusions.stream().map(x -> toString(x)).forEach(lines::add);
        return lines;
    }

    @NotNull
    private static List<LinxFusion> fromLines(@NotNull List<String> lines)
    {
        return lines.stream().filter(x -> !x.startsWith("FivePrimeBreakendId")).map(LinxFusion::fromString).collect(toList());
    }

    @NotNull
    private static String header()
    {
        return new StringJoiner(DELIMITER)
                .add("FivePrimeBreakendId")
                .add("ThreePrimeBreakendId")
                .add("Name")
                .add("Reported")
                .add("ReportedType")
                .add("Phased")
                .add("ChainLength")
                .add("ChainLinks")
                .add("ChainTerminated")
                .add("DomainsKept")
                .add("DomainsLost")
                .add("SkippedExonsUp")
                .add("SkippedExonsDown")
                .add("FusedExonUp")
                .add("FusedExonDown")
                .add("GeneStart")
                .add("GeneContextStart")
                .add("TranscriptStart")
                .add("GeneEnd")
                .add("GeneContextEnd")
                .add("TranscriptEnd")
                .add("JunctionCopyNumber")
                .toString();
    }

    @NotNull
    private static String toString(@NotNull final LinxFusion fusion)
    {
        return new StringJoiner(DELIMITER)
                .add(String.valueOf(fusion.fivePrimeBreakendId()))
                .add(String.valueOf(fusion.threePrimeBreakendId()))
                .add(String.valueOf(fusion.name()))
                .add(String.valueOf(fusion.reported()))
                .add(String.valueOf(fusion.reportedType()))
                .add(String.valueOf(fusion.phased()))
                .add(String.valueOf(fusion.chainLength()))
                .add(String.valueOf(fusion.chainLinks()))
                .add(String.valueOf(fusion.chainTerminated()))
                .add(String.valueOf(fusion.domainsKept()))
                .add(String.valueOf(fusion.domainsLost()))
                .add(String.valueOf(fusion.skippedExonsUp()))
                .add(String.valueOf(fusion.skippedExonsDown()))
                .add(String.valueOf(fusion.fusedExonUp()))
                .add(String.valueOf(fusion.fusedExonDown()))
                .add(String.valueOf(fusion.geneStart()))
                .add(String.valueOf(fusion.geneContextStart()))
                .add(String.valueOf(fusion.geneTranscriptStart()))
                .add(String.valueOf(fusion.geneEnd()))
                .add(String.valueOf(fusion.geneContextEnd()))
                .add(String.valueOf(fusion.geneTranscriptEnd()))
                .add(String.valueOf(fusion.junctionCopyNumber()))
                .toString();
    }

    @NotNull
    private static LinxFusion fromString(@NotNull final String fusion)
    {
        String[] values = fusion.split(DELIMITER, -1);
        int index = 0;

        return ImmutableLinxFusion.builder()
                .fivePrimeBreakendId(Integer.parseInt(values[index++]))
                .threePrimeBreakendId(Integer.parseInt(values[index++]))
                .name(values[index++])
                .reported(Boolean.parseBoolean(values[index++]))
                .reportedType(values[index++])
                .phased(Boolean.parseBoolean(values[index++]))
                .chainLength(Integer.parseInt(values[index++]))
                .chainLinks(Integer.parseInt(values[index++]))
                .chainTerminated(Boolean.parseBoolean(values[index++]))
                .domainsKept(values[index++])
                .domainsLost(values[index++])
                .skippedExonsUp(Integer.parseInt(values[index++]))
                .skippedExonsDown(Integer.parseInt(values[index++]))
                .fusedExonUp(Integer.parseInt(values[index++]))
                .fusedExonDown(Integer.parseInt(values[index++]))
                .geneStart(values[index++])
                .geneContextStart(values[index++])
                .geneTranscriptStart(values[index++])
                .geneEnd(values[index++])
                .geneContextEnd(values[index++])
                .geneTranscriptEnd(values[index++])
                .junctionCopyNumber(Double.parseDouble(values[index++]))
                .build();
     }

    public static String context(@NotNull Transcript transcript, int fusedExon) {
        switch (transcript.regionType()) {
            case UPSTREAM:
                return "Promoter Region";
            case IG:
                return "IG";
            case EXONIC:
            case INTRONIC:
                return String.format("Exon %d", fusedExon);
        }

        return String.format("ERROR: %s", transcript.regionType());
    }

    public static double fusionJcn(double downstreamJcn, double upstreamJcn)
    {
        return (upstreamJcn + downstreamJcn) * 0.5;
    }
}
