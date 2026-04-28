package ule.jescuj00.fridgey.ui.util

import kotlinx.datetime.LocalDate
import ule.jescuj00.fridgey.domain.model.Categoria

fun Categoria.displayName(): String = when (this) {
    Categoria.LACTEOS -> "Lácteos"
    Categoria.CARNES -> "Carnes"
    Categoria.PESCADOS -> "Pescados"
    Categoria.FRUTAS -> "Frutas"
    Categoria.VERDURAS -> "Verduras"
    Categoria.BEBIDAS -> "Bebidas"
    Categoria.CONGELADOS -> "Congelados"
    Categoria.PANADERIA -> "Panadería"
    Categoria.OTROS -> "Otros"
}

fun LocalDate.formatEs(): String =
    "${dayOfMonth.toString().padStart(2, '0')}/${monthNumber.toString().padStart(2, '0')}/$year"
