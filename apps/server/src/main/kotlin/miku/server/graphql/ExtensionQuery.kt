package miku.server.graphql

import miku.domain.model.ExtensionData
import miku.extension.runner.ExtensionManager
import org.koin.java.KoinJavaComponent.inject

class ExtensionQuery : Query {
    private val extensionManager: ExtensionManager by inject(ExtensionManager::class.java)

    fun extensions(): List<ExtensionData> {
        return extensionManager.registry.getAllExtensions().map { ext ->
            ExtensionData(
                packageName = ext.packageName,
                name = ext.name,
                versionCode = ext.versionCode,
                versionName = ext.versionName,
                lang = ext.lang,
                isNsfw = ext.isNsfw,
                sourceCount = ext.sources.size,
            )
        }
    }
}
