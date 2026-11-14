package moe.n4tsu.dextop.xr

import kotlin.test.Test
import kotlin.test.assertEquals

class XrGeometryTest {
    @Test fun singleScalesKeepAspect() {
        assertEquals(1920, canvasWidth("single", 1920, 1f))
        assertEquals(1080, canvasHeight("single", 1080, 1f))
        assertEquals(2560, canvasWidth("single", 1920, 4f / 3f))
        assertEquals(1440, canvasHeight("single", 1080, 4f / 3f))
        assertEquals(3840, canvasWidth("single", 1920, 2f))
        assertEquals(2160, canvasHeight("single", 1080, 2f))
    }

    @Test fun wideAndUltrawideIncludeGaps() {
        assertEquals(3888, canvasWidth("wide", 1920))
        assertEquals(1080, canvasHeight("wide", 1080))
        assertEquals(5856, canvasWidth("ultrawide", 1920))
        assertEquals(1080, canvasHeight("ultrawide", 1080))
    }

    @Test fun singleWindowIsTheCanvas() {
        assertEquals(3840, windowWidth("single", 1920, 3840))
        assertEquals(2160, windowHeight("single", 1080, 2160))
        assertEquals(1920, windowWidth("wide", 1920, 3888))
        assertEquals(1080, windowHeight("ultrawide", 1080, 1080))
    }

    @Test fun zones() {
        val wide = zoneRects("wide", 1920, 1080)
        assertEquals(2, wide.size)
        assertEquals(0, wide[0].left)
        assertEquals(1968, wide[1].left)
        assertEquals(1920, wide[0].right - wide[0].left)
        val ultra = zoneRects("ultrawide", 1920, 1080)
        assertEquals(listOf(0, 1968, 3936), ultra.map { it.left })
        assertEquals(1, zoneRects("nope", 1920, 1080).size)
        assertEquals(1920, zoneRects("nope", 1920, 1080).single().right)
    }

    @Test fun unknownPresetIsSingle() {
        assertEquals(canvasWidth("single", 1920), canvasWidth("nope", 1920))
    }

    @Test fun worldLockCanvasCoversPoseSpace() {
        assertEquals(13984, canvasWidth("single", 1920, worldLock = true))
        assertEquals(7112, canvasHeight("single", 1080, worldLock = true, panelW = 1920))
        assertEquals(13984, canvasWidth("wide", 1920, worldLock = true))
        assertEquals(13984, canvasWidth("ultrawide", 1920, worldLock = true))
        assertEquals(18644, canvasWidth("single", 1920, 4f / 3f, worldLock = true))
        assertEquals(9482, canvasHeight("single", 1080, 4f / 3f, worldLock = true, panelW = 1920))
        assertEquals(27966, canvasWidth("single", 1920, 2f, worldLock = true))
        assertEquals(14224, canvasHeight("single", 1080, 2f, worldLock = true, panelW = 1920))
    }

    @Test fun worldLockWindowIsTheLens() {
        assertEquals(1920, windowWidth("single", 1920, 13984, worldLock = true))
        assertEquals(1080, windowHeight("single", 1080, 7112, worldLock = true))
        assertEquals(3840, windowWidth("single", 1920, 27966, 2f, worldLock = true))
        assertEquals(2160, windowHeight("single", 1080, 14224, 2f, worldLock = true))
    }

    @Test fun worldLockZonesSitOnThePoseOrigin() {
        val single = zoneRects("single", 1920, 1080, worldLock = true).single()
        assertEquals(6032, single.left)
        assertEquals(3016, single.top)
        assertEquals(1920, single.right - single.left)
        val ultra = zoneRects("ultrawide", 1920, 1080, worldLock = true)
        assertEquals(3, ultra.size)
        assertEquals(6032, ultra[1].left)
        assertEquals(3016, ultra[1].top)
        val wide = zoneRects("wide", 1920, 1080, worldLock = true)
        assertEquals(5048, wide[0].left)
        assertEquals(7016, wide[1].left)
    }
}
