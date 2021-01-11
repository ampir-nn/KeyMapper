package io.github.sds100.keymapper.data.viewmodel

import android.content.pm.ApplicationInfo
import androidx.lifecycle.*
import io.github.sds100.keymapper.data.model.AppListItemModel
import io.github.sds100.keymapper.data.repository.SystemRepository
import io.github.sds100.keymapper.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Created by sds100 on 27/01/2020.
 */
class AppListViewModel internal constructor(
    private val repository: SystemRepository
) : ViewModel() {

    private val launchableAppModelList = liveData {
        emit(Loading())

        emit(repository.getLaunchableAppList().createModels().getState())
    }

    private val allAppModelList = liveData {
        emit(Loading())

        emit(repository.getAllAppList().createModels().getState())
    }

    val searchQuery: MutableLiveData<String> = MutableLiveData("")

    val showHiddenApps = MutableLiveData(false)

    val haveHiddenApps = launchableAppModelList.map {
        if (it is Data) {
            it.data.isNotEmpty()
        } else {
            false
        }
    }

    val filteredAppModelList = MediatorLiveData<State<List<AppListItemModel>>>().apply {
        value = Loading()

        fun filter(modelList: State<List<AppListItemModel>>, query: String) {
            value = Loading()

            when (modelList) {
                is Data -> {
                    val filteredList = modelList.data.filter { model ->
                        model.appName.toLowerCase(Locale.getDefault()).contains(query)
                    }

                    value = filteredList.getState()
                }

                is Empty -> Empty()
                is Loading -> Loading()
            }
        }

        fun showAllApps() {
            value = allAppModelList.value

            searchQuery.value?.let { query ->
                filter(allAppModelList.value ?: Empty(), query)
            }
        }

        fun showLaunchableApps() {
            value = launchableAppModelList.value

            searchQuery.value?.let { query ->
                filter(launchableAppModelList.value ?: Empty(), query)
            }
        }

        addSource(searchQuery) { query ->
            if (showHiddenApps.value == true) {
                filter(allAppModelList.value ?: Empty(), query)
            } else {
                filter(launchableAppModelList.value ?: Empty(), query)
            }
        }

        addSource(allAppModelList) {
            if (showHiddenApps.value == true) {
                showAllApps()
            }
        }

        addSource(launchableAppModelList) {
            if (showHiddenApps.value == false) {
                showLaunchableApps()
            }
        }

        addSource(showHiddenApps) {
            if (it == true) {
                showAllApps()
            } else {
                showLaunchableApps()
            }
        }
    }

    private suspend fun List<ApplicationInfo>.createModels(): List<AppListItemModel> =
        withContext(viewModelScope.coroutineContext + Dispatchers.Default) {
            return@withContext map {
                val name = repository.getAppName(it)
                val icon = repository.getAppIcon(it)

                AppListItemModel(it.packageName, name, icon)
            }.sortedBy { it.appName.toLowerCase(Locale.getDefault()) }
        }

    class Factory(
        private val repository: SystemRepository
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel?> create(modelClass: Class<T>) =
            AppListViewModel(repository) as T
    }
}
