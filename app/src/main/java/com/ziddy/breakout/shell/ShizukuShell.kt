package com.ziddy.breakout.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private var appContext: Context? = null
    private var bound = false
    private var shellService: IShellService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionTimeout = Runnable {
        if (_state.value == ShizukuState.CONNECTING && shellService == null) {
            bound = false
            _state.value = ShizukuState.SERVICE_ERROR
            _connectionError.value = "Timed out while connecting the Shizuku shell service."
        }
    }
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
        mainHandler.removeCallbacks(connectionTimeout)
        _state.value = ShizukuState.NOT_RUNNING
        _connectionError.value = null
        shellService = null
        bound = false
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == REQUEST_CODE) refreshState()
        }

    /** Recompute the current connection state from the live binder. */
    fun refreshState() {
        val authorizationState = try {
            when {
                !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
                Shizuku.isPreV11() -> ShizukuState.NOT_RUNNING
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.CONNECTING
                else -> ShizukuState.NO_PERMISSION
            }
        } catch (error: Exception) {
            _connectionError.value = error.message ?: "Could not check Shizuku permission."
            _state.value = ShizukuState.SERVICE_ERROR
            return
        }

        if (authorizationState != ShizukuState.CONNECTING) {
            mainHandler.removeCallbacks(connectionTimeout)
            _state.value = authorizationState
            _connectionError.value = null
            return
        }

        val service = shellService
        if (service != null && service.asBinder().isBinderAlive) {
            mainHandler.removeCallbacks(connectionTimeout)
            _state.value = ShizukuState.READY
            _connectionError.value = null
            return
        }

        _state.value = ShizukuState.CONNECTING
        _connectionError.value = null
        bindUserService()
    }

    /** Prompt the Shizuku manager for shell permission (no-op if not connected). */
    fun requestPermission() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) {
                refreshState()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshState()
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (error: Exception) {
            _state.value = ShizukuState.SERVICE_ERROR
            _connectionError.value = error.message ?: "Could not request Shizuku permission."
        }
    }

    fun isReady(): Boolean = state.value == ShizukuState.READY

    private fun bindUserService() {
        if (bound) return
        val context = appContext
        if (context == null) {
            _state.value = ShizukuState.SERVICE_ERROR
            _connectionError.value = "Shizuku has not been initialized."
            return
        }
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
            bound = true
            mainHandler.postDelayed(connectionTimeout, 10_000L)
        } catch (error: Exception) {
            mainHandler.removeCallbacks(connectionTimeout)
            bound = false
            _state.value = ShizukuState.SERVICE_ERROR
            _connectionError.value = error.message ?: "Could not bind the shell service."
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mainHandler.removeCallbacks(connectionTimeout)
            if (service != null && service.pingBinder()) {
                shellService = IShellService.Stub.asInterface(service)
                bound = true
                _connectionError.value = null
                _state.value = ShizukuState.READY
            } else {
                shellService = null
                bound = false
                _state.value = ShizukuState.SERVICE_ERROR
                _connectionError.value = "Shizuku returned an invalid shell service connection."
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainHandler.removeCallbacks(connectionTimeout)
            shellService = null
            bound = false
            refreshState()
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
                ?: run {
                    bound = false
                    _state.value = ShizukuState.SERVICE_ERROR
                    _connectionError.value = "Shell service did not connect before the timeout."
                    return ShellResult(-1, "", "Shell service not available", timedOut = true)
                }
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
