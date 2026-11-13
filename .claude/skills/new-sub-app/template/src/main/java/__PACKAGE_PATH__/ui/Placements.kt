/**
 * [OUTPUT]: 对外提供 Placements（{{APP_NAME}} 各页面的广告位名称）
 * [POS]: ui 的广告位清单；名称用于频控日志和 Adjust 收入上报。返回插屏、开屏在 :core:ads 的 AdPlacement。
 *        插屏只放在操作完成之后（结果页、返回），不打断正在进行的操作
 * [PROTOCOL]: 变更时更新此头部，然后检查 CLAUDE.md
 */
package {{CODE_PACKAGE}}.ui

object Placements {
    const val HOME_BANNER = "home_banner"
    const val RESULT_INTERSTITIAL = "result_interstitial"
}
