package com.hartwig.hmftools.cup.drivers;

public enum ViralInsertionType
{
    HPV,
    HBV,
    MERKEL,
    AAV,
    HERPES,
    OTHER;

    public static ViralInsertionType fromVirusName(final String virusName)
    {
        if(virusName.contains("papillo"))
            return HPV;

        if(virusName.contains("HBV") || virusName.contains("Hepatitis B"))
            return HBV;

        if(virusName.contains("Merkel"))
            return MERKEL;

        if(virusName.contains("adenovirus"))
            return AAV;

        if(virusName.contains("herpes"))
            return HERPES;

        return OTHER;
    }
}
