package com.mamoro.karman

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The ONLY live exchange-rate source is Settings (tab "Ещё").
 * Settings keeps 1e6 units of foreign currency for 1 RUB.
 * Ledger entries keep their historical RUB equivalent and are never rewritten.
 */
object FxRates {
    private val trillion = BigDecimal("1000000000000")

    fun basePerNativeMicros(settings: Settings, currency: String): Long {
        if (currency == "RUB") return 1_000_000L
        val foreignPerRubMicros = when (currency) {
            "USD" -> settings.usdPerBaseMicros
            "EUR" -> settings.eurPerBaseMicros
            "BYN" -> settings.bynPerBaseMicros
            else -> throw IllegalArgumentException("Неизвестная валюта: $currency")
        }
        require(foreignPerRubMicros > 0L) { "Укажите курс валюты во вкладке «Ещё»" }
        return trillion.divide(BigDecimal.valueOf(foreignPerRubMicros), 0, RoundingMode.HALF_UP)
            .longValueExact().also { require(it in 1L..100_000_000_000L) { "Некорректный курс валюты во вкладке «Ещё»" } }
    }

    fun toBase(settings: Settings, currency: String, localMinor: Long): Long =
        Money.toBase(localMinor, basePerNativeMicros(settings, currency))

    fun fromBase(settings: Settings, currency: String, baseMinor: Long): Long =
        Money.nativeFromBase(baseMinor, basePerNativeMicros(settings, currency))
}
