package com.mdview.data

import com.mdview.ui.theme.BuiltInSkins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SkinStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val directory: File get() = File(folder.root, "skins")

    /**
     * [dispatcher] has to come from the enclosing `runTest` where there is one --
     * a scheduler the test does not drive never advances, and mixing two of them
     * fails the test outright.
     */
    private fun store(
        dispatcher: TestDispatcher = StandardTestDispatcher(),
        lightId: String = BuiltInSkins.Paper.id,
        darkId: String = BuiltInSkins.Ink.id,
    ) = SkinStore(
        directory = directory,
        bootLightId = lightId,
        bootDarkId = darkId,
        dispatcher = dispatcher,
        scope = CoroutineScope(dispatcher),
    )

    private fun skinJson(id: String, name: String = id, accent: String = "#FF0000") =
        """{"id":"$id","name":"$name","base":"ink","colors":{"accent":"$accent"}}"""

    @Test
    fun `the built-ins are there before anything has been imported`() {
        val store = store()

        assertEquals(BuiltInSkins.all, store.catalog)
        assertTrue(store.imported.value.isEmpty())
    }

    @Test
    fun `a default install touches no files at all`() {
        // The synchronous constructor read is only affordable because it does nothing
        // in the common case. If this ever regresses it costs every cold start.
        store()

        assertFalse(directory.exists())
    }

    @Test
    fun `an imported skin is available synchronously to the next process`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).import(skinJson("mine"))

        // No advanceUntilIdle: the point is that the constructor has it already, because
        // MainActivity needs the active skin before onCreate.
        val restarted = SkinStore(
            directory = directory,
            bootLightId = BuiltInSkins.Paper.id,
            bootDarkId = "mine",
            dispatcher = dispatcher,
            scope = CoroutineScope(dispatcher),
        )

        assertEquals("mine", restarted.resolve("mine", dark = true).id)
    }

    @Test
    fun `a skin that is not active is not read until the catalogue loads`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).import(skinJson("later"))

        val restarted = store(dispatcher)
        assertTrue(restarted.imported.value.isEmpty())

        restarted.loadAll()
        assertEquals(listOf("later"), restarted.imported.value.map { it.id })
    }

    @Test
    fun `importing writes a file and publishes the skin`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)

        val skin = store.import(skinJson("mine", name = "Mine"))

        assertEquals("Mine", skin.name)
        assertTrue(File(directory, "mine.json").isFile)
        assertEquals(listOf("mine"), store.imported.value.map { it.id })
    }

    @Test
    fun `re-importing the same id replaces it rather than duplicating it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)

        store.import(skinJson("mine", accent = "#FF0000"))
        store.import(skinJson("mine", name = "Renamed", accent = "#00FF00"))

        assertEquals(1, store.imported.value.size)
        assertEquals("Renamed", store.imported.value.single().name)
    }

    @Test
    fun `a skin cannot shadow a built-in`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)

        val reason = runCatching { store.import(skinJson("midnight")) }
            .exceptionOrNull()
            .let { it as? InvalidSkinException }
            ?.reason

        assertEquals(InvalidSkinException.Reason.ReservedId, reason)
        assertTrue(store.imported.value.isEmpty())
    }

    @Test
    fun `the cap is enforced once it is reached`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)
        repeat(SkinStore.MAX_IMPORTED) { index -> store.import(skinJson("skin$index")) }

        val reason = runCatching { store.import(skinJson("onemore")) }
            .exceptionOrNull()
            .let { it as? InvalidSkinException }
            ?.reason

        assertEquals(InvalidSkinException.Reason.TooMany, reason)
        // Replacing one that already exists still works at the cap.
        assertNotNull(store.import(skinJson("skin0", name = "Replaced")))
    }

    @Test
    fun `deleting removes the file and the catalogue entry`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)
        store.import(skinJson("mine"))

        assertTrue(store.delete("mine"))
        assertFalse(File(directory, "mine.json").exists())
        assertTrue(store.imported.value.isEmpty())
    }

    @Test
    fun `deleting a built-in is refused and changes nothing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)

        assertFalse(store.delete(BuiltInSkins.Midnight.id))
        assertEquals(BuiltInSkins.all, store.catalog)
    }

    @Test
    fun `a corrupt file costs that skin rather than every skin`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        store(dispatcher).import(skinJson("good"))
        directory.mkdirs()
        File(directory, "broken.json").writeText("{ this is not json")

        val store = store(dispatcher)
        store.loadAll()

        assertEquals(listOf("good"), store.imported.value.map { it.id })
    }

    @Test
    fun `a file whose name disagrees with its id is ignored`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        directory.mkdirs()
        File(directory, "claimed.json").writeText(skinJson("actually-different"))

        val store = store(dispatcher)
        store.loadAll()

        assertTrue(store.imported.value.isEmpty())
    }

    @Test
    fun `an id nothing answers to falls back to the default for the mode`() {
        val store = store()

        assertEquals(BuiltInSkins.Ink, store.resolve("gone", dark = true))
        assertEquals(BuiltInSkins.Paper, store.resolve("gone", dark = false))
    }

    @Test
    fun `loading twice is idempotent`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = store(dispatcher)
        store.import(skinJson("mine"))

        store.loadAll()
        store.loadAll()

        assertEquals(1, store.imported.value.size)
    }
}
