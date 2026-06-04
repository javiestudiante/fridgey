import SwiftUI

enum NeveraRole {
    case owner
    case guest
}

/// Dashed line shape (horizontal or vertical) for the statline separators.
private struct DashedLine: Shape {
    var horizontal: Bool = true
    func path(in rect: CGRect) -> Path {
        var p = Path()
        if horizontal {
            p.move(to: CGPoint(x: 0, y: rect.midY))
            p.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        } else {
            p.move(to: CGPoint(x: rect.midX, y: 0))
            p.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        }
        return p
    }
}

/// "Mis neveras" card (parity with Android `NeveraCard`).
///  1. name (serif 26) + role badge ("Propietario"/"Invitado") top-right.
///  2. overlapping member avatars (the "última actividad" line is omitted —
///     no activity data; session decision).
///  3. statline: top dashed hairline + 3 stats (PRODUCTOS / POR CADUCAR /
///     MIEMBROS, serif 28) separated by vertical dashed hairlines. POR CADUCAR
///     tints amber when > 0.
///
/// `featured` paints the highlighted variant (mint-tint→paper gradient,
/// mint-soft border).
struct NeveraCard: View {
    let name: String
    let role: NeveraRole
    let featured: Bool
    let memberInitials: [String]
    let memberCount: Int
    let productCount: Int
    let expiringCount: Int
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .top) {
                    Text(name)
                        .font(.custom("InstrumentSerif-Regular", size: 26))
                        .foregroundStyle(Color.fridgeyInk)
                    Spacer()
                    roleBadge
                }

                Spacer().frame(height: FridgeySpacing.sm)

                avatarStack

                Spacer().frame(height: FridgeySpacing.lg)

                statline
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: FridgeyRadius.card))
            .overlay(
                RoundedRectangle(cornerRadius: FridgeyRadius.card)
                    .stroke(featured ? Color.fridgeyMintSoft : Color.fridgeyHairline, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var cardBackground: some View {
        if featured {
            LinearGradient(
                colors: [Color.fridgeyMintTint, Color.fridgeySurfaceWhite],
                startPoint: .top, endPoint: .bottom
            )
        } else {
            Color.fridgeySurfaceWhite
        }
    }

    private var roleBadge: some View {
        let isOwner = role == .owner
        return Text(isOwner ? "Propietario" : "Invitado")
            .font(.custom("Inter-Regular", size: 11).weight(.semibold))
            .foregroundStyle(isOwner ? Color.fridgeyMintDarker : Color.fridgeyInkSoft)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(
                (isOwner ? Color.fridgeyMintSoft : Color.fridgeyInk.opacity(0.06)),
                in: Capsule()
            )
    }

    private var avatarStack: some View {
        HStack(spacing: -6) {
            ForEach(Array(memberInitials.prefix(2).enumerated()), id: \.offset) { idx, ini in
                avatar(text: ini, bg: idx == 0 ? Color.fridgeyMint : Color.fridgeyMintDeep)
            }
            if memberCount > 2 {
                avatar(text: "+\(memberCount - 2)", bg: Color.fridgeyAmber)
            }
        }
    }

    private func avatar(text: String, bg: Color) -> some View {
        Text(text)
            .font(.custom("Inter-Regular", size: 10).weight(.semibold))
            .foregroundStyle(Color.fridgeySurfaceWhite)
            .frame(width: 22, height: 22)
            .background(bg, in: Circle())
            .overlay(Circle().stroke(Color.fridgeySurfaceWhite, lineWidth: 2))
    }

    private var statline: some View {
        VStack(spacing: 0) {
            DashedLine()
                .stroke(Color.fridgeyHairlineStrong, style: StrokeStyle(lineWidth: 1, dash: [6, 6]))
                .frame(height: 1)
            Spacer().frame(height: 14)
            HStack(spacing: 0) {
                stat(value: productCount, label: "PRODUCTOS", accent: .fridgeyInk)
                statDivider
                stat(value: expiringCount, label: "POR CADUCAR",
                     accent: expiringCount > 0 ? .fridgeyAmber : .fridgeyInk)
                statDivider
                stat(value: memberCount, label: "MIEMBROS", accent: .fridgeyInk)
            }
        }
    }

    private var statDivider: some View {
        DashedLine(horizontal: false)
            .stroke(Color.fridgeyHairlineStrong, style: StrokeStyle(lineWidth: 1, dash: [6, 6]))
            .frame(width: 1, height: 46)
    }

    private func stat(value: Int, label: String, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(String(value))
                .font(.custom("InstrumentSerif-Regular", size: 28))
                .foregroundStyle(accent)
            Text(label)
                .font(.custom("Inter-Regular", size: 11).weight(.medium))
                .tracking(0.5)
                .foregroundStyle(Color.fridgeyInkMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    VStack(spacing: 12) {
        NeveraCard(name: "Casa", role: .owner, featured: true,
                   memberInitials: ["J", "M", "A"], memberCount: 3,
                   productCount: 24, expiringCount: 2, onClick: {})
        NeveraCard(name: "Oficina", role: .guest, featured: false,
                   memberInitials: ["L"], memberCount: 1,
                   productCount: 6, expiringCount: 0, onClick: {})
    }
    .padding()
    .background(Color.fridgeyCream)
}
