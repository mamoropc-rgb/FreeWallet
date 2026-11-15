package com.mamoro.karman

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Label
import org.junit.Assert.*
import org.junit.Test

class WalletIconCatalogTest {
    @Test fun loadCoreMaterialVectorsNotFallbackLabels() {
        // Catch a change in generated Compose icon accessor names before releasing the APK.
        listOf("ShoppingCart", "Restaurant", "DirectionsCar", "AccountBalance", "Work")
            .forEach {key ->
                assertNotSame("Could not load Material vector for $key",
                    Icons.Default.Label, WalletIconCatalog.vector(key))
            }
    }
    @Test fun legacyNamesMapToNewCatalog() {
        assertSame(WalletIconCatalog.vector("ShoppingCart"),WalletIconCatalog.vector("shopping"))
        assertSame(WalletIconCatalog.vector("Restaurant"),WalletIconCatalog.vector("food"))
    }
}
