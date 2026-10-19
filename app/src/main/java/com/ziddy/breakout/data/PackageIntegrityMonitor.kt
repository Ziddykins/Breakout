package com.ziddy.breakout.data

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class PackageIntegrityRecord(
    val packageName: String,
    val baselineApkHash: String?,
    val currentApkHash: String?,
    val baselineSignerHash: String?,
    val currentSignerHash: String?,
    val checkedAt: Long,
    val error: String? = null,
) {
    val status: PackageIntegrityStatus
        get() = when {
            error != null -> PackageIntegrityStatus.CHECK_FAILED
            baselineApkHash == null || currentApkHash == null ||
                baselineSignerHash == null || currentSignerHash == null ->
                PackageIntegrityStatus.BASELINE_UNAVAILABLE
            baselineSignerHash != currentSignerHash -> PackageIntegrityStatus.SIGNER_CHANGED
            baselineApkHash != currentApkHash -> PackageIntegrityStatus.APK_CHANGED
            else -> PackageIntegrityStatus.UNCHANGED
        }
}

enum class PackageIntegrityStatus {
    UNCHANGED,
    APK_CHANGED,
    SIGNER_CHANGED,
    BASELINE_UNAVAILABLE,
    CHECK_FAILED,
}

object PackageIntegrityMonitor {
    private const val PREFS_NAME = "breakout_prefs"
    private const val PREF_ENABLED = "package_integrity_monitor_enabled"
    private const val DATA_FILE = "package_integrity_baselines.json"
    private val lock = Any()
    private val _packageUpdates = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val packageUpdates = _packageUpdates.asSharedFlow()

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (enabled) {
            setReceiverEnabled(context, true)
            preferences.edit().putBoolean(PREF_ENABLED, true).apply()
        } else {
            preferences.edit().putBoolean(PREF_ENABLED, false).apply()
            setReceiverEnabled(context, false)
        }
    }

    fun syncReceiverState(context: Context) {
        setReceiverEnabled(context, isEnabled(context))
    }

    suspend fun captureBaseline(context: Context): Map<String, PackageIntegrityRecord> {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val installed = packageManager.getInstalledPackages(0)
        val records = buildMap {
            installed.forEach { info ->
                currentCoroutineContext().ensureActive()
                val packageName = info.packageName
                put(packageName, captureRecord(packageManager, packageName, null))
            }
        }
        saveRecords(context, records)
        return records
    }

    fun observePackageChange(context: Context, packageName: String): PackageIntegrityRecord? {
        if (!isEnabled(context) || !packageName.matches(PACKAGE_NAME)) return null
        synchronized(lock) {
            val records = loadRecords(context).toMutableMap()
            val previous = records[packageName]
            val current = captureRecord(context.packageManager, packageName, previous)
            records[packageName] = current
            writeRecords(context, records)
            _packageUpdates.tryEmit(packageName)
            return current
        }
    }

    fun loadRecords(context: Context): Map<String, PackageIntegrityRecord> = synchronized(lock) {
        val file = File(context.filesDir, DATA_FILE)
        if (!file.exists()) return@synchronized emptyMap()
        val root = JSONObject(file.readText())
        val array = root.optJSONArray("records") ?: JSONArray()
        buildMap {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val record = PackageIntegrityRecord(
                    packageName = item.getString("packageName"),
                    baselineApkHash = item.optNullableString("baselineApkHash"),
                    currentApkHash = item.optNullableString("currentApkHash"),
                    baselineSignerHash = item.optNullableString("baselineSignerHash"),
                    currentSignerHash = item.optNullableString("currentSignerHash"),
                    checkedAt = item.optLong("checkedAt"),
                    error = item.optNullableString("error"),
                )
                put(record.packageName, record)
            }
        }
    }

    private fun captureRecord(
        packageManager: PackageManager,
        packageName: String,
        previous: PackageIntegrityRecord?,
    ): PackageIntegrityRecord {
        val checkedAt = System.currentTimeMillis()
        return try {
            val info = packageInfo(packageManager, packageName)
            val apkHash = hashApkFiles(info)
            val signerHash = hashSigners(info)
            PackageIntegrityRecord(
                packageName = packageName,
                baselineApkHash = previous?.baselineApkHash ?: apkHash,
                currentApkHash = apkHash,
                baselineSignerHash = previous?.baselineSignerHash ?: signerHash,
                currentSignerHash = signerHash,
                checkedAt = checkedAt,
            )
        } catch (error: Exception) {
            PackageIntegrityRecord(
                packageName = packageName,
                baselineApkHash = previous?.baselineApkHash,
                currentApkHash = null,
                baselineSignerHash = previous?.baselineSignerHash,
                currentSignerHash = null,
                checkedAt = checkedAt,
                error = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(packageManager: PackageManager, packageName: String): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return packageManager.getPackageInfo(packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun hashSigners(info: PackageInfo): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        } ?: throw IOException("Package signing certificates are unavailable")
        if (signatures.isEmpty()) throw IOException("Package has no signing certificates")
        return signatures
            .map { sha256(it.toByteArray()) }
            .sorted()
            .joinToString(":")
    }

    private fun hashApkFiles(info: PackageInfo): String {
        val source = info.applicationInfo?.sourceDir
            ?: throw IOException("Package APK path is unavailable")
        val paths = listOf(source) + info.applicationInfo?.splitSourceDirs.orEmpty().sorted()
        val hashes = paths.map { path ->
            FileInputStream(path).use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
                digest.digest().toHex()
            }
        }
        return sha256(hashes.joinToString(":").toByteArray())
    }

    private fun saveRecords(context: Context, records: Map<String, PackageIntegrityRecord>) =
        synchronized(lock) {
            writeRecords(context, records)
        }

    private fun setReceiverEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, PackageChangeReceiver::class.java),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun writeRecords(context: Context, records: Map<String, PackageIntegrityRecord>) {
        val array = JSONArray()
        records.values.sortedBy { it.packageName }.forEach { record ->
            array.put(
                JSONObject()
                    .put("packageName", record.packageName)
                    .put("baselineApkHash", record.baselineApkHash)
                    .put("currentApkHash", record.currentApkHash)
                    .put("baselineSignerHash", record.baselineSignerHash)
                    .put("currentSignerHash", record.currentSignerHash)
                    .put("checkedAt", record.checkedAt)
                    .put("error", record.error),
            )
        }
        val file = File(context.filesDir, DATA_FILE)
        val temporary = File(context.filesDir, "$DATA_FILE.tmp")
        temporary.writeText(JSONObject().put("records", array).toString())
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("Could not save package integrity records")
        }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name) || !has(name)) null else optString(name).takeIf(String::isNotEmpty)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")
}
