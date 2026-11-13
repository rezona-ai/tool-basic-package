/**
 * [INPUT]: 依赖 core 的 CoreServices.context
 * [OUTPUT]: 对外提供 CoreServices.files（全局唯一的 FileStore）
 * [POS]: core/storage 挂到 CoreServices 上的文件读写入口；放在本模块，core/common 就不必依赖 storage。
 *        不使用文件夹授权的品牌不访问它，也就不会创建
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.storage

import com.appbase.core.CoreServices

private object FilesHolder {
    val files: FileStore by lazy { FileStore(CoreServices.context) }
}

/** 授权文件夹的文件读写 */
val CoreServices.files: FileStore get() = FilesHolder.files
