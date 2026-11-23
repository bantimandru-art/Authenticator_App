package com.uday.auth.ui

import android.content.res.ColorStateList
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.uday.auth.R

/**
 * Shows the first letter of [name] as a tonal tile: the letter in full colour on a faint
 * wash of the same colour. The same name always gets the same colour.
 */
fun TextView.showAvatar(name: String) {
    val colors = resources.obtainTypedArray(R.array.avatar_colors)
    val color = colors.getColor(Math.floorMod(name.lowercase().hashCode(), colors.length()), 0)
    colors.recycle()

    text = name.firstOrNull()?.uppercase() ?: "?"
    setTextColor(color)
    backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(color, 0x2E))
}
