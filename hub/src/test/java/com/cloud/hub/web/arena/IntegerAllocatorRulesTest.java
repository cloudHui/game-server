package com.cloud.hub.web.arena;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntegerAllocatorRulesTest {

    @Test
    public void balancesRemainingValuesWhenOnlyTotalAverageIsGiven() {
        IntegerAllocator.Result result = IntegerAllocator.calculate(
                Arrays.asList(10, 20), 10.0, null);

        assertTrue(result.isSuccess());
        assertArrayEquals(new int[]{10, 20, 8, 8, 7, 7}, result.getValues());
        assertEquals(60, result.getTotalTargetSum());
        assertEquals(60, result.getTotalSum());
    }

    @Test
    public void choosesFirstLegalWindowForThreeValueAverage() {
        IntegerAllocator.Result result = IntegerAllocator.calculate(
                Arrays.asList(20, 20), 10.0, 10.0);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getSubStartIndex());
        assertArrayEquals(new int[]{20, 20, 5, 5, 5, 5}, result.getValues());
        assertEquals(30, result.getSubSum());
    }

    @Test
    public void reportsImpossibleConstraintsInsteadOfReturningPartialValues() {
        IntegerAllocator.Result result = IntegerAllocator.calculate(
                Arrays.asList(100), 10.0, 10.0);

        assertFalse(result.isSuccess());
        assertEquals("已知值和均值条件无法同时满足", result.getErrorMessage());
    }

    @Test
    public void doesNotIgnoreThreeValueAverageWhenNoWindowMatches() {
        IntegerAllocator.Result result = IntegerAllocator.calculate(
                Arrays.asList(1, 2, 3, 4, 5), 3.0, 0.0);

        assertFalse(result.isSuccess());
        assertEquals("已知值和均值条件无法同时满足", result.getErrorMessage());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSixDigitKnownValue() {
        IntegerAllocator.calculate(Arrays.asList(100000), 10.0, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeKnownValue() {
        IntegerAllocator.calculate(Arrays.asList(-1), 10.0, null);
    }

    @Test
    public void acceptsKnownValuesInSixSlotsWhileKeepingOneSlotForAllocation() {
        IntegerAllocator.Result result = IntegerAllocator.calculate(
                Arrays.asList(10, null, 20, null, null, null), 10.0, null);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getKnownCount());
        assertArrayEquals(new int[]{10, 8, 20, 8, 7, 7}, result.getValues());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSixKnownValues() {
        IntegerAllocator.calculate(Arrays.asList(1, 2, 3, 4, 5, 6), 3.5, null);
    }
}
