import SwiftUI

enum NeveraRole {
    case owner
    case guest
}

/// One member chip in the avatar stack. `letter` is centred over a
/// coloured circle; `color` is the caller's choice (typically derived
/// from member id or member palette).
struct MemberAvatar {
    let letter: Character
    let color: Color
}

/// Big card for a single nevera in "Mis neveras".
///
/// Three stacked rows:
///  1. Name (serif headline) + role badge ("Propietario" / "Invitado").
///  2. Overlapping avatar stack (+N pill if `extraMembersCount > 0`)
///     followed by `lastActivityLabel`.
///  3. Three metrics: total products, expiring count, member count. The
///     expiring count tints amber when > 0.
struct NeveraCard: View {
    let name: String
    let role: NeveraRole
    let memberAvatars: [MemberAvatar]
    let extraMembersCount: Int
    let lastActivityLabel: String
    let productCount: Int
    let expiringCount: Int
    let memberCount: Int
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 0) {
                // Row 1 — name + role badge
                HStack(alignment: .center) {
                    Text(name)
                        .font(FridgeyFont.headlineLarge)
                        .foregroundStyle(Color.fridgeyInk)
                    Spacer()
                    roleBadge
                }

                Spacer().frame(height: FridgeySpacing.md)

                // Row 2 — avatars + last-activity text
                HStack(alignment: .center, spacing: FridgeySpacing.sm) {
                    avatarStack
                    Text(lastActivityLabel)
                        .font(FridgeyFont.bodySmall)
                        .foregroundStyle(Color.fridgeyInkSoft)
                }

                Spacer().frame(height: FridgeySpacing.lg)

                // Row 3 — three-column metrics grid
                HStack(spacing: 0) {
                    metric(value: productCount,  label: "PRODUCTOS",   accent: .fridgeyInk)
                    metric(
                        value: expiringCount,
                        label: "POR CADUCAR",
                        accent: expiringCount > 0 ? .fridgeyAmber : .fridgeyInk
                    )
                    metric(value: memberCount,   label: "MIEMBROS",    accent: .fridgeyInk)
                }
            }
            .padding(FridgeySpacing.lg)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.fridgeySurfaceWhite)
            .clipShape(RoundedRectangle(cornerRadius: FridgeyRadius.md))
            .overlay(
                RoundedRectangle(cornerRadius: FridgeyRadius.md)
                    .stroke(Color.fridgeyInk.opacity(0.08), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var roleBadge: some View {
        let label = role == .owner ? "Propietario" : "Invitado"
        return Text(label)
            .font(FridgeyFont.labelMedium)
            .foregroundStyle(Color.fridgeyMintDeep)
            .padding(.horizontal, FridgeySpacing.md)
            .padding(.vertical, FridgeySpacing.xs)
            .background(Color.fridgeyMintSoft, in: Capsule())
    }

    private var avatarStack: some View {
        HStack(spacing: -6) {
            ForEach(memberAvatars.indices, id: \.self) { i in
                avatarCircle(
                    text: String(memberAvatars[i].letter),
                    bg: memberAvatars[i].color
                )
            }
            if extraMembersCount > 0 {
                avatarCircle(text: "+\(extraMembersCount)", bg: Color.fridgeyAmber)
            }
        }
    }

    private func avatarCircle(text: String, bg: Color) -> some View {
        Text(text)
            .font(FridgeyFont.labelMedium)
            .foregroundStyle(Color.fridgeySurfaceWhite)
            .frame(width: 24, height: 24)
            .background(bg, in: Circle())
    }

    private func metric(value: Int, label: String, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: FridgeySpacing.xs) {
            Text(String(value))
                .font(FridgeyFont.numericLarge)
                .foregroundStyle(accent)
            EyebrowLabel(text: label)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    NeveraCard(
        name: "Casa",
        role: .owner,
        memberAvatars: [
            MemberAvatar(letter: "J", color: .fridgeyMint),
            MemberAvatar(letter: "M", color: .fridgeyAmber),
        ],
        extraMembersCount: 1,
        lastActivityLabel: "Última actividad hace 2 h",
        productCount: 24,
        expiringCount: 2,
        memberCount: 3,
        onClick: {}
    )
    .padding()
    .background(Color.fridgeyCream)
}
