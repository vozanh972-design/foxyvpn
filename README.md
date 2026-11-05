# Foxy VPN – Màn hình chính (Kotlin + Jetpack Compose)

Đây là bản code Android của **màn hình chính** trong giao diện mẫu Foxy VPN: quả địa cầu chấm điểm, nút nguồn, thẻ vị trí, tốc độ tải xuống/tải lên, nút 3 gạch và nhãn trạng thái.

> **Lưu ý quan trọng:** đây chỉ là **giao diện mẫu**. Việc "kết nối" hiện chỉ là hoạt ảnh và số liệu giả, **chưa tạo kết nối VPN thật**. Cách nối VPN thật xem mục 7.
> Code được viết theo API Jetpack Compose chuẩn nhưng **chưa được biên dịch thử** trong môi trường tạo file. Nếu Android Studio báo lỗi phiên bản thư viện, xem mục 3.

---

## 1. Bên trong có gì

```
app/src/main/java/com/example/foxy/
├── MainActivity.kt              Điểm vào app, gắn màn chính + hộp chọn vị trí
├── data/
│   └── Server.kt                Danh sách máy chủ, vị trí người dùng, nhóm máy chủ "ở xa"
└── ui/
    ├── theme/
    │   └── Theme.kt             Bảng màu FoxyColors + FoxyTheme
    └── home/
        ├── HomeViewModel.kt     Trạng thái và logic: Idle -> Connecting -> Connected
        ├── HomeScreen.kt        Giao diện màn chính (thanh trên, bảng trắng, nút nguồn)
        ├── Globe.kt             Quả địa cầu: vẽ chấm, xoay, đường cung kéo từ xa về
        └── ServerPicker.kt      Hộp thoại chọn vị trí (tạm thời)
```

## 2. Cách chạy nhanh

1. Mở **Android Studio** (bản mới nhất) -> **New Project** -> chọn **Empty Activity** (loại có Jetpack Compose).
   Đặt `Package name` là `com.example.foxy` (hoặc tên bạn muốn, xem mục 4), **Minimum SDK 24** trở lên.
2. Xoá file `MainActivity.kt` và thư mục `ui/theme` mà Android Studio tạo sẵn.
3. Chép toàn bộ thư mục `com/example/foxy/` trong gói này vào `app/src/main/java/` của project.
4. Thêm thư viện vào `app/build.gradle.kts` (mục 3), bấm **Sync Now**.
5. Chạy trên máy thật hoặc máy ảo (**Run ▶**).
6. Muốn xem nhanh mà không cần chạy máy ảo: mở `HomeScreen.kt`, bật chế độ **Split/Design**, sẽ thấy hai bản xem trước (chưa kết nối và đã kết nối).

## 3. Thư viện cần có

Trong `app/build.gradle.kts`, mục `dependencies`:

```kotlin
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

Các số phiên bản trên chỉ là ví dụ. Nếu Android Studio gợi ý bản mới hơn thì cứ dùng bản mới. Nếu project của bạn dùng Kotlin 2.0 trở lên, cần bật plugin `org.jetbrains.kotlin.plugin.compose` (project mới tạo từ mẫu Compose thường đã có sẵn).

## 4. Đổi tên package

Nếu không dùng `com.example.foxy`: đổi tên thư mục và dòng `package ...` ở đầu mỗi file, các dòng `import com.example.foxy...` cũng đổi theo. Trong Android Studio, chuột phải vào package -> **Refactor -> Rename** sẽ làm tự động.

## 5. Màn hình hoạt động thế nào

**Ba trạng thái** (`ConnectionStatus` trong `HomeViewModel.kt`):

| Trạng thái | Nút nguồn | Chữ dưới nút | Quả địa cầu |
|---|---|---|---|
| `Idle` | Vàng | Nhấn để kết nối | Tự xoay chậm, hướng về vị trí của bạn |
| `Connecting` | Vàng, nhấp nháy | Đang kết nối… | Xoay tới máy chủ xa, rồi đường cung vàng kéo từ máy chủ về phía bạn |
| `Connected` | Xanh lá | Đã kết nối · 00:00:12 | Giữ đường cung, hai đầu hiện cả hai bên |

**Máy chủ ngẫu nhiên ở xa:** khi `isAuto = true` (mặc định), mỗi lần bấm kết nối app bốc ngẫu nhiên một máy chủ trong `ServerRepository.far` (ping từ 100 ms trở lên), không lặp lại máy chủ vừa dùng. Chọn một nước cụ thể trong hộp thoại thì app dùng đúng nước đó.

**Quả địa cầu** (`Globe.kt`): vẽ trực tiếp bằng `Canvas`, không dùng ảnh hay thư viện bản đồ. Lục địa được mô phỏng bằng các hình elip thô trong `landEllipses`. Thời gian và hướng xoay nằm trong hàm `rememberGlobeAnim`.

## 6. Tuỳ chỉnh thường gặp

- **Màu sắc:** sửa trong `FoxyColors` (`Theme.kt`).
- **Danh sách máy chủ:** sửa `ServerRepository.all` (`Server.kt`). Máy chủ ở xa là những máy có `pingMs >= 100`.
- **Vị trí người dùng:** sửa `ServerRepository.userLocation`. Bản thật nên lấy từ địa chỉ IP (dịch vụ định vị theo IP) thay vì để cố định.
- **Thời gian kết nối:** hằng số `CONNECT_DURATION_MS` trong `HomeViewModel.kt` (nên lớn hơn tổng thời gian hoạt ảnh khoảng 2,5 giây trong `Globe.kt`).
- **Độ lớn quả địa cầu / vị trí:** trong `GlobeCanvas`, biến `r` (bán kính, 0.44 chiều rộng) và `cy` (0.36 chiều cao).
- **Font Inter:** tải file `.ttf` bỏ vào `res/font/` (ví dụ `inter_regular.ttf`, `inter_semibold.ttf`, `inter_bold.ttf`), rồi tạo:
  ```kotlin
  val Inter = FontFamily(
      Font(R.font.inter_regular, FontWeight.Normal),
      Font(R.font.inter_semibold, FontWeight.SemiBold),
      Font(R.font.inter_bold, FontWeight.Bold),
  )
  ```
  và đưa vào `Typography(defaultFontFamily...)` hoặc truyền `fontFamily = Inter` cho các `Text`. Không có font này thì app dùng font mặc định của máy.

## 7. Nối VPN thật

Chỗ cần thay nằm ở `HomeViewModel.connect()`, đoạn có chú thích `TODO`. Hiện chỉ là `delay(...)`. Hướng đi thường dùng:

1. Dùng `VpnService` của Android (hoặc thư viện WireGuard / OpenVPN) để dựng đường hầm tới máy chủ `target`.
2. Xin quyền VPN bằng `VpnService.prepare(context)` trước khi kết nối.
3. Khi đường hầm lên thật, mới chuyển `status` sang `Connected`. Nếu lỗi thì quay về `Idle` và báo lỗi cho người dùng.
4. Thay số tải xuống/tải lên giả (`Random.nextDouble`) bằng số đo thật từ `TrafficStats` hoặc từ thư viện VPN.
5. Dữ liệu máy chủ (`ServerRepository`) nên lấy từ API của bạn thay vì viết cứng.

## 8. Chưa có trong gói này

- Màn khởi động (logo + tên), màn nhập key và lưu key.
- Menu 3 gạch (`onMenuClick` hiện để trống) và các màn Cài đặt, Gói của tôi.
- Màn chọn vị trí dạng trang đầy đủ (hiện chỉ có hộp thoại đơn giản `ServerPickerDialog`).

Muốn có các phần này theo đúng giao diện mẫu thì làm tiếp theo cùng cách tổ chức: mỗi màn một thư mục trong `ui/`, trạng thái đặt trong ViewModel riêng.
