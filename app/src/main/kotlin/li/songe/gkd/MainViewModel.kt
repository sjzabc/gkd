package li.songe.gkd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import li.songe.gkd.data.RawSubscription
import li.songe.gkd.data.SubsItem
import li.songe.gkd.db.DbSet
import li.songe.gkd.permission.AuthReason
import li.songe.gkd.ui.component.AlertDialogOptions
import li.songe.gkd.ui.component.UploadOptions
import li.songe.gkd.util.LOCAL_SUBS_ID
import li.songe.gkd.util.UpdateStatus
import li.songe.gkd.util.checkUpdate
import li.songe.gkd.util.clearCache
import li.songe.gkd.util.launchTry
import li.songe.gkd.util.map
import li.songe.gkd.util.storeFlow
import li.songe.gkd.util.updateSubscription

class MainViewModel : ViewModel() {
    val enableDarkThemeFlow = storeFlow.debounce(300).map { s -> s.enableDarkTheme }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDarkTheme
    )
    val enableDynamicColorFlow = storeFlow.debounce(300).map { s -> s.enableDynamicColor }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDynamicColor
    )

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val updateStatus = UpdateStatus()

    val shizukuErrorFlow = MutableStateFlow(false)

    val uploadOptions = UploadOptions(this)

    val showEditCookieDlgFlow = MutableStateFlow(false)

    init {
        viewModelScope.launchTry(Dispatchers.IO) {
            val subsItems = DbSet.subsItemDao.queryAll()
            if (!subsItems.any { s -> s.id == LOCAL_SUBS_ID }) {
                updateSubscription(
                    RawSubscription(
                        id = LOCAL_SUBS_ID,
                        name = "本地订阅",
                        version = 0
                    )
                )
                DbSet.subsItemDao.insert(
                    SubsItem(
                        id = LOCAL_SUBS_ID,
                        order = subsItems.minByOrNull { it.order }?.order ?: 0,
                    )
                )
            }
        }

        viewModelScope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            clearCache()
        }

        if (META.updateEnabled && storeFlow.value.autoCheckAppUpdate) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    updateStatus.checkUpdate()
                } catch (e: Exception) {
                    e.printStackTrace()
                    LogUtils.d(e)
                }
            }
        }

        viewModelScope.launch {
            storeFlow.map(viewModelScope) { s -> s.log2FileSwitch }.collect {
                LogUtils.getConfig().isLog2FileSwitch = it
            }
        }
    }
}