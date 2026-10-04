package com.multitasks.store

import android.app.Application
import com.multitasks.store.repository.WorkRepository
import com.multitasks.store.storage.ImageStorage

class MultiTasksApp: Application() {
    val repository by lazy { WorkRepository(this) }
    val images by lazy { ImageStorage(this) }
}
