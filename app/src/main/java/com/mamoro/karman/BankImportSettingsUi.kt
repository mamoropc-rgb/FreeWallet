package com.mamoro.karman

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** The platform controls the permission. There is no hidden or automatic access. */
object BankImportAccess {
    fun isGranted(context: android.content.Context): Boolean {
        val component = ComponentName(context, BankNotificationListener::class.java)
        return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
    }
}

@Composable
fun BankImportSettingsCard(s: Snapshot, vm: KarmanViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BankImportAccess.isGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = BankImportAccess.isGranted(context)
    }
    val rubAccounts = remember(s.accounts) { s.accounts.filter { it.currency == "RUB" } }
    val selected = rubAccounts.firstOrNull { it.id == s.settings.bankImportAccountId }
    val linkedTBankAccounts = rubAccounts.filter { BankSubaccounts.isTBank(it.bankGroup) }
    KCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NotificationsActive, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("Автоматический учёт покупок", fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Switch(
                checked = s.settings.bankImportEnabled,
                onCheckedChange = { vm.pref("bankImportEnabled", if (it) "1" else "0") },
                enabled = granted && (selected != null || linkedTBankAccounts.isNotEmpty())
            )
        }
        Text("Источник: уведомления Т-Банка. Сумма и название магазина сохраняются локально. Другие уведомления игнорируются.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Text("Т-Банк: привязано счетов — ${linkedTBankAccounts.size}",fontWeight=FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium)
        Text("Покупки по известным четырём цифрам карты попадут в соответствующий счёт. " +
            "Если карт несколько и номер в уведомлении скрыт, покупка пропускается — без ошибочного списания.",
            color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        Text("Резервный счёт, если группа банка не настроена", fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium)
        if (rubAccounts.isEmpty()) {
            Text("Сначала создайте рублёвый счёт во вкладке «Счета».",
                color = MaterialTheme.colorScheme.error)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rubAccounts, key = { it.id }) { account ->
                    FilterChip(
                        selected = s.settings.bankImportAccountId == account.id,
                        onClick = { vm.pref("bankImportAccountId", account.id.toString()) },
                        label = { Text(account.name) }
                    )
                }
            }
        }
        Text(if (granted) "Доступ к уведомлениям разрешён" else "Доступ к уведомлениям не предоставлен",
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("Настроить доступ к уведомлениям") }
        Text("Включите доступ в Android. Для двух карт создайте два счёта, укажите «Т-Банк» " +
            "и последние четыре цифры каждой карты в редакторе счёта. Резервный выбор используется " +
            "только до настройки группы. Кешбэк никогда не импортируется.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall)
    }
}
