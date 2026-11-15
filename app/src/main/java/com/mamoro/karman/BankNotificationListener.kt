package com.mamoro.karman

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableSharedFlow

/** Lightweight in-process update signal; no banking payload is sent through this flow. */
object BankImportUpdates {
    val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

/** Access must be granted explicitly in Android settings; only one allowlisted bank is parsed. */
class BankNotificationListener : NotificationListenerService() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != BankPushParser.T_BANK_PACKAGE) return
        val extras = sbn.notification?.extras ?: return
        // Grouped bank notifications can expose transactions via multiple fields.
        // Parse all lines but persist only merchant and purchase amount, never raw messages.
        val lines = buildList {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { item ->
                addAll(item.toString().lines())
            }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { addAll(it.lines()) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { addAll(it.lines()) }
        }.filter { it.isNotBlank() }.distinct()
        val purchases = BankPushParser.parse(lines)
        if (purchases.isEmpty()) return
        // Do not read notification history or notification content from any other package.
        val happenedAt = sbn.notification.`when`.takeIf { it > 0 } ?: sbn.postTime
        val origin = sbn.packageName
        try {
            worker.execute {
                runCatching {
                    LedgerDb(applicationContext).use { db ->
                        if (db.importBankPurchases(origin, purchases, happenedAt) > 0) {
                            BankImportUpdates.updates.tryEmit(Unit)
                        }
                    }
                }
                // Never log raw notification text, amounts, or banking details.
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Listener stopped while the notification was being delivered.
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
