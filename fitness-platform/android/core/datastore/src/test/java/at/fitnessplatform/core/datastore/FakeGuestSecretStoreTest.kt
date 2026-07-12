package at.fitnessplatform.core.datastore

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeGuestSecretStoreTest {
    @Test
    fun `concurrent recovery callers receive one value`() = runTest {
        val store = FakeGuestSecretStore()

        val values = List(20) { async { store.recoverySecretOrCreate() } }.awaitAll()

        assertEquals(1, values.toSet().size)
    }

    @Test
    fun `token rotation and clear are deterministic`() = runTest {
        val store = FakeGuestSecretStore()
        store.saveToken("first")
        store.saveToken("second")

        assertEquals("second", store.tokenOrNull())
        store.clear()
        assertNull(store.tokenOrNull())
    }
}
