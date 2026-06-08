package dev.davidfdev.stela.resilience

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceResilienceTest {

    @Test
    fun knownManufacturer_mapsToAutostartTarget() {
        assertEquals(
            "com.miui.securitycenter",
            DeviceResilience.autostartTarget("Xiaomi")?.packageName,
        )
    }

    @Test
    fun mappingIsCaseInsensitive() {
        assertEquals(
            DeviceResilience.autostartTarget("Xiaomi"),
            DeviceResilience.autostartTarget("xiaomi"),
        )
    }

    @Test
    fun realmeSharesOppoTarget() {
        assertEquals(
            "com.coloros.safecenter",
            DeviceResilience.autostartTarget("realme")?.packageName,
        )
    }

    @Test
    fun unknownManufacturer_hasNoTarget() {
        assertNull(DeviceResilience.autostartTarget("Google"))
        assertNull(DeviceResilience.autostartTarget("samsung"))
    }
}
