/**
 * [OUTPUT]: 对外提供 Placements（{{APP_NAME}} 各页面的广告位名称）
 * [POS]: ui 的广告位清单；名称用于频控日志和广告收入上报。开屏在 :core:ads 的 AdPlacement。
 *        插屏只在结果出来之后弹：扫描结果出来（每次扫描最多一次）、一次清理完成；不用返回插屏，不打断正在进行的删除
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui

object Placements {
    const val HOME_BANNER = "home_banner"
    const val DONE_BANNER = "done_banner"
    const val SCAN_DONE_INTERSTITIAL = "scan_done_interstitial"
    const val CLEAN_DONE_INTERSTITIAL = "clean_done_interstitial"
}
