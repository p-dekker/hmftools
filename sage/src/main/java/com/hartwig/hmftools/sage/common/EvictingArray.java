package com.hartwig.hmftools.sage.common;

import static com.google.common.math.IntMath.ceilingPowerOfTwo;
import static com.hartwig.hmftools.sage.SageCommon.SG_LOGGER;

import java.util.function.Consumer;
import java.util.function.Function;

import com.hartwig.hmftools.sage.candidate.RefContext;

public class EvictingArray
{
    private final RefContext[] mElements;
    private final Consumer<RefContext> mEvictionHandler;
    private int mMinPosition = 0;
    private int mMinPositionIndex = 0;

    private final int mCapacity;

    public static final int MIN_CAPACITY = 256;

    public EvictingArray(int minCapacity, Consumer<RefContext> evictionHandler)
    {
        mEvictionHandler = evictionHandler;
        mCapacity = minCapacity; // calculateSize(minCapacity);
        mElements = new RefContext[mCapacity];
    }

    public RefContext computeIfAbsent(int position, final Function<Integer,RefContext> supplier)
    {
        if(mMinPosition == 0)
        {
            mMinPosition = position - mCapacity + 1;
        }

        int distanceFromMinPosition = position - mMinPosition;
        if(distanceFromMinPosition < 0)
        {
            SG_LOGGER.warn("ignoring read with position({}) before prior position({})", position, mMinPosition);
            return null;
        }

        if(distanceFromMinPosition >= mCapacity)
        {
            //int prevMinPosition = mMinPosition;

            flush(position - mMinPosition - mCapacity + 1);

            //SG_LOGGER.trace("flushing rolling array: minPos({} -> {}) capacity({}) read position({})",
            //        prevMinPosition, mMinPosition, mCapacity, position);

            distanceFromMinPosition = position - mMinPosition;
        }

        int index = (mMinPositionIndex + distanceFromMinPosition) & (mElements.length - 1);
        RefContext element = mElements[index];
        if(element == null)
        {
            element = supplier.apply(position);
            mElements[index] = element;
        }

        return element;
    }

    public int capacity()
    {
        return mCapacity;
    }

    int minPosition()
    {
        return mMinPosition;
    }

    public void evictAll()
    {
        flush(mCapacity);
    }

    private void flush(int count)
    {
        for(int i = 0; i < count; i++)
        {
            RefContext element = mElements[mMinPositionIndex];
            if(element != null)
            {
                mEvictionHandler.accept(element);
                mElements[mMinPositionIndex] = null;
            }
            mMinPosition++;
            mMinPositionIndex = (mMinPositionIndex + 1) & (mElements.length - 1);
        }
    }

    public static int calculateSize(int numElements)
    {
        return ceilingPowerOfTwo(numElements);
    }
}
