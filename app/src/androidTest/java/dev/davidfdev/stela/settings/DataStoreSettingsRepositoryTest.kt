package dev.davidfdev.stela.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreSettingsRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = File(context.cacheDir, "settings_test_${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repository = DataStoreSettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun defaults_whenEmpty() = runBlocking {
        val settings = repository.settings.first()
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(false, settings.hideOnLockScreen)
    }

    @Test
    fun setThenRead_roundTrips() = runBlocking {
        repository.setThemeMode(ThemeMode.LIGHT)
        repository.setHideOnLockScreen(true)

        val settings = repository.settings.first()
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertTrue(settings.hideOnLockScreen)
    }
}
