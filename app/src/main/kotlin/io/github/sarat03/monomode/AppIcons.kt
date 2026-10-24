package io.github.sarat03.monomode

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The five Material outlined icons MonoMode uses beyond the core set, copied here so the 10 MB extended set is not needed. */
object AppIcons {
    private fun icon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(addPathNodes(path), fill = SolidColor(Color.Black)).build()

    val Shield by lazy { icon("Shield", "M12,2L4,5v6.09c0,5.05 3.41,9.76 8,10.91c4.59,-1.15 8,-5.86 8,-10.91V5L12,2zM18,11.09c0,4 -2.55,7.7 -6,8.83c-3.45,-1.13 -6,-4.82 -6,-8.83v-4.7l6,-2.25l6,2.25V11.09z") }
    val Broom by lazy { icon("CleaningServices", "M16,11h-1V3c0,-1.1 -0.9,-2 -2,-2h-2C9.9,1 9,1.9 9,3v8H8c-2.76,0 -5,2.24 -5,5v7h18v-7C21,13.24 18.76,11 16,11zM11,3h2v8h-2V3zM19,21h-2v-3c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1v3h-2v-3c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1v3H9v-3c0,-0.55 -0.45,-1 -1,-1s-1,0.45 -1,1v3H5v-5c0,-1.65 1.35,-3 3,-3h8c1.65,0 3,1.35 3,3V21z") }
    val Grid by lazy { icon("GridView", "M3,3v8h8V3H3zM9,9H5V5h4V9zM3,13v8h8v-8H3zM9,19H5v-4h4V19zM13,3v8h8V3H13zM19,9h-4V5h4V9zM13,13v8h8v-8H13zM19,19h-4v-4h4V19z") }
    val Book by lazy { icon("MenuBook", "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05c0.1,0.05 0.15,0.05 0.25,0.05c0.25,0 0.5,-0.25 0.5,-0.5V6C22.4,5.55 21.75,5.25 21,5zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5V18.5z") }
}
