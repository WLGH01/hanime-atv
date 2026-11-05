package me.hanime.atv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import me.hanime.atv.data.HanimeClient
import me.hanime.atv.data.HanimeRepository
import me.hanime.atv.data.LocalStore

class HanimeApp : Application(), ImageLoaderFactory {

    lateinit var client: HanimeClient
        private set
    lateinit var repository: HanimeRepository
        private set
    lateinit var store: LocalStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        client = HanimeClient(this)
        store = LocalStore(this)
        repository = HanimeRepository(client, store)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(client.httpClient)
            .crossfade(220)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(220L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()

    companion object {
        lateinit var instance: HanimeApp
            private set
    }
}
