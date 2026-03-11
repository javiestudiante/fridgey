package ule.jescuj00.fridgey

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform