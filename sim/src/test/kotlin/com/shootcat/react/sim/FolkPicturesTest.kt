package com.shootcat.react.sim

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures of the inhabitants' ways (Phase 5a) and needs (5b) for the report: each scene as a row of pictures at the
 * given seconds, with what the figures did written under each ("build/pictures/5a_*.png", "5b_*.png").
 */
class FolkPicturesTest {

    private fun lab(ground: Int = 70, ambient: Float = 20f): Lab {
        val lab = Lab(160, 80)
        lab.world.setAmbient(ambient)
        lab.rect("rock", 2, ground, 157, 77, t = ambient)
        return lab
    }

    /** Pictures of [lab] at [seconds] (cut to [area], [scale] times), side by side with [note] under each. */
    private fun strip(name: String, title: String, lab: Lab, area: IntArray, seconds: List<Float>, scale: Int = 5, phase: String = "5a", note: (Lab) -> String) {
        val sky = IntArray(lab.world.size) { 0xFF9CC3E0.toInt() }
        val frames = ArrayList<Pair<BufferedImage, String>>()
        var at = 0
        for (t in seconds) {
            val target = (t * 60).toInt()
            lab.world.run(target - at)
            at = target
            val f = Pictures.write(lab.world, sky, "${phase}_${name}_$at", scale = scale, area = area)
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
        val file = File("build/pictures/${phase}_$name.png")
        ImageIO.write(out, "png", file)
        println("${phase.uppercase()} ${file.name}: " + frames.joinToString(" | ") { it.second })
    }

    private fun Lab.say(): String = world.folk.figures().withIndex().joinToString("; ") { (k, f) ->
        val what = when (f.state) {
            Folk.WALK -> if (f.home) "im Dorf" else "geht"
            Folk.FALL -> "fällt"
            Folk.SWIM -> "schwimmt"
            Folk.SINK -> "geht unter"
            Folk.FAINT -> "ohnmächtig"
            Folk.WARM -> "wärmt sich"
            Folk.KINDLE -> "zündet an"
            else -> "kommt zurück"
        }
        val needs = world.folk.needs(k)
        val lacks = listOfNotNull(
            if (needs and Folk.Need.FROZEN != 0) "friert sehr" else if (needs and Folk.Need.COLD != 0) "friert" else null,
            if (needs and Folk.Need.THIRST != 0) "durstig" else null,
            if (needs and Folk.Need.AIR != 0) "Atemnot" else null,
        )
        "$what${if (lacks.isEmpty()) "" else ", " + lacks.joinToString(", ")} (${f.x},${f.y})"
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

    @Test
    fun needs() {
        // hard frost: it freezes, walks slower, faints and comes back warm
        val frost = lab(ambient = -20f)
        frost.world.folk.add(40, 69)
        strip("frost", "Frost −20 °C: friert nach 5 s, langsamer nach 10 s, Ohnmacht nach 20 s", frost, intArrayOf(30, 56, 110, 72), listOf(0f, 6f, 12f, 20.5f), scale = 5, phase = "5b") { it.say() }

        // freezing, it goes up to a campfire and warms itself
        val fire = lab(ambient = -20f)
        fire.rect("wood", 100, 66, 102, 69, t = 400f)
        fire.world.folk.add(70, 69)
        fire.world.folk.setNeeds(1, cold = 700)
        strip("feuer", "Frierend ans Lagerfeuer, aufgewärmt, wieder weg", fire, intArrayOf(66, 54, 108, 72), listOf(0f, 2f, 4f, 7f), scale = 7, phase = "5b") { it.say() }

        // freezing, it kindles a log at a burning post
        val kindle = lab(ambient = -20f)
        kindle.rect("wood", 80, 68, 82, 69)
        kindle.rect("wood", 96, 62, 97, 69, t = 400f)
        kindle.world.folk.add(60, 69)
        kindle.world.folk.setNeeds(1, cold = 700)
        strip("anzuenden", "Frierend: zündet den Holzklotz am brennenden Pfahl an, wärmt sich daran", kindle, intArrayOf(56, 54, 102, 72), listOf(0f, 2.2f, 3.5f, 5.5f), scale = 7, phase = "5b") { it.say() }

        // thirsty: it wades into the pond and drinks
        val pond = lab()
        pond.rect("air", 80, 70, 95, 71)
        pond.rect("water", 80, 70, 95, 71)
        pond.world.folk.add(66, 69)
        pond.world.folk.setNeeds(1, thirst = Folk.THIRSTY + 100)
        strip("trinken", "Durstig: watet in den Teich und trinkt", pond, intArrayOf(62, 58, 98, 73), listOf(0f, 1.5f, 2.5f), scale = 8, phase = "5b") { it.say() }

        // thirsty at salt water: it does not drink
        val salt = lab()
        salt.rect("air", 80, 70, 95, 71)
        salt.rect("saltwater", 80, 70, 95, 71)
        salt.world.folk.add(66, 69)
        salt.world.folk.setNeeds(1, thirst = Folk.THIRSTY + 100)
        strip("salzwasser", "Durstig am Salzwasser: trinkt nicht", salt, intArrayOf(62, 58, 98, 73), listOf(0f, 2.5f, 5f), scale = 8, phase = "5b") { it.say() }

        // firedamp under the roof of a gallery: it runs out into clean air
        val gas = lab()
        gas.rect("rock", 2, 40, 157, 63)
        gas.rect("rock", 120, 64, 157, 69)
        gas.rect("firedamp", 70, 64, 119, 67)
        gas.world.folk.add(100, 69)
        strip("grubengas", "Grubengas in der Strecke (gelblich, oben): Atemnot, läuft ins Freie", gas, intArrayOf(56, 58, 122, 72), listOf(0f, 1f, 2f, 4f), scale = 6, phase = "5b") { it.say() }

        // shut in with firedamp: no clean air, it faints
        val shut = lab()
        shut.rect("rock", 2, 40, 157, 63)
        shut.rect("rock", 120, 64, 157, 69)
        shut.rect("rock", 2, 64, 69, 69)
        shut.rect("firedamp", 70, 64, 119, 69)
        shut.world.folk.add(100, 69)
        strip("grubengas_zu", "Eingeschlossen im Grubengas: Ohnmacht nach 8 s", shut, intArrayOf(66, 58, 122, 72), listOf(0f, 4f, 8.2f), scale = 6, phase = "5b") { it.say() }
    }
}
