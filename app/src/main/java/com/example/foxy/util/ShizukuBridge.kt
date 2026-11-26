package com.example.foxy.util

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * ShizukuBridge — dùng Shizuku để bypass anti-cheat Free Fire.
 *
 * Chức năng:
 *  1. Kiểm tra Shizuku đã sẵn sàng chưa
 *  2. Grant SYSTEM_ALERT_WINDOW + VPN không cần dialog
 *  3. Kill FF anti-cheat watcher process (garena_guard / ff_anti)
 *  4. Mount /proc/<pid>/mem readable để BoneMemReader đọc bones
 *  5. Disable SELinux enforce tạm thời (cần root Shizuku)
 */
object ShizukuBridge {

    private const val TAG = "ShizukuBridge"
    private const val REQUEST_CODE = 42

    @Volatile var isReady = false
        private set

    // ─── Listener Shizuku bind/unbind ─────────────────────────
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isReady = false
        Log.w(TAG, "Shizuku binder died")
    }
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        isReady = true
        Log.i(TAG, "Shizuku binder received — version ${Shizuku.getVersion()}")
        // Auto grant permissions ngay khi connect
        runCatching { grantSelfPermissions() }
        runCatching { killAntiCheat() }
    }

    // ─── Init: gọi từ Application.onCreate hoặc MainActivity ──
    fun init() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Log.i(TAG, "ShizukuBridge initialized")
    }

    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    // ─── Check permission ──────────────────────────────────────
    fun checkAndRequestPermission(): Boolean {
        if (!isReady) return false
        return try {
            if (Shizuku.isPreV11()) {
                // Shizuku < 11: dùng provider
                false
            } else {
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Shizuku.requestPermission(REQUEST_CODE)
                }
                granted
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkPermission: $e")
            false
        }
    }

    // ─── Grant SYSTEM_ALERT_WINDOW + VPN cho chính app ────────
    fun grantSelfPermissions() {
        if (!hasPermission()) return
        val pkg = "com.example.foxy"
        runShell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        Log.i(TAG, "Granted SYSTEM_ALERT_WINDOW via Shizuku")
    }

    // ─── Kill anti-cheat process của Free Fire ─────────────────
    // FF dùng garena_guard hoặc thread riêng để detect injection
    fun killAntiCheat(): Boolean {
        if (!hasPermission()) return false
        return try {
            // Tìm và kill các process anti-cheat
            val antiCheatNames = listOf(
                "garena_guard",
                "ff_antichat",
                "com.dts.freefireth:ACE",
                "com.dts.freefiremax:ACE",
            )
            var killed = false
            for (name in antiCheatNames) {
                val result = runShell("pkill -f $name 2>/dev/null; echo ok")
                Log.i(TAG, "Kill $name: $result")
                killed = true
            }
            // Disable ptrace protection — cho phép /proc/pid/mem đọc được
            runShell("echo 0 > /proc/sys/kernel/yama/ptrace_scope")
            Log.i(TAG, "ptrace_scope set to 0")
            killed
        } catch (e: Exception) {
            Log.e(TAG, "killAntiCheat: $e")
            false
        }
    }

    // ─── Set ptrace scope = 0 để đọc /proc/pid/mem ────────────
    fun enableProcMem(): Boolean {
        if (!hasPermission()) return false
        return try {
            runShell("echo 0 > /proc/sys/kernel/yama/ptrace_scope")
            Log.i(TAG, "proc/mem reading enabled")
            true
        } catch (e: Exception) {
            Log.e(TAG, "enableProcMem: $e")
            false
        }
    }

    // ─── Lấy PID của Free Fire (tiện cho BoneMemReader) ───────
    fun getFreefirePid(): Int {
        if (!hasPermission()) return -1
        return try {
            val out = runShell("pidof com.dts.freefireth 2>/dev/null || pidof com.dts.freefiremax 2>/dev/null")
            out.trim().split("\\s+".toRegex()).firstOrNull()?.toIntOrNull() ?: -1
        } catch (e: Exception) { -1 }
    }

    // ─── Thực thi lệnh shell qua Shizuku ──────────────────────
    fun runShell(cmd: String): String {
        return try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(
                arrayOf("sh", "-c", cmd), null, null
            )
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            out.trim()
        } catch (e: Exception) {
            Log.e(TAG, "runShell '$cmd': $e")
            ""
        }
    }

    // ─── Helpers ───────────────────────────────────────────────
    private fun hasPermission(): Boolean {
        if (!isReady) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }
}
