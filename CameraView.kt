package com.example.gcamclone

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var previewView: PreviewView
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null

    private var colorFilterMatrix: ColorMatrix? = null
    private val paint = Paint()

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_camera, this, true)
        previewView = view.findViewById(R.id.previewView)
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                context as androidx.lifecycle.LifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("CameraView", "Use case binding failed", e)
        }
    }

    fun takePhoto(
        filterXml: String,
        flashMode: String = "auto",
        cameraFacing: String = "back",
        timerSec: Int = 0,
        zoom: Double = 1.0,
        onImageSaved: (String?) -> Unit
    ) {
        val imageCapture = imageCapture ?: return

        switchCamera(cameraFacing)

        applyFlashMode(flashMode)

        applyZoom(zoom.toFloat())

        colorFilterMatrix = parseFilterMatrixFromXml(filterXml)

        if (timerSec > 0) {
            CoroutineScope(Dispatchers.Main).launch {
                delay(timerSec * 1000L)
                capturePhoto(imageCapture, onImageSaved)
            }
        } else {
            capturePhoto(imageCapture, onImageSaved)
        }
    }

    private fun capturePhoto(imageCapture: ImageCapture, onImageSaved: (String?) -> Unit) {
        val photoFile = createFile(context)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // For simplicity, no filter post processing on file here
                    onImageSaved(photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraView", "Photo capture failed: ${exception.message}", exception)
                    onImageSaved(null)
                }
            })
    }

    private fun applyFlashMode(mode: String) {
        camera?.cameraControl?.enableTorch(false)
        when (mode.lowercase()) {
            "on" -> camera?.cameraControl?.enableTorch(true)
            "off" -> camera?.cameraControl?.enableTorch(false)
            "auto" -> {
                // CameraX belum support auto flash
                camera?.cameraControl?.enableTorch(false)
            }
        }
    }

    private fun switchCamera(facing: String) {
        val selector = when (facing) {
            "front" -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        cameraProvider?.unbindAll()
        try {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            camera = cameraProvider?.bindToLifecycle(
                context as androidx.lifecycle.LifecycleOwner,
                selector,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Log.e("CameraView", "Switch camera failed", e)
        }
    }

    private fun applyZoom(zoom: Float) {
        // zoom linear 0f..1f scale
        val linearZoom = ((zoom - 1) / 3f).coerceIn(0f, 1f)
        camera?.cameraControl?.setLinearZoom(linearZoom)
    }

    private fun parseFilterMatrixFromXml(xml: String): ColorMatrix? {
        val matrix = ColorMatrix()
        var brightness = 1.0f
        var contrast = 1.0f
        var saturation = 1.0f
        var sepia = false

        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "filter") {
                    val name = parser.getAttributeValue(null, "name")
                    val value = parser.getAttributeValue(null, "value")
                    when (name) {
                        "brightness" -> brightness = value.toFloatOrNull() ?: 1.0f
                        "contrast" -> contrast = value.toFloatOrNull() ?: 1.0f
                        "saturation" -> saturation = value.toFloatOrNull() ?: 1.0f
                        "sepia" -> sepia = value == "true"
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("CameraView", "XML parse error: ${e.message}")
        }

        matrix.setSaturation(saturation)

        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(contrastMatrix)

        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                brightness, 0f, 0f, 0f, 0f,
                0f, brightness, 0f, 0f, 0f,
                0f, 0f, brightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        matrix.postConcat(brightnessMatrix)

        if (sepia) {
            val sepiaMatrix = ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.postConcat(sepiaMatrix)
        }

        return matrix
    }

    private fun createFile(context: Context): File {
        val dir = context.getExternalFilesDir(null)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "IMG_$timeStamp.jpg")
    }
}
