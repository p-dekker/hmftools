package com.hartwig.hmftools.ckb.indication;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.hartwig.hmftools.common.utils.json.JsonFunctions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class IndicationFactory {

    private static final Logger LOGGER = LogManager.getLogger(IndicationFactory.class);

    private IndicationFactory() {

    }

    @NotNull
    public static List<Indication> readingIndication(@NotNull String indicationDir) throws IOException {
        LOGGER.info("Start reading indications");

        List<Indication> indications = Lists.newArrayList();
        File[] filesIndications = new File(indicationDir).listFiles();

        if (filesIndications != null) {
            LOGGER.info("The total files in the genes dir is {}", filesIndications.length);

            for (File indication : filesIndications) {
                JsonParser parser = new JsonParser();
                JsonReader reader = new JsonReader(new FileReader(indication));
                reader.setLenient(true);

                while (reader.peek() != JsonToken.END_DOCUMENT) {
                    JsonObject indicationEntryObject = parser.parse(reader).getAsJsonObject();

                    indications.add(ImmutableIndication.builder()
                            .id(JsonFunctions.string(indicationEntryObject, "id"))
                            .name(JsonFunctions.string(indicationEntryObject, "name"))
                            .source(JsonFunctions.string(indicationEntryObject, "source"))
                            .definition(JsonFunctions.nullableString(indicationEntryObject, "definition"))
                            .currentPreferredTerm(JsonFunctions.nullableString(indicationEntryObject, "currentPreferredTerm"))
                            .lastUpdateDateFromDO(JsonFunctions.nullableString(indicationEntryObject, "lastUpdateDateFromDO"))
                            .altIds(JsonFunctions.stringList(indicationEntryObject, "altIds"))
                            .termId(JsonFunctions.string(indicationEntryObject, "termId"))
                            .evidence(extractEvidence(indicationEntryObject.getAsJsonArray("evidence")))
                            .clinicalTrial(extractClinicalTrials(indicationEntryObject.getAsJsonArray("clinicalTrials")))
                            .build());
                }
            }
        }
        return indications;
    }

    @NotNull
    public static List<IndicationEvidence> extractEvidence(@NotNull JsonArray jsonArray) {
        List<IndicationEvidence> evidences = Lists.newArrayList();
        for (JsonElement evidence : jsonArray) {
            JsonObject evidenceJsonObject = evidence.getAsJsonObject();

            evidences.add(ImmutableIndicationEvidence.builder()
                    .id(JsonFunctions.string(evidenceJsonObject, "id"))
                    .approvalStatus(JsonFunctions.string(evidenceJsonObject, "approvalStatus"))
                    .evidenceType(JsonFunctions.string(evidenceJsonObject, "evidenceType"))
                    .efficacyEvidence(JsonFunctions.string(evidenceJsonObject, "efficacyEvidence"))
                    .molecularProfile(extractMolecularProfile(evidenceJsonObject.getAsJsonObject("molecularProfile")))
                    .therapy(extractTherapy(evidenceJsonObject.getAsJsonObject("therapy")))
                    .indication(extractIndication(evidenceJsonObject.getAsJsonObject("indication")))
                    .responseType(JsonFunctions.string(evidenceJsonObject, "responseType"))
                    .reference(extractReference(evidenceJsonObject.getAsJsonArray("references")))
                    .ampCapAscoEvidenceLevel(JsonFunctions.string(evidenceJsonObject, "ampCapAscoEvidenceLevel"))
                    .ampCapAscoInferredTier(JsonFunctions.string(evidenceJsonObject, "ampCapAscoInferredTier"))
                    .build());
        }
        return evidences;
    }

    @NotNull
    public static IndicationMolecularProfile extractMolecularProfile(@NotNull JsonObject jsonObject) {
        return ImmutableIndicationMolecularProfile.builder()
                .id(JsonFunctions.string(jsonObject, "id"))
                .profileName(JsonFunctions.string(jsonObject, "profileName"))
                .build();
    }

    @NotNull
    public static IndicationTherapy extractTherapy(@NotNull JsonObject jsonObject) {
        return ImmutableIndicationTherapy.builder()
                .id(JsonFunctions.string(jsonObject, "id"))
                .therapyName(JsonFunctions.string(jsonObject, "therapyName"))
                .synonyms(JsonFunctions.nullableString(jsonObject, "synonyms"))
                .build();
    }

    @NotNull
    public static IndicationIndication extractIndication(@NotNull JsonObject jsonObject) {
        return ImmutableIndicationIndication.builder()
                .id(JsonFunctions.string(jsonObject, "id"))
                .name(JsonFunctions.string(jsonObject, "name"))
                .source(JsonFunctions.string(jsonObject, "source"))
                .build();
    }

    @NotNull
    public static List<IndicationReference> extractReference(@NotNull JsonArray jsonArray) {
        List<IndicationReference> references = Lists.newArrayList();
        for (JsonElement reference : jsonArray) {
            JsonObject referenceJsonObject = reference.getAsJsonObject();
            references.add(ImmutableIndicationReference.builder()
                    .id(JsonFunctions.string(referenceJsonObject, "id"))
                    .pubMedId(JsonFunctions.nullableString(referenceJsonObject, "pubMedId"))
                    .title(JsonFunctions.nullableString(referenceJsonObject, "title"))
                    .url(JsonFunctions.nullableString(referenceJsonObject, "url"))
                    .build());
        }
        return references;
    }

    @NotNull
    public static List<IndicationClinicalTrial> extractClinicalTrials(@NotNull JsonArray jsonArray) {
        List<IndicationClinicalTrial> clinicalTrials = Lists.newArrayList();
        for (JsonElement clinicalTrial : jsonArray) {
            JsonObject clinicalTrialJsonObject = clinicalTrial.getAsJsonObject();
            clinicalTrials.add(ImmutableIndicationClinicalTrial.builder()
                    .nctId(JsonFunctions.string(clinicalTrialJsonObject, "nctId"))
                    .title(JsonFunctions.string(clinicalTrialJsonObject, "title"))
                    .phase(JsonFunctions.string(clinicalTrialJsonObject, "phase"))
                    .recruitment(JsonFunctions.string(clinicalTrialJsonObject, "recruitment"))
                    .therapy(extractTherapyList(clinicalTrialJsonObject.getAsJsonArray("therapies")))
                    .build());
        }
        return clinicalTrials;
    }

    @NotNull
    public static List<IndicationTherapy> extractTherapyList(@NotNull JsonArray jsonArray) {
        List<IndicationTherapy> indications = Lists.newArrayList();
        for (JsonElement indication : jsonArray) {
            JsonObject indicationJsonObject = indication.getAsJsonObject();
            indications.add(ImmutableIndicationTherapy.builder()
                    .id(JsonFunctions.string(indicationJsonObject, "id"))
                    .therapyName(JsonFunctions.string(indicationJsonObject, "therapyName"))
                    .synonyms(JsonFunctions.nullableString(indicationJsonObject, "synonyms"))
                    .build());
        }
        return indications;

    }
}
