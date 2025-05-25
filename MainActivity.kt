package com.example.gcamclone

import android.os.Bundle
import android.view.View
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.flutter.plugin.common.StandardMessageCodec

class MainActivity : FlutterActivity() {
    private val CHANNEL = "gcam_clone/camera"
    private lateinit var cameraView: CameraView

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        cameraView = CameraView(this)

        flutterEngine
            .platformViewsController
            .registry
            .registerViewFactory("gcam_clone/preview", object : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
                override fun create(context: android.content.Context?, viewId: Int, args: Any?): PlatformView {
                    return object : PlatformView {
                        override fun getView(): View = cameraView
                        override fun dispose() {}
                    }
                }
            })

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "takePhoto" -> {
                    val filterXml = call.argument<String>("filterXml") ?: ""
                    val flashMode = call.argument<String>("flashMode") ?: "auto"
                    val cameraFacing = call.argument<String>("cameraFacing") ?: "back"
                    val timerSec = call.argument<Int>("timer") ?: 0
                    val zoom = call.argument<Double>("zoom") ?: 1.0

                    cameraView.takePhoto(
                        filterXml,
                        flashMode,
                        cameraFacing,
                        timerSec,
                        zoom
                    ) { path ->
                        if (path != null) {
                            result.success(path)
                        } else {
                            result.error("ERROR", "Failed to capture photo", null)
                        }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }
}
