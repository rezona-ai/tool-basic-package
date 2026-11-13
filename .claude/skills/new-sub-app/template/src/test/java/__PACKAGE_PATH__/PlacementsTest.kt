package {{CODE_PACKAGE}}

import {{CODE_PACKAGE}}.ui.Placements
import org.junit.Assert.assertEquals
import org.junit.Test

class PlacementsTest {
    /** 广告位名称用于频控日志和收入上报，不能重复 */
    @Test
    fun placementNamesAreUnique() {
        val names = Placements::class.java.declaredFields.filter { it.type == String::class.java }.map { it.get(null) as String }
        assertEquals(names.size, names.toSet().size)
    }
}
