/*
 *  RadioPlayerPlugin.kt
 *
 *  Created by Ilya Chirkunov <xc@yar.net> on 28.12.2020.
 */

package com.cheebeez.radio_player

import androidx.annotation.NonNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.ComponentName
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.os.IBinder
import com.google.android.exoplayer2.util.Util

/** RadioPlayerPlugin */
class RadioPlayerPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var context: Context
    private lateinit var channel: MethodChannel
    private lateinit var stateChannel: EventChannel
    private lateinit var metadataChannel: EventChannel
    private lateinit var intent: Intent
    private lateinit var service: RadioPlayerService

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "radio_player")
        channel.setMethodCallHandler(this)

        stateChannel = EventChannel(flutterPluginBinding.binaryMessenger, "radio_player/stateEvents")
        stateChannel.setStreamHandler(stateStreamHandler)
        metadataChannel = EventChannel(flutterPluginBinding.binaryMessenger, "radio_player/metadataEvents")
        metadataChannel.setStreamHandler(metadataStreamHandler)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        context.unbindService(serviceConnection)
        context.stopService(intent)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        val args = call.arguments<ArrayList<*>>()

        when (call.method) {
            "init" -> {
                intent = Intent(context, RadioPlayerService::class.java)
                intent.apply {
                    putExtra(RadioPlayerService.STREAM_TITLE, args[0] as String)
                    putExtra(RadioPlayerService.STREAM_URL, args[1] as String)
                }
                context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Util.startForegroundService(context, intent)
            }
            "play" -> {
                service.play()
            }
            "pause" -> {
                service.pause()
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val binder = iBinder as RadioPlayerService.LocalBinder
            service = binder.getService()
        }

        // Called when the connection with the service disconnects unexpectedly.
        // The service should be running in a different process.
        override fun onServiceDisconnected(componentName: ComponentName) {
        }
    }

    /** Handler for playback state changes, passed to setStreamHandler() */
    private var stateStreamHandler = object : StreamHandler {
        private var eventSink: EventSink? = null

        override fun onListen(arguments: Any?, events: EventSink?) {
            eventSink = events
            LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver, 
                    IntentFilter(RadioPlayerService.ACTION_STATE_CHANGED))
        }

        override fun onCancel(arguments: Any?) {
            eventSink = null
            LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
        }

        // Broadcast receiver for playback state changes, passed to registerReceiver()
        private var broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    val received = intent.getBooleanExtra(RadioPlayerService.ACTION_STATE_CHANGED_EXTRA, false)
                    eventSink?.success(received)
                }
            }
        }
    }

    /** Handler for new metadata, passed to setStreamHandler() */
    private var metadataStreamHandler = object : StreamHandler {
        private var eventSink: EventSink? = null

        override fun onListen(arguments: Any?, events: EventSink?) {
            eventSink = events
            LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver, 
                    IntentFilter(RadioPlayerService.ACTION_NEW_METADATA))
        }

        override fun onCancel(arguments: Any?) {
            eventSink = null
            LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver)
        }

        // Broadcast receiver for new metadata, passed to registerReceiver()
        private var broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent != null) {
                    val received = intent.getStringArrayListExtra(RadioPlayerService.ACTION_NEW_METADATA_EXTRA)
                    eventSink?.success(received)
                }
            }
        }
    }
}
