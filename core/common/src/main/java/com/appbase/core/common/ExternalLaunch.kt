/**
 * [OUTPUT]: 对外提供 ExternalLaunch（mark / isRecent）
 * [POS]: core/common 的"刚刚是我们自己跳出 App"标记：打开系统设置、分享、打开文件、评分卡片、点击广告前调用 [mark]，
 *        App 退到后台时用 [isRecent] 判断，回到前台时不当作热启动弹开屏广告
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.core.common

import android.os.SystemClock

object ExternalLaunch {
    /** 跳出后这段时间内的退后台，算作我们自己跳出去的 */
    private const val WINDOW_MS = 5_000L

    private var lastAt = 0L

    /** 即将跳到 App 外部的页面时调用 */
    fun mark() {
        lastAt = SystemClock.elapsedRealtime()
    }

    /** App 退到后台时判断：是不是刚刚由我们自己跳出去的 */
    fun isRecent() = SystemClock.elapsedRealtime() - lastAt < WINDOW_MS
}
