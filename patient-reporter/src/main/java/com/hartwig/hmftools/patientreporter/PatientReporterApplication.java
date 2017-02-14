package com.hartwig.hmftools.patientreporter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.xml.stream.XMLStreamException;

import com.hartwig.hmftools.common.ecrf.CpctEcrfModel;
import com.hartwig.hmftools.common.exception.HartwigException;
import com.hartwig.hmftools.patientreporter.copynumber.CopyNumberAnalyzer;
import com.hartwig.hmftools.patientreporter.report.PDFWriter;
import com.hartwig.hmftools.patientreporter.slicing.Slicer;
import com.hartwig.hmftools.patientreporter.slicing.SlicerFactory;
import com.hartwig.hmftools.patientreporter.variants.VariantAnalyzer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.sf.dynamicreports.report.exception.DRException;

public class PatientReporterApplication {

    private static final Logger LOGGER = LogManager.getLogger(PatientReporterApplication.class);

    private static final String CPCT_SLICING_BED_ARGS_DESC = "Complete path towards the CPCT slicing bed.";
    private static final String CPCT_SLICING_BED = "cpct_slicing_bed";

    private static final String HIGH_CONFIDENCE_BED_ARGS_DESC = "Complete path towards the high confidence bed.";
    private static final String HIGH_CONFIDENCE_BED = "high_confidence_bed";

    private static final String HMF_SLICING_BED_ARGS_DESC = "Complete path towards the HMF slicing bed.";
    private static final String HMF_SLICING_BED = "hmf_slicing_bed";

    private static final String CPCT_ECRF_ARGS_DESC = "Complete path towards the cpct ecrf xml database.";
    private static final String CPCT_ECRF = "cpct_ecrf";

    private static final String REPORT_LOGO_ARGS_DESC = "Complete path to the logo used in the PDF report.";
    private static final String REPORT_LOGO = "report_logo";

    private static final String REPORT_DIRECTORY_ARGS_DESC = "Complete path to where the PDF reports have to be saved.";
    private static final String REPORT_DIRECTORY = "report_dir";

    private static final String TMP_DIRECTORY_ARGS_DESC = "Complete path where, if provided, temporary output files will be written to.";
    private static final String TMP_DIRECTORY = "tmp_dir";

    private static final String RUN_DIRECTORY_ARGS_DESC = "Complete path towards a single rundir.";
    private static final String RUN_DIRECTORY = "run_dir";

    private static final String BATCH_MODE_ARGS_DESC = "If set, runs in batch mode (Caution!!! Korneel Only)";
    private static final String BATCH_MODE = "batch_mode";

    private static final String NOT_SEQUENCEABLE_ARGS_DESC = "If set, generates a non-sequenceable report.";
    private static final String NOT_SEQUENCEABLE = "not_sequenceable";

    private static final String NOT_SEQUENCEABLE_REASON_ARGS_DESC = "Either 'low_tumor_percentage' or 'low_dna_yield'";
    private static final String NOT_SEQUENCEABLE_REASON = "not_sequenceable_reason";

    private static final String NOT_SEQUENCEABLE_SAMPLE_ARGS_DESC = "In case of non-sequenceable reports, the name of the sample used.";
    private static final String NOT_SEQUENCEABLE_SAMPLE = "not_sequenceable_sample";

    public static void main(final String... args)
            throws ParseException, IOException, HartwigException, DRException, XMLStreamException {
        final Options options = createOptions();
        final CommandLine cmd = createCommandLine(options, args);

        final String cpctEcrf = cmd.getOptionValue(CPCT_ECRF);
        final String hmfSlicingBed = cmd.getOptionValue(HMF_SLICING_BED);
        final String reportLogo = cmd.getOptionValue(REPORT_LOGO);
        final String reportDirectory = cmd.getOptionValue(REPORT_DIRECTORY);

        if (validGeneralInput(cpctEcrf, hmfSlicingBed, reportLogo, reportDirectory)) {
            LOGGER.info("Running patient reporter");
            LOGGER.info(" Loading ECRF database...");
            final CpctEcrfModel cpctEcrfModel = CpctEcrfModel.loadFromXML(cpctEcrf);
            LOGGER.info("  Loaded data for " + cpctEcrfModel.patientCount() + " patients.");

            final Slicer hmfSlicingRegion = SlicerFactory.fromBedFile(hmfSlicingBed);
            final PDFWriter pdfWriter = new PDFWriter(reportDirectory, reportLogo, hmfSlicingRegion);

            if (cmd.hasOption(NOT_SEQUENCEABLE)) {
                final NotSequenceableReason notSequenceableReason = NotSequenceableReason.fromIdentifier(
                        cmd.getOptionValue(NOT_SEQUENCEABLE_REASON));
                final String notSequenceableSample = cmd.getOptionValue(NOT_SEQUENCEABLE_SAMPLE);
                if (notSequenceableReason != NotSequenceableReason.OTHER && notSequenceableSample != null) {
                    final String tumorType = PatientReporterHelper.extractTumorType(cpctEcrfModel,
                            notSequenceableSample);
                    pdfWriter.writeNonSequenceableReport(notSequenceableSample, tumorType, "0%",
                            notSequenceableReason);
                } else {
                    gracefulShutdown(options);
                }
            } else {
                final String runDirectory = cmd.getOptionValue(RUN_DIRECTORY);
                final String cpctSlicingBed = cmd.getOptionValue(CPCT_SLICING_BED);
                final String highConfidenceBed = cmd.getOptionValue(HIGH_CONFIDENCE_BED);
                final String tmpDirectory = cmd.getOptionValue(TMP_DIRECTORY);

                if (validPatientInput(runDirectory, cpctSlicingBed, highConfidenceBed, tmpDirectory)) {
                    final VariantAnalyzer variantAnalyzer = VariantAnalyzer.fromSlicingRegions(hmfSlicingRegion,
                            SlicerFactory.fromBedFile(highConfidenceBed), SlicerFactory.fromBedFile(cpctSlicingBed));
                    final CopyNumberAnalyzer copyNumberAnalyzer = CopyNumberAnalyzer.fromHmfSlicingRegion(
                            hmfSlicingRegion);
                    final boolean batchMode = cmd.hasOption(BATCH_MODE);
                    new PatientReporterAlgo(runDirectory, cpctEcrfModel, variantAnalyzer, copyNumberAnalyzer,
                            tmpDirectory, pdfWriter, batchMode).run();
                } else {
                    gracefulShutdown(options);
                }
            }
        } else {
            gracefulShutdown(options);
        }
    }

    private static void gracefulShutdown(@NotNull final Options options) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Patient-Reporter", options);
        System.exit(1);
    }

    @NotNull
    private static Options createOptions() {
        final Options options = new Options();

        options.addOption(CPCT_SLICING_BED, true, CPCT_SLICING_BED_ARGS_DESC);
        options.addOption(HIGH_CONFIDENCE_BED, true, HIGH_CONFIDENCE_BED_ARGS_DESC);
        options.addOption(HMF_SLICING_BED, true, HMF_SLICING_BED_ARGS_DESC);
        options.addOption(CPCT_ECRF, true, CPCT_ECRF_ARGS_DESC);
        options.addOption(REPORT_LOGO, true, REPORT_LOGO_ARGS_DESC);
        options.addOption(REPORT_DIRECTORY, true, REPORT_DIRECTORY_ARGS_DESC);
        options.addOption(TMP_DIRECTORY, true, TMP_DIRECTORY_ARGS_DESC);
        options.addOption(RUN_DIRECTORY, true, RUN_DIRECTORY_ARGS_DESC);
        options.addOption(BATCH_MODE, false, BATCH_MODE_ARGS_DESC);
        options.addOption(NOT_SEQUENCEABLE, false, NOT_SEQUENCEABLE_ARGS_DESC);
        options.addOption(NOT_SEQUENCEABLE_REASON, true, NOT_SEQUENCEABLE_REASON_ARGS_DESC);
        options.addOption(NOT_SEQUENCEABLE_SAMPLE, true, NOT_SEQUENCEABLE_SAMPLE_ARGS_DESC);

        return options;
    }

    @NotNull
    private static CommandLine createCommandLine(@NotNull final Options options, @NotNull final String... args)
            throws ParseException {
        final CommandLineParser parser = new DefaultParser();
        return parser.parse(options, args);
    }

    private static boolean validGeneralInput(@Nullable final String cpctEcrf, @Nullable final String hmfSlicingBed,
            @Nullable final String reportLogo, @Nullable final String reportDirectory) {
        if (cpctEcrf == null || !exists(cpctEcrf)) {
            LOGGER.warn(CPCT_ECRF + " has to be an existing file: " + cpctEcrf);
        } else if (hmfSlicingBed == null || !exists(hmfSlicingBed)) {
            LOGGER.warn(HMF_SLICING_BED + " has to be an existing file: " + hmfSlicingBed);
        } else if (reportLogo == null || !exists(reportLogo)) {
            LOGGER.warn(REPORT_LOGO + " has to be an existing file: " + reportLogo);
        } else if (reportDirectory == null || !exists(reportDirectory) || !isDirectory(reportDirectory)) {
            LOGGER.warn(REPORT_DIRECTORY + " has to be an existing directory: " + reportDirectory);
        } else {
            return true;
        }

        return false;
    }

    private static boolean validPatientInput(@Nullable final String runDirectory,
            @Nullable final String cpctSlicingBed, @Nullable final String highConfidenceBed,
            @Nullable final String tmpDirectory) {
        if (runDirectory == null || !exists(runDirectory) || !isDirectory(runDirectory)) {
            LOGGER.warn(RUN_DIRECTORY + " has to be an existing directory: " + runDirectory);
        } else if (cpctSlicingBed == null || !exists(cpctSlicingBed)) {
            LOGGER.warn(CPCT_SLICING_BED + " has to be an existing file: " + cpctSlicingBed);
        } else if (highConfidenceBed == null || !exists(highConfidenceBed)) {
            LOGGER.warn(HIGH_CONFIDENCE_BED + " has to be an existing file: " + highConfidenceBed);
        } else if (tmpDirectory != null && (!exists(tmpDirectory) || !isDirectory(tmpDirectory))) {
            LOGGER.warn(TMP_DIRECTORY + " has to be an existing directory: " + highConfidenceBed);
        } else {
            return true;
        }

        return false;
    }

    private static boolean exists(@NotNull final String path) {
        return Files.exists(new File(path).toPath());
    }

    private static boolean isDirectory(@NotNull final String path) {
        return Files.isDirectory(new File(path).toPath());
    }
}
