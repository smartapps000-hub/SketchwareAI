package pz

import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class SmokeTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test fun telugu() {
        val t = TextView(paparazzi.context)
        t.textSize = 28f
        t.text = "అను ఫాంట్స్ ఈశ్వర్ నీ శ్రీ క్ష్మీ"
        paparazzi.snapshot(t)
    }
}
