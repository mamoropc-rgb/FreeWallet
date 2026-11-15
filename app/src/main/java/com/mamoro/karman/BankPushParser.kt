package com.mamoro.karman

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Extract only confirmed RUB purchases. Ancillary bank fields are never retained. */
data class BankPurchase(val merchant: String, val amountMinor: Long, val cardLast4: String? = null)

object BankPushParser {
    const val T_BANK_PACKAGE = "com.idamob.tinkoff.android"

    // The bank's original notification may contain several separate purchase lines.
    private val purchasePattern = Regex(
        """(?iu)покупка\s+(?:на\s+)?(\d[\d\s\u00a0\u202f]*(?:[,.]\d{1,2})?)\s*(?:₽|руб(?:\.|ля|лей)?)"""
    )
    private val whitespace = Regex("""\s+""")
    private val cardSuffix = Regex("""(?iu)(?:сч[её]т\s+карты|карты|карта)\s*\*([0-9]{4})(?![0-9])""")

    fun parse(lines: List<String>): List<BankPurchase> = lines.asSequence()
        .mapNotNull(::parseLine)
        .distinct()
        .take(15) // A bank's group notification should not create an unbounded batch.
        .toList()

    fun parseLine(text: String): BankPurchase? {
        if (text.length > 1000) return null
        val normalized = text.replace('\u00a0', ' ').replace('\u202f', ' ').trim()
        if (normalized.startsWith("возврат", ignoreCase = true) ||
            normalized.startsWith("отмена", ignoreCase = true)) return null
        val match = purchasePattern.find(normalized) ?: return null
        val merchant = normalized.substring(0, match.range.first)
            .trim().trimEnd(':', '—', '–', '-', ',', '.', ' ')
            .replace(whitespace, " ")
        // Do not persist card fragments or any additional credit field in merchant names.
        val privateFragments = Regex("""(?iu)(?:карта|сч[её]т|\*\d{3,4}|к[еэ]шб[еэ]к)""")
        // No speculative merchant names; unknown formats are ignored, not booked.
        if (merchant.length !in 2..75 || merchant.contains("покупка", ignoreCase = true) ||
            privateFragments.containsMatchIn(merchant)) return null
        val amount = try {
            BigDecimal(match.groupValues[1].replace(" ", "").replace(',', '.'))
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2).longValueExact()
        } catch (_: Exception) { return null }
        if (amount !in 1..Money.MAX_MINOR) return null
        // Only exact four-digit card endings are used for routing; incomplete values are ignored.
        val suffix=cardSuffix.find(normalized.substring(match.range.last + 1))?.groupValues?.get(1)
        return BankPurchase(merchant, amount, suffix)
    }

    fun normalizeMerchant(name: String): String = name.trim()
        .replace(whitespace, " ").lowercase(Locale.ROOT)
}
