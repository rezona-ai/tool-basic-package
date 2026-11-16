/**
 * [INPUT]: 依赖 :core:storage 的 FileStore（CoreServices.files.rename），依赖 RenameRules 的 FileNames
 * [OUTPUT]: 对外提供 RenameOp、RenameOutcome、Renamer.run（执行一批改名，撤销也走它）、Renamer.tempName
 * [POS]: data 的改名执行器。两段式：先把每个文件改成唯一的临时名，再改成目标名。
 *        这样 A↔B 互换、只改大小写（手机存储不区分大小写，会被当成重名）都能成功；第二段失败时把那个文件改回原名
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.data

import com.appbase.core.CoreServices
import com.appbase.core.storage.files

data class RenameOp(val oldName: String, val newName: String)

data class RenameOutcome(val done: List<RenameOp>, val failed: List<Pair<RenameOp, String>>)

object Renamer {

    /** 临时名：不以点开头（避免被当成隐藏文件），保留扩展名（避免媒体库把它当成别的类型） */
    fun tempName(stamp: Long, index: Int, original: String): String =
        "namewise_tmp_${stamp}_$index" + FileNames.split(original).second

    /** 在 [folder] 里执行 [ops]；[onProgress] 回调已完成的步数和总步数（每个文件两步） */
    suspend fun run(folder: String, ops: List<RenameOp>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): RenameOutcome {
        val store = CoreServices.files
        val work = ops.filter { it.oldName != it.newName }
        val total = work.size * 2
        var step = 0
        val stamp = System.currentTimeMillis()
        val failed = mutableListOf<Pair<RenameOp, String>>()

        // 第一段：全部改成临时名
        val staged = mutableListOf<Pair<RenameOp, String>>()
        work.forEachIndexed { i, op ->
            val temp = tempName(stamp, i, op.oldName)
            store.rename("$folder/${op.oldName}", temp)
                .onSuccess { staged += op to temp }
                .onFailure { failed += op to (it.message ?: "Couldn't rename") }
            onProgress(++step, total)
        }
        // 第一段失败的文件第二段不用做，进度直接补上
        step += failed.size
        onProgress(step, total)

        // 第二段：临时名 → 目标名；失败就改回原名
        val done = mutableListOf<RenameOp>()
        for ((op, temp) in staged) {
            val result = store.rename("$folder/$temp", op.newName)
            if (result.isSuccess) {
                done += op
            } else {
                store.rename("$folder/$temp", op.oldName)
                failed += op to (result.exceptionOrNull()?.message ?: "Couldn't rename")
            }
            onProgress(++step, total)
        }
        return RenameOutcome(done, failed)
    }
}
