/**
 * [OUTPUT]: 对外提供 Placements（Orbit Clean 各页面的广告位名称）
 * [POS]: ui 的广告位清单；名称同时用于频控日志和 Adjust 收入上报，改名会影响历史数据。返回插屏、开屏在 core/ads 的 AdPlacement
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package com.orbitclean.app.ui

object Placements {
    const val HOME_BANNER = "home_banner"
    const val MORE_BANNER = "more_banner"
    const val FILE_LIST_BANNER = "file_list_banner"
    const val SCAN_DONE_INTERSTITIAL = "scan_done_interstitial"
    const val CLEAN_DONE_INTERSTITIAL = "clean_done_interstitial"
    const val TOOL_RESULT_INTERSTITIAL = "tool_result_interstitial"
    const val CLICK_NOTICE_INTERSTITIAL = "click_notice_interstitial"
    const val CLEAN_DONE_BANNER = "clean_done_banner"
}
