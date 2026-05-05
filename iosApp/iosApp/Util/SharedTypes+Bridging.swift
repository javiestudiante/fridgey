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
