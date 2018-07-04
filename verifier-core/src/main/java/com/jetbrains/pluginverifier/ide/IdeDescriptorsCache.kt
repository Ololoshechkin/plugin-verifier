package com.jetbrains.pluginverifier.ide

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.pluginverifier.misc.closeLogged
import com.jetbrains.pluginverifier.repository.cache.ResourceCacheEntry
import com.jetbrains.pluginverifier.repository.cache.ResourceCacheEntryResult
import com.jetbrains.pluginverifier.repository.cache.createSizeLimitedResourceCache
import com.jetbrains.pluginverifier.repository.provider.ProvideResult
import com.jetbrains.pluginverifier.repository.provider.ResourceProvider
import java.io.Closeable

/**
 * Caches the [IdeDescriptor]s by [IdeVersion]s.
 *
 * The cache must be [closed] [close] on the application shutdown
 * to deallocate all the [IdeDescriptor]s.
 */
class IdeDescriptorsCache(cacheSize: Int,
                          private val ideFilesBank: IdeFilesBank) : Closeable {

  private val resourceCache = createSizeLimitedResourceCache(
      cacheSize,
      IdeDescriptorResourceProvider(ideFilesBank),
      { it.close() },
      "IdeDescriptorsCache"
  )

  /**
   * Atomically [selects] [selector] an IDE from a set of [available] [IdeFilesBank.getAvailableIdeVersions] IDEs
   * and registers an entry for the [IdeDescriptor].
   * The cache's state is not modified until this method returns.
   */
  fun getIdeDescriptor(selector: (Set<IdeVersion>) -> IdeVersion): Result.Found =
      getIdeDescriptors {
        listOf(selector(it))
      }.single()

  /**
   * Atomically [selects] [selector] several IDEs from a set of all [available] [IdeFilesBank.getAvailableIdeVersions] IDEs
   * and registers the [ResourceCacheEntry]s for the corresponding [IdeDescriptor]s.
   * The cache's state is not modified until this method returns.
   */
  private fun getIdeDescriptors(selector: (Set<IdeVersion>) -> List<IdeVersion>): List<Result.Found> {
    val result = arrayListOf<Result.Found>()
    try {
      /**
       * Lock the [ideFilesBank] to guarantee that the available IDEs will not be removed
       * until the corresponding [ResourceCacheEntry]s are registered for them.
       */
      ideFilesBank.lockAndAccess {
        val selectedVersions = selector(ideFilesBank.getAvailableIdeVersions())
        selectedVersions.mapNotNullTo(result) {
          getIdeDescriptorCacheEntry(it) as? Result.Found
        }
      }
    } catch (e: Throwable) {
      result.forEach { it.closeLogged() }
      throw e
    }
    return result
  }

  /**
   * Atomically creates an [IdeDescriptor] for IDE [ideVersion]
   * and registers a [ResourceCacheEntry] for it.
   * The cache's state is not modified until this method returns.
   */
  fun getIdeDescriptorCacheEntry(ideVersion: IdeVersion): Result {
    val resourceCacheEntryResult = resourceCache.getResourceCacheEntry(ideVersion)
    return with(resourceCacheEntryResult) {
      when (this) {
        is ResourceCacheEntryResult.Found -> Result.Found(resourceCacheEntry)
        is ResourceCacheEntryResult.Failed -> Result.Failed(message, error)
        is ResourceCacheEntryResult.NotFound -> Result.NotFound(message)
      }
    }
  }

  sealed class Result : Closeable {
    data class Found(private val resourceCacheEntry: ResourceCacheEntry<IdeDescriptor>) : Result() {

      val ideDescriptor: IdeDescriptor
        get() = resourceCacheEntry.resource

      override fun close() = resourceCacheEntry.close()
    }

    data class Failed(val message: String, val error: Throwable) : Result() {
      override fun close() = Unit
    }

    data class NotFound(val message: String) : Result() {
      override fun close() = Unit
    }
  }

  /**
   * Implementation of the [ResourceProvider] that provides the IDE files from the [ideFilesBank].
   */
  private class IdeDescriptorResourceProvider(private val ideFilesBank: IdeFilesBank) : ResourceProvider<IdeVersion, IdeDescriptor> {

    override fun provide(key: IdeVersion): ProvideResult<IdeDescriptor> {
      val result = ideFilesBank.getIdeFile(key)
      val ideLock = (result as? IdeFilesBank.Result.Found)?.ideFileLock
          ?: return ProvideResult.NotFound("IDE $key is not found in the $ideFilesBank")
      val ideDescriptor = try {
        IdeDescriptor.create(ideLock.file, null, ideLock)
      } catch (e: Exception) {
        return ProvideResult.Failed("Unable to open IDE $key", e)
      }
      return ProvideResult.Provided(ideDescriptor)
    }
  }

  override fun close() = resourceCache.close()
}