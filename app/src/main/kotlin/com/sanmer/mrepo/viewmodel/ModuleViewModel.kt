package com.sanmer.mrepo.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanmer.mrepo.app.Const
import com.sanmer.mrepo.model.json.ModuleUpdate
import com.sanmer.mrepo.model.json.ModuleUpdateItem
import com.sanmer.mrepo.model.module.LocalModule
import com.sanmer.mrepo.model.module.OnlineModule
import com.sanmer.mrepo.repository.LocalRepository
import com.sanmer.mrepo.repository.ModulesRepository
import com.sanmer.mrepo.repository.SuRepository
import com.sanmer.mrepo.repository.UserDataRepository
import com.sanmer.mrepo.service.DownloadService
import com.sanmer.mrepo.utils.expansion.toDateTime
import com.sanmer.mrepo.utils.expansion.update
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ModuleViewModel @Inject constructor(
    private val localRepository: LocalRepository,
    private val modulesRepository: ModulesRepository,
    private val userDataRepository: UserDataRepository,
    private val suRepository: SuRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val moduleId: String = checkNotNull(savedStateHandle["moduleId"])
    var online by mutableStateOf(OnlineModule())
        private set
    val versions = mutableListOf<ModuleUpdateItem>()

    var local by mutableStateOf(LocalModule())
        private set
    val installed get() = local.id != "unknown"
    val modulePath get() = "${Const.MODULE_PATH}/${moduleId}"

    val userData get() = userDataRepository.userData
    val suState get() = suRepository.state

    init {
        Timber.d("ModuleViewModel init")
        getModule(moduleId)
    }

    private fun getModule(moduleId: String) = viewModelScope.launch {
        Timber.d("getModule: $moduleId")

        runCatching {
            localRepository.local.find { it.id == moduleId }?.let { local = it }
            localRepository.online.first { it.id == moduleId }.apply { online = this }
        }.onSuccess {
            getUpdates(it)
        }.onFailure {
            Timber.e(it, "getModule")
        }
    }

    suspend fun getRepoByUrl(url: String) = localRepository.getRepoByUrl(url)

    private suspend fun getUpdates(module: OnlineModule) { // TODO: TODO: Waiting for version 2.0 of util
        val update: (ModuleUpdate) -> Unit = { update ->
            update.versions.forEach { item ->
                val versionCodes = versions.map { it.versionCode }
                if (item.versionCode !in versionCodes) {
                    val new = item.copy(repoUrl = update.repoUrl)
                    versions.update(new)
                }
            }
        }

        val result = module.repoUrls.map { url ->
            modulesRepository.getUpdate(url, module.id)
                .onSuccess {
                    return@map Result.success(it.copy(repoUrl = url))
                }.onFailure {
                    Timber.e(it, "getUpdates")
                }
        }

        if (result.all { it.isFailure }) return

        result.mapNotNull { it.getOrNull() }.let { list ->
            list.sortedByDescending { it.timestamp }
                .forEach(update)

            if (versions.isNotEmpty()) {
                versions.sortedByDescending { it.versionCode }
            }
        }
    }

    fun downloader(
        context: Context,
        item: ModuleUpdateItem,
        install: Boolean = false
    ) {
        val path = userDataRepository.value.downloadPath.resolve(
            "${online.name}_${item.version}_${item.versionCode}.zip"
                .replace("[\\s+|/]".toRegex(), "_")
        )

        DownloadService.start(
            context = context,
            name = online.name,
            path = path.absolutePath,
            url = item.zipUrl,
            install = install
        )
    }

    fun getLastModified(): String? = try {
        val moduleProp = suRepository.fs
            .getFile("$modulePath/module.prop")

        if (moduleProp.exists()) {
            moduleProp.lastModified().toDateTime()
        } else {
            null
        }

    } catch (e: Exception) {
        null
    }
}