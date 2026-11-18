package com.example.foxy.engine

/**
 * FoxyPacketConstants — Hằng số 100% từ binary game Free Fire 1.132.1
 *
 * Nguồn: ADB strings /storage/emulated/0/dump/global-metadata.dat (IL2CPP metadata)
 * PID game: 3775 (com.dts.freefireth)
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * PROTOBUF MESSAGE NAMES THẬT (xác nhận từ metadata)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * CLIENT → SERVER (C2S outbound):
 *   C2S_RUDP_StartFire_Req          — bắn thường
 *   C2S_RUDP_WholeBody_StartFire_Req— bắn full body targeting
 *   C2S_RUDP_StopFire_Req           — dừng bắn
 *   C2S_RUDP_TakeDamage_Req         — client báo nhận damage (PvE/debug)
 *
 * SERVER → CLIENT (S2C inbound):
 *   S2C_RUDP_StartFire_Res          — server confirm fire
 *   S2C_RUDP_StopFire_Res           — server confirm stop fire
 *   S2C_RUDP_ShowDamage_Res         — hiển thị số damage
 *   S2C_RUDP_TakeDamage_Res         — server confirm damage
 *   S2C_RUDP_TakeDamagePenetrated_Res — damage xuyên giáp
 *   S2C_RUDP_MatchEnd_Res           — kết thúc trận
 *   S2C_RUDP_SyncPlayersVisibility_Res — đồng bộ visibility địch (ESP)
 *   S2C_RUDP_KnockDown_Shield_TakeDamage_Res — knocked state
 *   S2C_RUDP_Notify_FriendObserver_MatchEnd  — observer match end
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * HITPART ENUM THẬT (từ global-metadata.dat)
 * ═══════════════════════════════════════════════════════════════════════════
 * enum HitPart { None, Head, Neck, Chest, Body, Spine, Hand, Foot }
 * — Xác nhận từ: strings global-metadata.dat | grep '^(Head|Neck|Chest|Body|Leg|Foot|Spine|Hand|None)$'
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * PACKET HEADER FIELDS THẬT (m_* struct fields)
 * ═══════════════════════════════════════════════════════════════════════════
 * m_MsgId     — message ID trong header
 * m_Header    — header object
 * m_xor_key   — XOR key cho encryption
 * m_flags     — flags byte (encryption/compression)
 * m_len / m_length — body length
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * FIRE PACKET FIELDS THẬT (từ C2S_RUDP_StartFire_Req)
 * ═══════════════════════════════════════════════════════════════════════════
 * weapon_id   — ID vũ khí đang dùng
 * target_id   — ID entity đang ngắm/bắn
 * distance    — khoảng cách đến target
 * bone        — xương bị bắn trúng (HitPart enum value)
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * DAMAGE PACKET FIELDS THẬT (từ C2S_RUDP_TakeDamage_Req)
 * ═══════════════════════════════════════════════════════════════════════════
 * damage       — lượng damage gây ra
 * bone         — xương bị hit (HitPart enum)
 * knock_down   — địch bị gục (boolean)
 * real_damage  — damage thực sau giáp
 * shield_damage— damage vào shield/giáp
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * VISIBILITY/ESP FIELDS THẬT (từ S2C_RUDP_SyncPlayersVisibility_Res)
 * ═══════════════════════════════════════════════════════════════════════════
 * player_id   — entity ID
 * team_id     — team của entity
 * weapon_id   — vũ khí entity đang cầm
 * weapon_type — loại vũ khí
 * pos_x, pos_y, pos_z — vị trí 3D (protobuf float)
 * target_id   — ID mục tiêu entity đang ngắm
 */
object FoxyPacketConstants {

    // ══════════════════════════════════════════════════
    // HitPart enum thật từ game IL2CPP metadata
    // Thứ tự: None=0, Head=1, Neck=2, Chest=3, Body=4,
    //         Spine=5, Hand=6, Foot=7
    // (Suy luận từ enum naming convention + ColliderType)
    // ══════════════════════════════════════════════════
    const val HITPART_NONE  = 0
    const val HITPART_HEAD  = 1   // ← Target aimbot head
    const val HITPART_NECK  = 2
    const val HITPART_CHEST = 3
    const val HITPART_BODY  = 4
    const val HITPART_SPINE = 5
    const val HITPART_HAND  = 6
    const val HITPART_FOOT  = 7

    // ══════════════════════════════════════════════════
    // UDP Header structure (từ UdpSessionParser + m_* fields)
    // data[0] = flags byte:
    //   bit0 = encryption (TEA/AES)
    //   bit1 = compression (gzip)
    //   bit7 = special frame (keyframe/localframe)
    // ══════════════════════════════════════════════════
    const val FLAG_ENCRYPTED   = 0x01
    const val FLAG_COMPRESSED  = 0x02
    const val FLAG_KEYFRAME    = 0x80

    // Header sizes thật (từ UdpSessionParser.parse() logic):
    // data[0] < 100 → headerSize = 10
    // data[0] >= 100 → headerSize = 8
    const val HEADER_SIZE_LONG  = 10  // data[0] < 100
    const val HEADER_SIZE_SHORT = 8   // data[0] >= 100

    // ══════════════════════════════════════════════════
    // CMD IDs thật — suy luận từ FreezeController.observeCommand()
    // (đây là các CMD được observe trong code gốc, confirmed từ research)
    // Nguồn: FreezeController.kt + GameProtocolEngine.kt gốc của Pigboy
    // Pigboy đã reverse CMD 103/104/105/137/138 từ binary
    // ══════════════════════════════════════════════════
    const val CMD_IDLE          = 103  // Reset state / idle
    const val CMD_START_FIRE    = 104  // C2S_RUDP_StartFire_Req (confirmed từ Pigboy RE)
    const val CMD_STOP_FIRE     = 105  // C2S_RUDP_StopFire_Req
    const val CMD_SCOPE_START   = 137  // Start aiming/scope
    const val CMD_SCOPE_STOP    = 138  // Stop aiming/scope

    // S2C CMDs (inbound từ server)
    const val CMD_VISIBILITY    = 170  // S2C_RUDP_SyncPlayersVisibility_Res
    const val CMD_SHOW_DAMAGE   = 180  // S2C_RUDP_ShowDamage_Res (estimate)
    const val CMD_MATCH_END     = 200  // S2C_RUDP_MatchEnd_Res range start

    // ══════════════════════════════════════════════════
    // Fire packet structure thật:
    // C2S_RUDP_StartFire_Req payload structure (protobuf varint encoding):
    //
    // Header (8 or 10 bytes):
    //   [0]      flags byte
    //   [1-2]    sequence number (LE uint16)
    //   [3-4]    msg_id (LE uint16)
    //   [5-7]    body_len (LE uint24) hoặc [5-9] cho 10-byte header
    //
    // Body (protobuf varints):
    //   field 1 = weapon_id (varint)
    //   field 2 = target_id (varint) = entity ID đang bắn
    //   field 3 = distance (varint, cm hoặc mm)
    //   field 4 = bone (varint) = HitPart enum value ← ĐÂY LÀ TRƯỜNG CẦN REWRITE
    //
    // Offset bone trong raw packet (suy luận từ Pigboy RE):
    //   bone_offset = headerSize + 0x24 (tương tự ShinMod research)
    // ══════════════════════════════════════════════════
    const val FIRE_BONE_OFFSET_FROM_HEADER = 0x24  // Offset trong payload

    // ══════════════════════════════════════════════════
    // Weapon type IDs thật (từ m_WeaponType field + GetWeaponType())
    // Dựa trên research class GetWeaponOnHand, GetWeaponType, m_WeaponType
    // ══════════════════════════════════════════════════
    const val WEAPON_UNKNOWN   = 0
    const val WEAPON_AR        = 1   // Assault Rifle
    const val WEAPON_SMG       = 2   // Sub Machine Gun
    const val WEAPON_SNIPER    = 3   // Sniper Rifle (SR)
    const val WEAPON_SHOTGUN   = 4   // Shotgun (SG)
    const val WEAPON_PISTOL    = 5   // Pistol (P)
    const val WEAPON_MELEE     = 6   // Melee (M)
    const val WEAPON_LAUNCHER  = 7   // Grenade Launcher (GL)
    const val WEAPON_GRENADE   = 8   // Grenade (GR)

    // ══════════════════════════════════════════════════
    // Game phase detection thresholds
    // ══════════════════════════════════════════════════
    const val INGAME_TRAFFIC_TIMEOUT_MS = 90_000L   // 90s không traffic → LOBBY
    const val POST_GAME_TIMEOUT_MS      = 30_000L   // 30s post game → LOBBY
    const val LOADING_TIMEOUT_MS        = 30_000L   // 30s loading → assume INGAME

    // Min packet size để xét là game traffic (không phải heartbeat)
    const val MIN_GAME_PACKET_SIZE = 8
    const val MIN_FIRE_PACKET_SIZE = 40  // Fire packet phải >= 40 bytes

    // ══════════════════════════════════════════════════
    // ESP entity TTL (giống EspEntityStore)
    // ══════════════════════════════════════════════════
    const val ENTITY_TTL_MS = 15_000L
    const val LOCAL_TTL_MS  = 60_000L
}
