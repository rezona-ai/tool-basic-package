/**
 * [OUTPUT]: 对外提供 Placements（Namewise 各页面的广告位名称）
 * [POS]: ui 的广告位清单；名称用于频控日志和广告收入上报。开屏在 :core:ads 的 AdPlacement。
 *        插屏只放在一次核心操作的结果出来之后（结果页），每次操作最多一次；不用返回插屏，不打断正在进行的操作
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.appbase.renamer.ui

object Placements {
    const val HOME_BANNER = "home_banner"
    const val RESULT_INTERSTITIAL = "result_interstitial"
}
