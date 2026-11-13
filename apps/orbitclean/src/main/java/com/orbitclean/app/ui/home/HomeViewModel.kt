/**
 * [INPUT]: 依赖 data/ScanRepository 的 storageInfo，依赖 lifecycle 的 ViewModel
 * [OUTPUT]: 对外提供 HomeViewModel（storage 状态 + refresh）
 * [POS]: ui/home 的状态层，HomeScreen 每次回到前台调用 refresh，清理后首页的用量随之更新
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.appbase.core.storage.StorageInfo
import com.orbitclean.app.data.ScanRepository

class HomeViewModel(private val repo: ScanRepository) : ViewModel() {

    var storage: StorageInfo by mutableStateOf(repo.store.storageInfo())
        private set

    fun refresh() {
        storage = repo.store.storageInfo()
    }
}
