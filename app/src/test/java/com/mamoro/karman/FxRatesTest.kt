package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test

class FxRatesTest {
    @Test fun settingsAreOnlyLiveFxSource() {
        val hundredRubPerUsd=Settings(usdPerBaseMicros=10000L)
        val fiftyRubPerUsd=Settings(usdPerBaseMicros=20000L)
        assertEquals(1_000_000L,FxRates.toBase(hundredRubPerUsd,"USD",100_00L))
        assertEquals(500_000L,FxRates.toBase(fiftyRubPerUsd,"USD",100_00L))
        assertEquals(1_000_000L,FxRates.toBase(fiftyRubPerUsd,"RUB",1_000_000L))
    }
}
