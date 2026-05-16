package ule.jescuj00.fridgey.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ule.jescuj00.fridgey.ui.theme.Amber
import ule.jescuj00.fridgey.ui.theme.FridgeyNumericLarge
import ule.jescuj00.fridgey.ui.theme.FridgeyShapes
import ule.jescuj00.fridgey.ui.theme.FridgeyTheme
import ule.jescuj00.fridgey.ui.theme.Ink
import ule.jescuj00.fridgey.ui.theme.InkSoft
import ule.jescuj00.fridgey.ui.theme.LocalFridgeySpacing
import ule.jescuj00.fridgey.ui.theme.Mint
import ule.jescuj00.fridgey.ui.theme.MintDeep
import ule.jescuj00.fridgey.ui.theme.MintSoft
import ule.jescuj00.fridgey.ui.theme.PillShape
import ule.jescuj00.fridgey.ui.theme.SurfaceWhite

enum class NeveraRole { OWNER, GUEST }

/**
 * One member chip in the avatar stack. Letter centred over a coloured
 * circle; colour is the caller's choice (typically derived from member id).
 */
data class MemberAvatar(val letter: Char, val color: Color)

/**
 * Big card for a single nevera in "Mis neveras".
 *
 * Three stacked rows:
 *  1. Name (serif, headlineLarge) + role badge ("Propietario" / "Invitado").
 *  2. Overlapping avatar stack (+N pill if `extraMembersCount > 0`)
 *     followed by `lastActivityLabel`.
 *  3. Three metrics: total products, expiring count, member count. The
 *     expiring count tints amber when > 0.
 */
@Composable
fun NeveraCard(
    name: String,
    role: NeveraRole,
    memberAvatars: List<MemberAvatar>,
    extraMembersCount: Int,
    lastActivityLabel: String,
    productCount: Int,
    expiringCount: Int,
    memberCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalFridgeySpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(FridgeyShapes.medium)
            .background(SurfaceWhite)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = FridgeyShapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(spacing.lg),
    ) {
        // Row 1 — name + role badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            RoleBadge(role = role)
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // Row 2 — avatars + last-activity text
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberAvatarStack(avatars = memberAvatars, extra = extraMembersCount)
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(
                text = lastActivityLabel,
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
        }

        Spacer(modifier = Modifier.height(spacing.lg))

        // Row 3 — three-column metrics grid
        Row(modifier = Modifier.fillMaxWidth()) {
            Metric(
                value = productCount,
                label = "PRODUCTOS",
                accent = Ink,
                modifier = Modifier.weight(1f),
            )
            Metric(
                value = expiringCount,
                label = "POR CADUCAR",
                accent = if (expiringCount > 0) Amber else Ink,
                modifier = Modifier.weight(1f),
            )
            Metric(
                value = memberCount,
                label = "MIEMBROS",
                accent = Ink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RoleBadge(role: NeveraRole) {
    val spacing = LocalFridgeySpacing.current
    val label = when (role) {
        NeveraRole.OWNER -> "Propietario"
        NeveraRole.GUEST -> "Invitado"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MintDeep,
        modifier = Modifier
            .clip(PillShape)
            .background(MintSoft)
            .padding(horizontal = spacing.md, vertical = spacing.xs),
    )
}

@Composable
private fun MemberAvatarStack(avatars: List<MemberAvatar>, extra: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
        avatars.forEach { avatar ->
            AvatarCircle(text = avatar.letter.toString(), bg = avatar.color)
        }
        if (extra > 0) {
            AvatarCircle(text = "+$extra", bg = Amber)
        }
    }
}

@Composable
private fun AvatarCircle(text: String, bg: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = SurfaceWhite,
        )
    }
}

@Composable
private fun Metric(
    value: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LocalFridgeySpacing.current.xs),
    ) {
        Text(
            text = value.toString(),
            style = FridgeyNumericLarge,
            color = accent,
        )
        EyebrowLabel(text = label)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBFAF6)
@Composable
private fun NeveraCardPreview() {
    FridgeyTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            NeveraCard(
                name = "Casa",
                role = NeveraRole.OWNER,
                memberAvatars = listOf(
                    MemberAvatar('J', Mint),
                    MemberAvatar('M', Amber),
                ),
                extraMembersCount = 1,
                lastActivityLabel = "Última actividad hace 2 h",
                productCount = 24,
                expiringCount = 2,
                memberCount = 3,
                onClick = {},
            )
        }
    }
}
