package com.example.foxy.data

data class GeoPoint(val lat: Double, val lon: Double)

data class Server(
    val flag: String,
    val country: String,
    val city: String,
    val location: GeoPoint,
    val pingMs: Int,
)

object ServerRepository {
    /** Vị trí giả định của người dùng (TP. Hồ Chí Minh). Thay bằng vị trí thật nếu cần. */
    val userLocation = GeoPoint(10.8, 106.7)

    val all = listOf(
        Server("🇸🇬", "Singapore", "Singapore", GeoPoint(1.3, 103.8), 18),
        Server("🇻🇳", "Việt Nam", "TP. Hồ Chí Minh", GeoPoint(10.8, 106.7), 8),
        Server("🇯🇵", "Nhật Bản", "Tokyo", GeoPoint(35.7, 139.7), 62),
        Server("🇰🇷", "Hàn Quốc", "Seoul", GeoPoint(37.5, 127.0), 74),
        Server("🇭🇰", "Hồng Kông", "Hồng Kông", GeoPoint(22.3, 114.2), 40),
        Server("🇹🇭", "Thái Lan", "Bangkok", GeoPoint(13.7, 100.5), 28),
        Server("🇦🇺", "Úc", "Sydney", GeoPoint(-33.9, 151.2), 120),
        Server("🇺🇸", "Hoa Kỳ", "Los Angeles", GeoPoint(34.0, -118.2), 168),
        Server("🇬🇧", "Anh", "London", GeoPoint(51.5, -0.1), 210),
        Server("🇩🇪", "Đức", "Frankfurt", GeoPoint(50.1, 8.7), 196),
        Server("🇧🇷", "Brazil", "São Paulo", GeoPoint(-23.5, -46.6), 260),
    )

    /** Máy chủ ở xa, dùng cho chế độ "Ngẫu nhiên". */
    val far = all.filter { it.pingMs >= 100 }
}
