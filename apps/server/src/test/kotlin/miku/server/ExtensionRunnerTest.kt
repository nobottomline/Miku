package miku.server

import miku.extension.runner.ExtensionManager
import miku.extension.runner.ExtensionRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class ExtensionRunnerTest {

    @Test
    fun `extension registry should start empty`() {
        val registry = ExtensionRegistry()
        assertEquals(0, registry.getSourceCount())
        assertEquals(0, registry.getExtensionCount())
        assertTrue(registry.getAllSources().isEmpty())
        assertTrue(registry.getAvailableLanguages().isEmpty())
    }

    @Test
    fun `extension manager should initialize with empty extensions dir`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "miku-test-ext-${System.currentTimeMillis()}")
        try {
            val manager = ExtensionManager(tempDir)
            manager.initialize()
            assertEquals(0, manager.registry.getExtensionCount())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `source lookup for nonexistent id should return null`() {
        val registry = ExtensionRegistry()
        assertNull(registry.getSource(999L))
        assertNull(registry.getCatalogueSource(999L))
    }
}
