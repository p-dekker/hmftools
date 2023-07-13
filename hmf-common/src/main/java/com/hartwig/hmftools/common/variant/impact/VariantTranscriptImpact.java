package com.hartwig.hmftools.common.variant.impact;

import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import com.google.common.collect.Lists;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;

public class VariantTranscriptImpact
{
    public final String GeneId;
    public final String GeneName;
    public final String Transcript;
    public final String Effects;
    public final boolean SpliceRegion;
    public final String HgvsCoding;
    public final String HgvsProtein;

    public VariantTranscriptImpact(final String geneId, final String geneName, final String transcript, final String effects,
            final boolean spliceRegion, final String hgvsCoding, final String hgvsProtein)
    {
        GeneId = geneId;
        GeneName = geneName;
        Transcript = transcript;
        Effects = effects;
        SpliceRegion = spliceRegion;
        HgvsCoding = hgvsCoding;
        HgvsProtein = hgvsProtein;
    }

    // serialisation
    public static final String VAR_TRANS_IMPACT_ANNOTATION = "PAVE_TI";

    // the in the VCF, transcript impacts are separated by ',', the components by '|' and the effects by '&"
    public static final String VAR_TRANS_IMPACT_DELIM = ",";
    public static final String VAR_TRANS_IMPACT_ITEM_DELIM = "|";

    public static void writeHeader(final VCFHeader header)
    {
        StringJoiner fields = new StringJoiner("|");
        List<String> fieldItems = Lists.newArrayList("Gene", "GeneName", "Transcript", "Effects", "SpliceRegion", "HGVS.c", "HGVS.p");
        fieldItems.forEach(x -> fields.add(x));

        header.addMetaDataLine(new VCFInfoHeaderLine(
                VAR_TRANS_IMPACT_ANNOTATION, VCFHeaderLineCount.UNBOUNDED, VCFHeaderLineType.String,
                String.format("Transcript impact [%s]", fields.toString())));
    }

    public static void writeVcfData(final VariantContext context, final List<VariantTranscriptImpact> transImpacts)
    {
        StringJoiner sj = new StringJoiner(VAR_TRANS_IMPACT_DELIM);
        transImpacts.forEach(x -> sj.add(x.toVcfData()));
        context.getCommonInfo().putAttribute(VAR_TRANS_IMPACT_ANNOTATION, sj.toString(), true);
    }

    public static List<VariantTranscriptImpact> fromVariantContext(final VariantContext variant)
    {
        if(!variant.hasAttribute(VAR_TRANS_IMPACT_ANNOTATION))
            return Collections.EMPTY_LIST;

        // when we get the impacts string, the square brackets from the array are actually included in the string
        // here, we strip them
        String impactsRawString = variant.getAttributeAsString(VAR_TRANS_IMPACT_ANNOTATION, "[]");
        String[] impactsStr = impactsRawString.substring(1, impactsRawString.length() - 1).split(VAR_TRANS_IMPACT_DELIM, -1);

        List<VariantTranscriptImpact> transImpacts = Lists.newArrayList();

        for(String impactStr : impactsStr)
        {
            transImpacts.add(fromVcfData(impactStr));
        }

        return transImpacts;
    }

    public static VariantTranscriptImpact fromVcfData(final String data)
    {
        String[] items = data.split("\\" + VAR_TRANS_IMPACT_ITEM_DELIM, -1);
        return new VariantTranscriptImpact(
                items[0], items[1], items[2], items[3], Boolean.parseBoolean(items[4]), items[5], items[6]);
    }

    private String toVcfData()
    {
        StringJoiner sj = new StringJoiner(VAR_TRANS_IMPACT_ITEM_DELIM);
        sj.add(GeneId);
        sj.add(GeneName);
        sj.add(Transcript);
        sj.add(Effects);
        sj.add(String.valueOf(SpliceRegion));
        sj.add(HgvsCoding);
        sj.add(HgvsProtein);
        return sj.toString();
    }

    public String toString() { return toVcfData(); }
}
