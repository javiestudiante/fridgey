package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ule.jescuj00.fridgey.ui.theme.Amber
import ule.jescuj00.fridgey.ui.theme.Hairline
import ule.jescuj00.fridgey.ui.theme.HairlineStrong
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkMuted
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.InstrumentSerif
import ule.jescuj00.fridgey.ui.theme.Inter
import ule.jescuj00.fridgey.ui.theme.Mint
import ule.jescuj00.fridgey.ui.theme.MintDarker
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.MintSoft
import ule.jescuj00.fridgey.ui.theme.MintTint
import ule.jescuj00.fridgey.ui.theme.NeveraCardShape
import ule.jescuj00.fridgey.ui.theme.Paper
import ule.jescuj00.fridgey.ui.theme.PillShape

enum class NeveraRole { OWNER, GUEST }

// --- Local type styles (exact design sizes, outside the M3 role set) ---
private val NameStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal,
    fontSize = 26.sp, lineHeight = 27.sp, letterSpacing = (-0.3).sp,
)
private val StatNumberStyle = TextStyle(
    fontFamily = InstrumentSerif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 30.sp,
)
private val StatLabelStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp,
)
private val BadgeStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.44.sp,
)
private val AvatarStyle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp,
)

private val GuestBadgeBg = Color(0x0F1A1F1C)  // rgba(26,31,28,0.06)

/**
 * "Mis neveras" card. Anatomy per Claude Design:
 *  - Row 1: fridge name (serif 26) + role badge ("Propietario"/"Invitado") top-right.
 *  - Row 2: overlapping member avatars. (The "última actividad" line is omitted —
 *    no activity-tracking data exists; see session decision.)
 *  - Statline: top dashed hairline, then 3 stats (PRODUCTOS / POR CADUCAR /
 *    MIEMBROS) separated by vertical dashed hairlines. POR CADUCAR tints amber
 *    when > 0.
 *
 * [featured] paints the highlighted variant (mint-tint→paper gradient, mint-soft
 * border) used for the first/primary fridge.
 */
@Composable
fun NeveraCard(
    name: String,
    role: NeveraRole,
    featured: Boolean,
    memberInitials: List<String>,
    memberCount: Int,
    productCount: Int,
    expiringCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (featured) {
        Modifier.background(Brush.verticalGradient(listOf(MintTint, Paper)))
    } else {
        Modifier.background(Paper)
    }
    val borderColor = if (featured) MintSoft else Hairline

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(NeveraCardShape)
            .then(background)
            .border(1.dp, borderColor, NeveraCardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        // Row 1 — name + role badge.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(
                text = name,
                style = NameStyle,
                color = Ink,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            RoleBadge(role = role)
        }

        Spacer(Modifier.height(8.dp))

        // Row 2 — avatar stack (activity line intentionally omitted).
        MemberAvatarStack(initials = memberInitials, memberCount = memberCount)

        Spacer(Modifier.height(16.dp))

        // Statline.
        DashedHorizontalLine()
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            Stat(productCount.toString(), "PRODUCTOS", Ink, Modifier.weight(1f))
            DashedVerticalLine()
            Stat(
                value = expiringCount.toString(),
                label = "POR CADUCAR",
                accent = if (expiringCount > 0) Amber else Ink,
                modifier = Modifier.weight(1f),
            )
            DashedVerticalLine()
            Stat(memberCount.toString(), "MIEMBROS", Ink, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RoleBadge(role: NeveraRole) {
    val bg: Color
    val fg: Color
    val label: String
    when (role) {
        NeveraRole.OWNER -> { bg = MintSoft; fg = MintDarker; label = "Propietario" }
        NeveraRole.GUEST -> { bg = GuestBadgeBg; fg = InkSoft; label = "Invitado" }
    }
    Text(
        text = label,
        style = BadgeStyle,
        color = fg,
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun MemberAvatarStack(initials: List<String>, memberCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        initials.take(2).forEachIndexed { index, ini ->
            Avatar(text = ini, bg = if (index == 0) Mint else MintDeep)
        }
        if (memberCount > 2) {
            Avatar(text = "+${memberCount - 2}", bg = Amber)
        }
    }
}

@Composable
private fun Avatar(text: String, bg: Color) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, Paper, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AvatarStyle, color = Paper)
    }
}

@Composable
private fun Stat(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = value, style = StatNumberStyle, color = accent)
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = StatLabelStyle, color = InkMuted)
    }
}

private val dashes = floatArrayOf(6f, 6f)

@Composable
private fun DashedHorizontalLine() {
    Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = HairlineStrong,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(dashes, 0f),
        )
    }
}

@Composable
private fun DashedVerticalLine() {
    Canvas(modifier = Modifier.fillMaxHeight().width(1.dp)) {
        drawLine(
            color = HairlineStrong,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = size.width,
            pathEffect = PathEffect.dashPathEffect(dashes, 0f),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF6)
@Composable
private fun NeveraCardPreview() {
    Column(Modifier.padding(16.dp)) {
        NeveraCard(
            name = "Casa",
            role = NeveraRole.OWNER,
            featured = true,
            memberInitials = listOf("J", "M", "A"),
            memberCount = 3,
            productCount = 24,
            expiringCount = 2,
            onClick = {},
        )
        Spacer(Modifier.height(12.dp))
        NeveraCard(
            name = "Oficina",
            role = NeveraRole.GUEST,
            featured = false,
            memberInitials = listOf("L"),
            memberCount = 1,
            productCount = 6,
            expiringCount = 0,
            onClick = {},
        )
    }
}
