package com.d4viddf.hyperbridge.screenrecorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

object ScreenRecorderCommands {
    suspend fun start(context: Context, options: Bundle? = null): Result<Unit> =
        send(context, ScreenRecorderContract.MSG_COMMAND_START, options)

    suspend fun pause(context: Context): Result<Unit> =
        send(context, ScreenRecorderContract.MSG_COMMAND_PAUSE)

    suspend fun resume(context: Context): Result<Unit> =
        send(context, ScreenRecorderContract.MSG_COMMAND_RESUME)

    suspend fun stop(context: Context): Result<Unit> =
        send(context, ScreenRecorderContract.MSG_COMMAND_STOP)

    private suspend fun send(context: Context, command: Int, extras: Bundle? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(1_500L) {
                    val app = context.applicationContext
                    val deferred = CompletableDeferred<IBinder>()
                    val closed = AtomicBoolean(false)
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            deferred.complete(service)
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            if (!deferred.isCompleted) {
                                deferred.completeExceptionally(IllegalStateException("Recorder control disconnected"))
                            }
                        }
                    }
                    val intent = Intent().setClassName(
                        ScreenRecorderContract.MODULE_PACKAGE,
                        ScreenRecorderContract.CONTROL_SERVICE_CLASS,
                    )
                    check(app.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                        "Unable to bind screen recorder control service"
                    }
                    try {
                        val messenger = Messenger(deferred.await())
                        messenger.send(Message.obtain(null, command).apply {
                            if (extras != null) data = extras
                        })
                    } finally {
                        if (closed.compareAndSet(false, true)) {
                            runCatching { app.unbindService(connection) }
                        }
                    }
                }
            }
        }
}
