package com.example.foxy.engine

import java.util.concurrent.atomic.AtomicLong

/**
 * GamePhaseDetector — Phát hiện giai đoạn trận đấu Free Fire từ UDP packet stream.
 *
 * Dựa trên research global-metadata.dat (Free Fire 1.132.1):
 *   PlayerState_IDLE        → Sảnh chờ / menu chính
 *   PlayerState_INROOM      → Trong phòng / lobby
 *   PlayerState_MATCHMAKING → Đang tìm trận
 *   PlayerState_ROOMMATCHMAKING → Tìm trận theo phòng
 *   PlayerState_AUTOGROUP   → Auto group matching
 *   PlayerState_LOADING     → Đang tải vào trận
 *   PlayerState_INGAME      → Đang trong trận
 *   PlayerState_INGAME_CAN_RECONNECT → Trong trận (mất kết nối tạm)
 *   PlayerState_WAITING_GS_READY → Chờ game server sẵn sàng
 *   PlayerState_BP          → Battle Points settlement
 *   EMatchPhase_Pregame     → Giai đoạn trước trận
 *   State_INGAME            → Alias của INGAME
 *   State_LOADING           → Alias của LOADING
 *
 * Cơ chế detect từ packet:
 *   - CMD 104 (0x68) = RUDP_START_FIRE → game đang bắn → chắc chắn INGAME
 *   - CMD 109 (0x6D) = RUDP_STOP_FIRE  → game dừng bắn → vẫn INGAME
 *   - CMD 170+ inbound burst → visibility sync (INGAME)
 *   - Silence > 60s trên game port → LOBBY
 *   - CMD 200+ (settlement range) → POST_GAME
 *   - Packet size pattern: INGAME packets thường 40-120 bytes; LOBBY thường 20-40 bytes
 */
object GamePhaseDetector {

    enum class Phase {
        LOBBY,          // Sảnh / menu / phòng chờ
        LOADING,        // Đang load vào trận
        INGAME,         // Đang trong trận (ESP + Aim hoạt động)
        POST_GAME,      // Màn hình kết quả
    }

    // ── Constants từ RUDP research ────────────────────────────
    private const val CMD_START_FIRE          = 104   // RUDP_START_FIRE (0x68) — C2S outbound
    private const val CMD_STOP_FIRE           = 109   // RUDP_STOP_FIRE  (0x6D) — C2S outbound
    private const val CMD_LOADING_END         = 110   // RUDP_C2S_LOADING_END — client xong load
    private const val CMD_VISIBILITY_SYNC     = 170   // RUDP_SYNC_PLAYERS_VISIBILITY — S2C
    private const val CMD_MATCHEND_NOTIFY     = 200   // RUDP_S2C_NOTIFY_FRIENDOB_MATCHEND range

    // Thời gian timeout để xác định phase
    private const val INGAME_TIMEOUT_MS  = 90_000L   // 90s không có game traffic → LOBBY
    private const val LOADING_TIMEOUT_MS = 30_000L   // 30s loading → INGAME (giả sử load xong)

    // ── State ─────────────────────────────────────────────────
    @Volatile private var currentPhase: Phase = Phase.LOBBY
    private val lastGameTrafficMs  = AtomicLong(0L)
    private val lastFireCommandMs  = AtomicLong(0L)
    private val lastLoadingMs      = AtomicLong(0L)
    private val phaseEnterMs       = AtomicLong(System.currentTimeMillis())

    // Đếm packet outbound vs inbound để đánh giá traffic pattern
    @Volatile private var recentGamePackets = 0
    @Volatile private var recentGamePacketsTs = System.currentTimeMillis()

    // ── Public API ────────────────────────────────────────────

    /** Phase hiện tại */
    fun phase(): Phase = currentPhase

    /** Trả về true nếu đang trong trận (ESP + Aim nên hoạt động) */
    fun isIngame(): Boolean = currentPhase == Phase.INGAME

    /**
     * Gọi từ GameProtocolEngine mỗi khi parse được command từ UDP header.
     * @param command  parsed command ID từ UdpSessionParser
     * @param outbound true nếu gói từ client→server
     * @param dataSize kích thước gói để detect traffic pattern
     */
    fun onCommand(command: Int, outbound: Boolean, dataSize: Int) {
        val now = System.currentTimeMillis()

        // Chỉ xét game traffic (port game, không phải DNS/HTTP)
        // Tất cả valid command đều tính là game traffic
        if (command > 0) {
            lastGameTrafficMs.set(now)
            recentGamePackets++
        }

        when {
            // ── FIRE commands → chắc chắn INGAME ──
            command == CMD_START_FIRE || command == CMD_STOP_FIRE -> {
                lastFireCommandMs.set(now)
                transitionTo(Phase.INGAME, now)
            }

            // ── LOADING END từ client → chuyển LOADING→INGAME ──
            outbound && command == CMD_LOADING_END -> {
                transitionTo(Phase.INGAME, now)
            }

            // ── Visibility sync → INGAME ──
            !outbound && command == CMD_VISIBILITY_SYNC -> {
                transitionTo(Phase.INGAME, now)
            }

            // ── Match end notify → POST_GAME ──
            !outbound && command >= CMD_MATCHEND_NOTIFY && command <= CMD_MATCHEND_NOTIFY + 20 -> {
                transitionTo(Phase.POST_GAME, now)
            }

            // ── Traffic heuristic: gói nhỏ outbound thường = lobby heartbeat ──
            outbound && currentPhase == Phase.LOBBY && dataSize < 20 -> {
                // Không làm gì — giữ LOBBY
            }

            // ── Traffic heuristic: gói 40–200 bytes inbound liên tục → INGAME ──
            !outbound && dataSize in 40..200 && command > 50 -> {
                // Nếu đang LOADING và bắt đầu nhận game data → INGAME
                if (currentPhase == Phase.LOADING) {
                    transitionTo(Phase.INGAME, now)
                }
            }
        }

        // ── Auto timeout check ──
        checkTimeouts(now)
    }

    /**
     * Gọi khi VPN session bắt đầu hoặc reset.
     * Trở về LOBBY, xóa tất cả timeout state.
     */
    fun reset() {
        currentPhase = Phase.LOBBY
        val now = System.currentTimeMillis()
        lastGameTrafficMs.set(0L)
        lastFireCommandMs.set(0L)
        lastLoadingMs.set(0L)
        phaseEnterMs.set(now)
        recentGamePackets = 0
        recentGamePacketsTs = now
    }

    /**
     * Gọi khi không có traffic trong một khoảng thời gian (từ cleanup scheduler).
     * Dùng để tự động chuyển INGAME → LOBBY nếu game disconnect.
     */
    fun tick() {
        checkTimeouts(System.currentTimeMillis())
    }

    // ── Internal ──────────────────────────────────────────────

    private fun transitionTo(newPhase: Phase, now: Long) {
        if (currentPhase == newPhase) return
        // POST_GAME → LOBBY OK; INGAME → LOBBY OK; không downgrade INGAME từ POST_GAME ngay
        currentPhase = newPhase
        phaseEnterMs.set(now)
    }

    private fun checkTimeouts(now: Long) {
        when (currentPhase) {
            Phase.INGAME -> {
                val lastTraffic = lastGameTrafficMs.get()
                if (lastTraffic > 0 && now - lastTraffic > INGAME_TIMEOUT_MS) {
                    // Không có game traffic 90s → kết thúc trận hoặc disconnect
                    transitionTo(Phase.LOBBY, now)
                    EspEntityStore.reset()
                }
            }
            Phase.POST_GAME -> {
                // 30s sau kết thúc → trở về LOBBY
                val enterMs = phaseEnterMs.get()
                if (now - enterMs > 30_000L) {
                    transitionTo(Phase.LOBBY, now)
                    EspEntityStore.reset()
                }
            }
            Phase.LOADING -> {
                // Loading quá 30s → giả định INGAME rồi
                val enterMs = phaseEnterMs.get()
                if (now - enterMs > LOADING_TIMEOUT_MS) {
                    transitionTo(Phase.INGAME, now)
                }
            }
            else -> {}
        }
    }
}
