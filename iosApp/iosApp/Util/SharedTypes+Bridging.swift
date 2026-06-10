import Foundation
import Shared

// MARK: - kotlinx.datetime.LocalDate ↔ Swift Date

extension Kotlinx_datetimeLocalDate {
    /// Build a Kotlin `LocalDate` from a Swift `Date` using the device's
    /// current calendar (year/month/day are taken from local time).
    static func from(date: Date, calendar: Calendar = .current) -> Kotlinx_datetimeLocalDate {
        let comps = calendar.dateComponents([.year, .month, .day], from: date)
        return Kotlinx_datetimeLocalDate(
            year: Int32(comps.year ?? 1970),
            monthNumber: Int32(comps.month ?? 1),
            dayOfMonth: Int32(comps.day ?? 1)
        )
    }

    /// Convert back to a Swift `Date` at the start of the day (local time).
    var asSwiftDate: Date {
        var c = DateComponents()
        c.year = Int(year)
        c.month = Int(monthNumber)
        c.day = Int(dayOfMonth)
        return Calendar.current.date(from: c) ?? Date()
    }

    /// Mirror of Android's `LocalDate.formatEs()` — `dd/MM/yyyy`.
    var formattedEs: String {
        String(format: "%02d/%02d/%04d", Int(dayOfMonth), Int(monthNumber), Int(year))
    }
}

// MARK: - Categoria display names (mirror of Android's Categoria.displayName())

extension Categoria {
    var displayName: String {
        switch self {
        case .lacteos:    return "Lácteos"
        case .carnes:     return "Carnes"
        case .pescados:   return "Pescados"
        case .frutas:     return "Frutas"
        case .verduras:   return "Verduras"
        case .bebidas:    return "Bebidas"
        case .congelados: return "Congelados"
        case .panaderia:  return "Panadería"
        case .otros:      return "Otros"
        default:          return "Otros"
        }
    }
}

// MARK: - Identifiable conformance for SwiftUI Lists

extension Nevera: Identifiable {}
extension Producto: Identifiable {}

// MARK: - ResultadoInvitacion (sealed) → Swift enum

/// Swift-side mirror of the Kotlin sealed `ResultadoInvitacion` (UC-03b).
///
/// Kotlin/Native exports the sealed interface as an ObjC protocol plus
/// concrete classes, which Swift cannot `switch` over exhaustively — this
/// closed enum restores that guarantee: the mapping below covers all 7
/// Kotlin cases (the Kotlin compiler enforces exhaustiveness on its side)
/// and any future, unmapped case fails SAFE into `.error` instead of
/// crashing.
enum ResultadoInvitacionUI {
    case aceptada(neveraId: String, nombreNevera: String)
    case yaEresMiembro(neveraId: String, nombreNevera: String)
    case noEncontrada
    case expirada
    case yaUsada
    case neveraLlena
    case error(String)

    init(_ kotlin: ResultadoInvitacion) {
        if let r = kotlin as? ResultadoInvitacionAceptada {
            self = .aceptada(neveraId: r.neveraId, nombreNevera: r.nombreNevera)
        } else if let r = kotlin as? ResultadoInvitacionYaEresMiembro {
            self = .yaEresMiembro(neveraId: r.neveraId, nombreNevera: r.nombreNevera)
        } else if kotlin is ResultadoInvitacionNoEncontrada {
            self = .noEncontrada
        } else if kotlin is ResultadoInvitacionExpirada {
            self = .expirada
        } else if kotlin is ResultadoInvitacionYaUsada {
            self = .yaUsada
        } else if kotlin is ResultadoInvitacionNeveraLlena {
            self = .neveraLlena
        } else if let r = kotlin as? ResultadoInvitacionError {
            self = .error(r.mensaje)
        } else {
            self = .error("Resultado de invitación desconocido")
        }
    }
}
