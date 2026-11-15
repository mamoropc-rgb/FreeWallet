package com.mamoro.karman

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** Stable anonymized identity; raw bank text is never persisted. */
object BankImportRules {
    const val DUPLICATE_WINDOW_SECONDS = 600L
    fun sourcePrefix(bankPackage: String, accountId: Long, day: String,
                     purchase: BankPurchase): String {
        val payload = listOf(bankPackage, accountId.toString(), day,
            BankPushParser.normalizeMerchant(purchase.merchant), purchase.amountMinor.toString()).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "bank:v1:$digest:"
    }
    fun duplicate(existingTimestamps: List<Long>, atSeconds: Long): Boolean =
        existingTimestamps.any { old ->
            val delta = atSeconds - old
            delta in -DUPLICATE_WINDOW_SECONDS..DUPLICATE_WINDOW_SECONDS
        }
    fun notificationDay(atMillis: Long): String = Instant.ofEpochMilli(atMillis)
        .atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
