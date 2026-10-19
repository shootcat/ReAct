package com.shootcat.react.sim

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures of the inhabitants' ways (Phase 5a) for the report: each scene as a row of pictures at the given seconds,
 * with what the figures did written under each ("build/pictures/5a_*.png").
 */
class FolkPicturesTest {

    private fun lab(ground: Int = 70): Lab {
        val lab = Lab(160, 80)
        lab.rect("rock", 2, ground, 157, 77)
        return lab
    }

    /** Pictures of [lab] at [seconds] (cut to [area], [scale] times), side by side with [note] under each. */
    private fun strip(name: String, title: String, lab: Lab, area: IntArray, seconds: List<Float>, scale: Int = 5, note: (Lab) -> String) {
        val sky = IntArray(lab.world.size) { 0xFF9CC3E0.toInt() }
        val frames = ArrayList<Pair<BufferedImage, String>>()
        var at = 0
        for (t in seconds) {
            val target = (t * 60).toInt()
            lab.world.run(target - at)
            at = target
            val f = Pictures.write(lab.world, sky, "5a_${name}_$at", scale = scale, area = area)
            frames += ImageIO.read(f) to "${"%.1f".format(t)} s: ${note(lab)}"
        }
        val pw = frames[0].first.width
        val ph = frames[0].first.height
        val label = 46
        val out = BufferedImage(pw * frames.size + 8 * (frames.size - 1), ph + label + 30, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = Color(16, 20, 26)
        g.fillRect(0, 0, out.width, out.height)
        g.color = Color.WHITE
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 20)
        g.drawString(title, 10, 22)
        for ((k, f) in frames.withIndex()) {
            g.drawImage(f.first, k * (pw + 8), 30, null)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            g.drawString(f.second, k * (pw + 8) + 6, 30 + ph + 20)
        }
        g.dispose()
        val file = File("build/pictures/5a_$name.png")
        ImageIO.write(out, "png", file)
        println("5A ${file.name}: " + frames.joinToString(" | ") { it.second })
    }

    private fun Lab.say(): String = world.folk.figures().joinToString("; ") { f ->
        val what = when (f.state) {
            Folk.WALK -> if (f.home) "im Dorf" else "geht"
            Folk.FALL -> "fällt"
            Folk.SWIM -> "schwimmt"
            Folk.SINK -> "geht unter"
            Folk.FAINT -> "ohnmächtig"
            else -> "kommt zurück"
        }
        "$what (${f.x},${f.y})"
    }

    @Test
    fun pictures() {
        // walking, two steps up, the third too high: back
        val steps = lab()
        steps.rect("rock", 40, 69, 157, 69)
        steps.rect("rock", 50, 67, 157, 68)
        steps.rect("rock", 60, 64, 157, 66)
        steps.world.folk.add(25, 69)
        strip("stufen", "Stufen: eine und zwei Zellen hinauf, drei zu hoch, kehrt um", steps, intArrayOf(20, 54, 66, 72), listOf(0f, 2f, 4f, 6f), scale = 9) { it.say() }

        // lava ahead: it sees it and turns back
        val lava = lab()
        lava.rect("air", 80, 70, 88, 73)
        lava.rect("lava", 80, 70, 88, 73)
        lava.world.folk.add(48, 69)
        strip("lava", "Lava voraus: gesehen, umgekehrt", lava, intArrayOf(42, 56, 92, 75), listOf(0f, 1.5f, 3f, 4.5f), scale = 8) { it.say() }

        // a high fall: it faints and is brought back to the village
        val fall = lab()
        fall.world.folk.setHome(120, 60)
        fall.world.folk.add(60, 20)
        strip("sturz", "Sturz aus 49 Zellen: Ohnmacht, zurück ins Dorf", fall, intArrayOf(54, 14, 134, 72), listOf(0f, 0.8f, 2f, 3.5f, 5f), scale = 5) { it.say() }

        // into the lake: it swims across and climbs out
        val lake = lab(ground = 60)
        lake.rect("air", 60, 60, 79, 72)
        lake.rect("water", 60, 60, 79, 72)
        lake.world.folk.add(62, 30)
        strip("schwimmen", "In den See gefallen: schwimmt hinüber, steigt aus", lake, intArrayOf(54, 26, 90, 74), listOf(0f, 1f, 3f, 5f), scale = 8) { it.say() }

        // a block falling at it: it runs out from under it
        val block = lab()
        block.world.folk.add(62, 69)
        block.rect("stone", 60, 30, 65, 35)
        strip("ausweichen", "Ein Block fällt: läuft darunter weg", block, intArrayOf(44, 26, 78, 72), listOf(0f, 0.2f, 0.4f, 1f), scale = 8) { it.say() }

        // over a bridge across a chasm
        val bridge = lab(ground = 60)
        bridge.rect("air", 80, 60, 99, 77)
        bridge.rect("wood", 76, 59, 103, 59)
        bridge.world.folk.add(70, 58)
        strip("bruecke", "Über die Brücke", bridge, intArrayOf(66, 46, 110, 70), listOf(0f, 2f, 4f), scale = 8) { it.say() }
    }
}
