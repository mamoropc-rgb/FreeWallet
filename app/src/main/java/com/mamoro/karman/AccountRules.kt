package com.mamoro.karman

/** Identifiers from separate database tables must not collide inside one LazyColumn. */
object AccountRowKeys {
    fun account(id: Long): String = "account:$id"
    fun bond(id: Long): String = "bond:$id"
}

/** Conservative account management: changing financial fields is forbidden if there are links. */
object AccountRules {
    fun hasLinkedData(s: Snapshot, id: Long): Boolean =
        s.entries.any { it.fromAccount == id || it.toAccount == id } ||
        s.plans.any { it.accountId == id } ||
        s.templates.any { it.accountId == id } ||
        s.bonds.any { it.accountId == id }

    fun canDelete(s: Snapshot, id: Long): Boolean =
        s.accounts.size > 1 && s.accounts.any { it.id == id } && !hasLinkedData(s, id)
}
