package com.example.foxy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.UUID

/**
 * DocumentFolderManager — Chuyển đổi và chuẩn hóa từ FolderAccess (STIRLAX).
 *
 * Cung cấp giải pháp chuẩn Android Storage Access Framework (SAF):
 * - Kiểm tra quyền truy cập thư mục lưu trữ vĩnh viễn (Persistable URI Permission).
 * - Kiểm tra tính khả ghi thực tế (Writable check) bằng file kiểm thử tạm thời.
 * - Hỗ trợ quản lý dữ liệu an toàn tương thích từ Android 8 đến Android 15.
 */
class DocumentFolderManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "foxy_folder_prefs"
        private const val KEY_SAVED_URI = "saved_document_tree_uri"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Tạo Intent mở trình chọn thư mục chuẩn Android SAF (ACTION_OPEN_DOCUMENT_TREE).
     */
    fun createOpenFolderIntent(initialPath: String? = null): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !initialPath.isNullOrEmpty()) {
                val uri = Uri.parse("content://com.android.externalstorage.documents/document/$initialPath")
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            }
        }
    }

    /**
     * Lưu và bảo lưu quyền truy cập thư mục lâu dài (Persistable URI Permission).
     */
    fun persistFolderPermission(treeUri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            prefs.edit().putString(KEY_SAVED_URI, treeUri.toString()).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Lấy URI thư mục đã lưu trước đó nếu quyền vẫn còn hiệu lực.
     */
    fun getPersistedFolderUri(): Uri? {
        val uriString = prefs.getString(KEY_SAVED_URI, null) ?: return null
        val uri = Uri.parse(uriString)
        val hasPerm = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        return if (hasPerm) uri else null
    }

    /**
     * Kiểm tra tính khả ghi (writable check) tương tự FolderAccess.writable() của STIRLAX
     * bằng cách tạo một tệp tạm thời và xóa ngay lập tức.
     */
    fun isFolderWritable(treeUri: Uri): Boolean {
        return try {
            val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            if (!dir.canWrite()) return false

            val testFileName = ".foxy-check-${UUID.randomUUID()}.tmp"
            val testFile = dir.createFile("application/octet-stream", testFileName) ?: return false
            val canWrite = testFile.canWrite()
            testFile.delete()
            canWrite
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Xóa cấu hình quyền truy cập thư mục khi người dùng reset.
     */
    fun clearPersistedFolder() {
        val saved = prefs.getString(KEY_SAVED_URI, null)
        if (saved != null) {
            try {
                val uri = Uri.parse(saved)
                val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
            } catch (_: Exception) {
                // Ignore
            }
        }
        prefs.edit().remove(KEY_SAVED_URI).apply()
    }
}
