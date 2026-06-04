package ule.jescuj00.fridgey.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FridgeyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val PillShape = RoundedCornerShape(999.dp)

// Radios concretos del diseño de neveras (fuera del set M3 Shapes).
val NeveraCardShape = RoundedCornerShape(22.dp)  // tarjeta de nevera
val ShelfShape = RoundedCornerShape(18.dp)       // balda / shelf del detalle
val EmojiIconShape = RoundedCornerShape(14.dp)   // icono emoji de producto
val BackButtonShape = RoundedCornerShape(12.dp)  // botón back/bell cuadrado
