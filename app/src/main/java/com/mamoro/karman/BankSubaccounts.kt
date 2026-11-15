package com.mamoro.karman

/** A bank is only a UI grouping of existing independent ledger accounts. */
object BankSubaccounts {
    const val T_BANK = "Т-Банк"

    fun cleanBank(value: String): String {
        val short = value.trim().replace(Regex("\\s+"), " ")
        return if (short.lowercase() in setOf("т-банк", "тбанк", "тинькофф", "t-bank", "tbank")) T_BANK else short
    }

    fun isTBank(bank: String): Boolean {
        val name = cleanBank(bank).lowercase().replace('‐', '-').replace('‑', '-').replace('–', '-')
        return name in setOf("т-банк", "тбанк", "тинькофф", "t-bank", "tbank")
    }

    /** Total inside one bank/currency card. Never include a virtual parent as a real account. */
    fun totalNative(accounts: List<Account>, entries: List<Entry>): Long {
        require(accounts.map { it.currency }.distinct().size <= 1) {
            "Счета разных валют нельзя суммировать без конвертации"
        }
        return accounts.fold(0L) { sum, account ->
            Math.addExact(sum, LedgerMath.accountLocal(account, entries))
        }
    }

    fun verifyMetadata(accounts: List<Account>, bank: String, last4: String, editedId: Long? = null) {
        require(bank.length <= 60) { "Слишком длинное название банка" }
        require(last4.isEmpty() || last4.matches(Regex("[0-9]{4}"))) {
            "Нужны ровно последние 4 цифры карты"
        }
        require(last4.isEmpty() || bank.isNotBlank()) { "Сначала укажите банк" }
        require(last4.isEmpty() || accounts.none {
            it.id != editedId && it.cardLast4 == last4 &&
                cleanBank(it.bankGroup).equals(cleanBank(bank), ignoreCase = true)
        }) { "Эти четыре цифры уже указаны для другого счёта этого банка" }
    }

    /** Never guess which account spent money when two or more accounts share a bank. */
    fun routeTBankPurchase(accounts: List<Account>, purchase: BankPurchase, fallbackId: Long): Account? {
        val bankAccounts = accounts.filter { isTBank(it.bankGroup) && it.currency == "RUB" }
        if (bankAccounts.isNotEmpty()) {
            if (purchase.cardLast4 != null) {
                return bankAccounts.singleOrNull { it.cardLast4 == purchase.cardLast4 }
            }
            return bankAccounts.singleOrNull() // No four digits -> only safe for one matching bank account.
        }
        // Backward compatibility for users who have not configured grouped bank accounts.
        return accounts.singleOrNull { it.id == fallbackId && it.currency == "RUB" }
    }
}
