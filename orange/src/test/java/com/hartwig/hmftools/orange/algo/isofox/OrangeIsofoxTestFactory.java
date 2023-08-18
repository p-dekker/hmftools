package com.hartwig.hmftools.orange.algo.isofox;

import com.hartwig.hmftools.common.isofox.IsofoxTestFactory;
import com.hartwig.hmftools.datamodel.isofox.ImmutableIsofoxRnaStatistics;
import com.hartwig.hmftools.datamodel.isofox.ImmutableGeneExpression;
import com.hartwig.hmftools.datamodel.isofox.ImmutableNovelSpliceJunction;
import com.hartwig.hmftools.datamodel.isofox.ImmutableRnaFusion;
import com.hartwig.hmftools.orange.conversion.IsofoxConversion;

import org.jetbrains.annotations.NotNull;

public class OrangeIsofoxTestFactory
{

    private OrangeIsofoxTestFactory()
    {
    }

    @NotNull
    public static ImmutableIsofoxRnaStatistics.Builder rnaStatisticsBuilder()
    {
        var common = IsofoxTestFactory.rnaStatisticsBuilder().build();
        var converted = IsofoxConversion.convert(common);
        return ImmutableIsofoxRnaStatistics.builder().from(converted);
    }

    @NotNull
    public static ImmutableGeneExpression.Builder geneExpressionBuilder()
    {
        var common = IsofoxTestFactory.geneExpressionBuilder().build();
        var converted = IsofoxConversion.convert(common);
        return ImmutableGeneExpression.builder().from(converted);
    }

    @NotNull
    public static ImmutableRnaFusion.Builder rnaFusionBuilder()
    {
        var common = IsofoxTestFactory.rnaFusionBuilder().build();
        var converted = IsofoxConversion.convert(common);
        return ImmutableRnaFusion.builder().from(converted);
    }

    @NotNull
    public static ImmutableNovelSpliceJunction.Builder novelSpliceJunctionBuilder()
    {
        var common = IsofoxTestFactory.novelSpliceJunctionBuilder().build();
        var converted = IsofoxConversion.convert(common);
        return ImmutableNovelSpliceJunction.builder().from(converted);
    }
}
