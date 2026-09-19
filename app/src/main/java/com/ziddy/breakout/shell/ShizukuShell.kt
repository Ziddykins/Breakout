package com.ziddy.breakout.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.ziddy.breakout.model.ShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Bridge to the Shizuku server. Binds an [IShellService] user service that
 * executes shell commands with ADB (shell uid) privileges, without requiring
 * a PC connection.
 */
object ShizukuShell {

    private const val REQUEST_CODE = 4213
    private const val SERVICE_VERSION = 1

    private val _state = MutableStateFlow(ShizukuState.NOT_RUNNING)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var bound = false
    private var shellService: IShellService? = null
    private lateinit var serviceArgs: Shizuku.UserServiceArgs

    /** Attach lifecycle listeners. Called once from [com.ziddy.breakout.BreakoutApp]. */
    fun init(context: Context) {
        appContext = context.applicationContext
        serviceArgs = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShellService::class.java.name),
        )
            .processNameSuffix("shell")
            .version(SERVICE_VERSION)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _state.value = ShizukuState.NOT_RUNNING
        shellService = null
        bound = false
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == REQUEST_CODE) refreshState()
        }

    /** Recompute the current connection state from the live binder. */
    fun refreshState() {
        _state.value = runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
                Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
                else -> ShizukuState.NO_PERMISSION
            }
        }.getOrDefault(ShizukuState.NOT_RUNNING)
        if (_state.value == ShizukuState.READY) bindUserService()
    }

    /** Prompt the Shizuku manager for shell permission (no-op if not connected). */
    fun requestPermission() {
        runCatching {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshState()
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        }
    }

    fun isReady(): Boolean = state.value == ShizukuState.READY

    private fun bindUserService() {
        if (bound) return
        val context = appContext ?: return
        runCatching {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
            bound = true
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                shellService = IShellService.Stub.asInterface(service)
            } else {
                shellService = null
                bound = false
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            bound = false
        }
    }

    data class ShellResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val timedOut: Boolean,
    )

    /**
     * Run a shell command through the Shizuku user service as the shell user.
     * Blocking — call from a background dispatcher only.
     */
    fun exec(command: String, timeoutMs: Long = 25_000L): ShellResult {
        return try {
            if (state.value != ShizukuState.READY) {
                return ShellResult(-1, "", "Shizuku not connected", timedOut = false)
            }
            bindUserService()
            val service = awaitService(timeoutMs)
                ?: return ShellResult(-1, "", "Shell service not available", timedOut = false)
            val outcome = ShellProtocol.parse(service.exec(command))
            ShellResult(outcome.exitCode, outcome.stdout, outcome.stderr, timedOut = false)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Shell error", timedOut = false)
        }
    }

    private fun awaitService(timeoutMs: Long): IShellService? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (shellService == null && System.currentTimeMillis() < deadline) {
            bindUserService()
            Thread.sleep(150)
        }
        return shellService
    }
}
