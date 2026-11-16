package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.model.AccessoryStyle
import com.example.model.CharacterArchetype
import com.example.model.HatStyle
import com.example.model.SkinConfig
import com.example.model.toComposeColor
import kotlin.math.sin

@Composable
fun SurvivorCharacterPreview(
    skin: SkinConfig,
    modifier: Modifier = Modifier,
    isMoving: Boolean = false,
    walkPhase: Float = 0f,
    hasTorch: Boolean = false,
    hasSpear: Boolean = false
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.62f
        val scale = minOf(size.width, size.height) / 110f
        drawSurvivorCharacter(
            center = Offset(cx, cy),
            scale = scale,
            skin = skin,
            facingRight = true,
            walkBob = if (isMoving) sin(walkPhase) * 3f else 0f,
            isAttacking = false,
            hasTorch = hasTorch,
            hasSpear = hasSpear
        )
    }
}

fun DrawScope.drawSurvivorCharacter(
    center: Offset,
    scale: Float,
    skin: SkinConfig,
    facingRight: Boolean,
    walkBob: Float,
    isAttacking: Boolean,
    hasTorch: Boolean,
    hasSpear: Boolean
) {
    val skinColor = skin.skinToneHex.toComposeColor(Color(0xFFF5D6BA))
    val coatColor = skin.coatColorHex.toComposeColor(Color(0xFFE87A3E))
    val hairColor = skin.hairColorHex.toComposeColor(Color(0xFF1F1B1E))
    val outline = Color(0xFF141115)
    val dir = if (facingRight) 1f else -1f

    // Ground shadow
    drawOval(
        color = Color.Black.copy(alpha = 0.42f),
        topLeft = Offset(center.x - 18f * scale, center.y + 18f * scale),
        size = Size(36f * scale, 11f * scale)
    )

    val bodyY = center.y + walkBob * scale

    // Accessory behind body
    when (skin.accessoryEnum()) {
        AccessoryStyle.BACKPACK -> {
            drawRoundRect(
                color = Color(0xFF6B4423),
                topLeft = Offset(center.x - dir * 15f * scale - 6f * scale, bodyY - 12f * scale),
                size = Size(12f * scale, 22f * scale),
                cornerRadius = CornerRadius(4f * scale)
            )
        }
        AccessoryStyle.CAPE -> {
            val capePath = Path().apply {
                moveTo(center.x - dir * 5f * scale, bodyY - 14f * scale)
                lineTo(center.x - dir * 24f * scale, bodyY + 18f * scale)
                lineTo(center.x + dir * 4f * scale, bodyY + 14f * scale)
                close()
            }
            drawPath(capePath, color = Color(0xFF3E2938))
        }
        AccessoryStyle.QUIVER -> {
            drawRoundRect(
                color = Color(0xFF7D522D),
                topLeft = Offset(center.x - dir * 14f * scale - 4f * scale, bodyY - 18f * scale),
                size = Size(8f * scale, 22f * scale),
                cornerRadius = CornerRadius(3f * scale)
            )
        }
        else -> Unit
    }

    // Legs
    val legSwing = walkBob * 1.4f * scale
    drawLine(
        color = outline,
        start = Offset(center.x - 6f * scale, bodyY + 10f * scale),
        end = Offset(center.x - 7f * scale + legSwing, bodyY + 23f * scale),
        strokeWidth = 4.5f * scale
    )
    drawLine(
        color = outline,
        start = Offset(center.x + 6f * scale, bodyY + 10f * scale),
        end = Offset(center.x + 7f * scale - legSwing, bodyY + 23f * scale),
        strokeWidth = 4.5f * scale
    )

    // Coat / Torso (A-line Don't Starve silhouette)
    val torsoPath = Path().apply {
        moveTo(center.x - 10f * scale, bodyY - 15f * scale)
        lineTo(center.x + 10f * scale, bodyY - 15f * scale)
        lineTo(center.x + 14f * scale, bodyY + 13f * scale)
        lineTo(center.x - 14f * scale, bodyY + 13f * scale)
        close()
    }
    drawPath(torsoPath, color = coatColor)
    drawPath(torsoPath, color = outline, style = Stroke(width = 2.2f * scale))

    // Collar detail
    drawLine(
        color = Color(0xFFEDE3D2),
        start = Offset(center.x - 6f * scale, bodyY - 14f * scale),
        end = Offset(center.x + 6f * scale, bodyY - 14f * scale),
        strokeWidth = 2.5f * scale
    )

    // Belt lantern accessory
    if (skin.accessoryEnum() == AccessoryStyle.LANTERN_BELT) {
        drawCircle(
            color = Color(0xFFF4B942),
            radius = 4.5f * scale,
            center = Offset(center.x - dir * 11f * scale, bodyY + 6f * scale)
        )
    }

    // Head
    val headCenter = Offset(center.x, bodyY - 28f * scale)
    drawCircle(
        color = skinColor,
        radius = 15.5f * scale,
        center = headCenter
    )
    drawCircle(
        color = outline,
        radius = 15.5f * scale,
        center = headCenter,
        style = Stroke(width = 2.3f * scale)
    )

    // Archetype / Hair base
    val arch = skin.archetypeEnum()
    val hairTopPath = Path().apply {
        moveTo(headCenter.x - 15f * scale, headCenter.y - 3f * scale)
        lineTo(headCenter.x - 12f * scale, headCenter.y - 18f * scale)
        lineTo(headCenter.x, headCenter.y - (if (arch == CharacterArchetype.SCHOLAR) 24f else 19f) * scale)
        lineTo(headCenter.x + 12f * scale, headCenter.y - 18f * scale)
        lineTo(headCenter.x + 15f * scale, headCenter.y - 3f * scale)
        close()
    }
    drawPath(hairTopPath, color = hairColor)

    // Expressive Don't Starve style oval eyes
    val eyeOffset = dir * 3f * scale
    drawOval(
        color = Color(0xFFEDE3D2),
        topLeft = Offset(headCenter.x - 8f * scale + eyeOffset, headCenter.y - 4f * scale),
        size = Size(5.5f * scale, 7.5f * scale)
    )
    drawOval(
        color = Color(0xFFEDE3D2),
        topLeft = Offset(headCenter.x + 2.5f * scale + eyeOffset, headCenter.y - 4f * scale),
        size = Size(5.5f * scale, 7.5f * scale)
    )
    drawCircle(
        color = outline,
        radius = 1.8f * scale,
        center = Offset(headCenter.x - 5f * scale + eyeOffset * 1.2f, headCenter.y)
    )
    drawCircle(
        color = outline,
        radius = 1.8f * scale,
        center = Offset(headCenter.x + 5.5f * scale + eyeOffset * 1.2f, headCenter.y)
    )

    // Hat / Headgear
    when (skin.hatEnum()) {
        HatStyle.EXPLORER_HAT -> {
            drawOval(
                color = Color(0xFFC49A5B),
                topLeft = Offset(headCenter.x - 21f * scale, headCenter.y - 18f * scale),
                size = Size(42f * scale, 10f * scale)
            )
            drawRoundRect(
                color = Color(0xFF9C763E),
                topLeft = Offset(headCenter.x - 12f * scale, headCenter.y - 29f * scale),
                size = Size(24f * scale, 14f * scale),
                cornerRadius = CornerRadius(5f * scale)
            )
        }
        HatStyle.VIKING_HELM -> {
            drawArc(
                color = Color(0xFF8B939C),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(headCenter.x - 15f * scale, headCenter.y - 24f * scale),
                size = Size(30f * scale, 24f * scale)
            )
            // Horns
            drawCircle(Color(0xFFEDE3D2), radius = 4f * scale, center = Offset(headCenter.x - 16f * scale, headCenter.y - 19f * scale))
            drawCircle(Color(0xFFEDE3D2), radius = 4f * scale, center = Offset(headCenter.x + 16f * scale, headCenter.y - 19f * scale))
        }
        HatStyle.FLOWER_CROWN -> {
            val petals = listOf(Color(0xFFE87A3E), Color(0xFFF4B942), Color(0xFFC84B31), Color(0xFF8E7CC3))
            for (i in 0..3) {
                drawCircle(
                    color = petals[i],
                    radius = 4f * scale,
                    center = Offset(headCenter.x + (-11f + i * 7.3f) * scale, headCenter.y - 14f * scale)
                )
            }
        }
        HatStyle.LANTERN_HOOD -> {
            drawArc(
                color = coatColor.copy(alpha = 0.92f),
                startAngle = 165f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = Offset(headCenter.x - 18f * scale, headCenter.y - 20f * scale),
                size = Size(36f * scale, 34f * scale),
                style = Stroke(width = 6f * scale)
            )
        }
        HatStyle.GOGGLES -> {
            drawRoundRect(
                color = Color(0xFF544332),
                topLeft = Offset(headCenter.x - 15f * scale, headCenter.y - 15f * scale),
                size = Size(30f * scale, 6f * scale),
                cornerRadius = CornerRadius(2f * scale)
            )
            drawCircle(Color(0xFF66D1E8), radius = 4.2f * scale, center = Offset(headCenter.x - 6f * scale, headCenter.y - 12f * scale))
            drawCircle(Color(0xFF66D1E8), radius = 4.2f * scale, center = Offset(headCenter.x + 6f * scale, headCenter.y - 12f * scale))
        }
        HatStyle.NONE -> Unit
    }

    // Held Weapon / Torch
    val handX = center.x + dir * 16f * scale
    val handY = bodyY + (if (isAttacking) -6f else 2f) * scale
    if (hasSpear) {
        val tipX = handX + dir * (if (isAttacking) 26f else 14f) * scale
        val tipY = handY - (if (isAttacking) 4f else 22f) * scale
        drawLine(
            color = Color(0xFF7D5632),
            start = Offset(handX - dir * 6f * scale, handY + 10f * scale),
            end = Offset(tipX, tipY),
            strokeWidth = 3f * scale
        )
        drawCircle(Color(0xFFD6D2CA), radius = 4f * scale, center = Offset(tipX, tipY))
    } else if (hasTorch) {
        drawLine(
            color = Color(0xFF6A4525),
            start = Offset(handX, handY + 6f * scale),
            end = Offset(handX + dir * 4f * scale, handY - 14f * scale),
            strokeWidth = 3.5f * scale
        )
        drawCircle(
            color = Color(0xFFE87A3E).copy(alpha = 0.4f),
            radius = 16f * scale,
            center = Offset(handX + dir * 4f * scale, handY - 17f * scale)
        )
        drawCircle(
            color = Color(0xFFF4B942),
            radius = 5.5f * scale,
            center = Offset(handX + dir * 4f * scale, handY - 17f * scale)
        )
    }
}
