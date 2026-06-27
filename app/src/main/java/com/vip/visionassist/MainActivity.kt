package com.vip.visionassist

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var yoloDetector: YoloDetector
    private lateinit var depthEstimator: DepthEstimator
    private lateinit var navEngine: NavigationEngine
    private lateinit var voiceEngine: VoiceEngine

    private lateinit var previewView: PreviewView
    private lateinit var boundingBoxView: BoundingBoxView
    private lateinit var tvStatus: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvBattery: TextView
    private lateinit var scanLine: View

    private lateinit var zoneNav: FrameLayout
    private lateinit var zoneAsk: FrameLayout
    private lateinit var zoneDescribe: FrameLayout
    private lateinit var zoneStandby: FrameLayout

    private var currentMode = "STANDBY"
    private var isProcessing = false
    private var lastFrameWidth = 320
    private var lastFrameHeight = 320
    private var currentBatteryPct = -1
    private var isCharging = false
    private var lastDetections: List<YoloDetector.Detection> = emptyList()
    private var speechRecognizer: SpeechRecognizer? = null

    private val modeCycle = listOf("NAV", "ASK", "DESCRIBE", "STANDBY")
    private var modeIndex = 3

    private val cocoLabels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    companion object {
        private const val REQUEST_CODE = 100
        private const val REQUEST_AUDIO = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView     = findViewById(R.id.previewView)
        boundingBoxView = findViewById(R.id.boundingBoxView)
        tvStatus        = findViewById(R.id.tvStatus)
        tvMode          = findViewById(R.id.tvMode)
        tvBattery       = findViewById(R.id.tvBattery)
        scanLine        = findViewById(R.id.scanLine)
        zoneNav         = findViewById(R.id.zoneNav)
        zoneAsk         = findViewById(R.id.zoneAsk)
        zoneDescribe    = findViewById(R.id.zoneDescribe)
        zoneStandby     = findViewById(R.id.zoneStandby)

        yoloDetector   = YoloDetector(this)
        depthEstimator = DepthEstimator(this)
        navEngine      = NavigationEngine()
        voiceEngine    = VoiceEngine(this)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startScanLineAnimation()
        startStatusBlink()
        setupTouchZones()
        setupDoubleTap()
        setupBatteryMonitor()

        tvBattery.setOnClickListener { speakBattery() }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE
            )
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO
            )
        }

        voiceEngine.speak(
            "Vision Assist ready. Top left navigate, top right find object, " +
                    "bottom left describe scene, bottom right standby."
        )
    }

    // Scan line sweeping animation
    private fun startScanLineAnimation() {
        scanLine.post {
            val screenWidth = scanLine.width.toFloat()
            val animator = ObjectAnimator.ofFloat(
                scanLine, "translationX", -screenWidth, screenWidth
            )
            animator.duration = 2500
            animator.repeatCount = ValueAnimator.INFINITE
            animator.interpolator = LinearInterpolator()
            animator.start()
        }
    }

    // Status text blink animation
    private fun startStatusBlink() {
        val animator = ObjectAnimator.ofFloat(tvStatus, "alpha", 1f, 0.6f)
        animator.duration = 1200
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.start()
    }

    // Zone tap animation — scale + glow
    private fun animateZoneTap(zone: FrameLayout) {
        zone.animate()
            .scaleX(0.93f)
            .scaleY(0.93f)
            .setDuration(80)
            .withEndAction {
                zone.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()

        // Flash all TextViews green then back
        flashZoneGreen(zone)
    }

    private fun flashZoneGreen(zone: FrameLayout) {
        val labels = mutableListOf<TextView>()
        for (i in 0 until zone.childCount) {
            val child = zone.getChildAt(i)
            if (child is TextView) labels.add(child)
            if (child is android.widget.LinearLayout) {
                for (j in 0 until child.childCount) {
                    val sub = child.getChildAt(j)
                    if (sub is TextView) labels.add(sub)
                }
            }
        }
        labels.forEach { it.setTextColor(0xFF00E676.toInt()) }
        zone.postDelayed({
            labels.forEach { it.setTextColor(0xFFFFFFFF.toInt()) }
        }, 600)
    }

    private fun setupDoubleTap() {
        val gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    modeIndex = (modeIndex + 1) % modeCycle.size
                    setMode(modeCycle[modeIndex])
                    voiceEngine.vibrate(150)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    voiceEngine.speak(tvStatus.text.toString(), force = true)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    voiceEngine.speak(
                        "Vision Assist. Current mode: $currentMode. " +
                                "Battery: $currentBatteryPct percent. " +
                                "Top left navigate, top right find, " +
                                "bottom left scene, bottom right standby.",
                        force = true
                    )
                }
            })

        previewView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupTouchZones() {
        zoneNav.setOnClickListener {
            animateZoneTap(zoneNav)
            modeIndex = 0
            setMode("NAV")
            voiceEngine.vibrate(100)
        }

        zoneAsk.setOnClickListener {
            animateZoneTap(zoneAsk)
            modeIndex = 1
            setMode("ASK")
            voiceEngine.vibrate(100)
        }

        zoneDescribe.setOnClickListener {
            animateZoneTap(zoneDescribe)
            modeIndex = 2
            setMode("DESCRIBE")
            voiceEngine.vibrate(100)
        }

        zoneStandby.setOnClickListener {
            animateZoneTap(zoneStandby)
            modeIndex = 3
            setMode("STANDBY")
            voiceEngine.vibrate(100)
        }

        zoneNav.setOnLongClickListener {
            speakBattery()
            true
        }

        zoneAsk.setOnLongClickListener {
            voiceEngine.speak(
                "Top left navigate, top right find object, " +
                        "bottom left describe scene, bottom right standby.",
                force = true
            )
            true
        }
    }

    private fun setMode(mode: String) {
        currentMode = mode
        runOnUiThread {
            tvMode.text = mode
            when (mode) {
                "NAV" -> {
                    tvStatus.text = "NAVIGATING..."
                    tvStatus.setTextColor(0xFF00E676.toInt())
                    voiceEngine.speak("Navigation mode activated")
                }
                "ASK" -> {
                    tvStatus.text = "LISTENING..."
                    tvStatus.setTextColor(0xFF00E676.toInt())
                    handleAskMode()
                }
                "DESCRIBE" -> {
                    tvStatus.text = "DESCRIBING..."
                    tvStatus.setTextColor(0xFF00E676.toInt())
                    voiceEngine.speak("Describe mode activated")
                }
                "STANDBY" -> {
                    tvStatus.text = "STANDBY"
                    tvStatus.setTextColor(0xFF00E676.toInt())
                    voiceEngine.speak("Standby")
                }
            }
        }
    }

    private fun handleAskMode() {
        runOnUiThread {
            tvStatus.text = "LISTENING..."
            tvStatus.setTextColor(0xFF00E676.toInt())
        }
        voiceEngine.speak("Find mode activated. Speak now.", force = true)
        Handler(Looper.getMainLooper()).postDelayed({
            startListening()
        }, 2500)
    }

    private fun startListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                runOnUiThread {
                    tvStatus.text = "SPEAK NOW..."
                    tvStatus.setTextColor(0xFF00E676.toInt())
                }
                voiceEngine.vibrate(100)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val query = matches?.firstOrNull()?.lowercase() ?: ""
                Log.d("VIP", "Speech: $query")
                processVoiceQuery(query)
            }

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Sorry, I didn't catch that."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    else -> "Listening error."
                }
                voiceEngine.speak(msg, force = true)
                runOnUiThread {
                    tvStatus.text = "ERROR"
                    tvStatus.setTextColor(0xFFFF5252.toInt())
                }
                returnToStandby()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                runOnUiThread { tvStatus.text = "PROCESSING..." }
            }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun processVoiceQuery(query: String) {
        if (query.isEmpty()) {
            voiceEngine.speak("I didn't understand.", force = true)
            returnToStandby()
            return
        }

        if (query.contains("battery")) {
            speakBattery()
            returnToStandby()
            return
        }

        val detections = lastDetections
        val foundLabel = cocoLabels.firstOrNull { query.contains(it) }

        val response = if (foundLabel != null) {
            val count = detections.count { it.label == foundLabel }
            if (count > 0) "Yes, I found $count $foundLabel"
            else "No $foundLabel found in the scene"
        } else {
            if (detections.isEmpty()) "I don't see any objects right now"
            else {
                val counts = detections.groupBy { it.label }
                    .map { "${it.value.size} ${it.key}" }
                    .joinToString(", ")
                "I can see $counts"
            }
        }

        voiceEngine.speak(response, force = true)
        runOnUiThread {
            tvStatus.text = response.uppercase()
            tvStatus.setTextColor(0xFF00E676.toInt())
        }
        returnToStandby()
    }

    private fun returnToStandby() {
        currentMode = "STANDBY"
        modeIndex = 3
        runOnUiThread {
            tvMode.text = "STANDBY"
            tvStatus.text = "STANDBY"
            tvStatus.setTextColor(0xFF00E676.toInt())
        }
    }

    private fun speakBattery() {
        val chargingText = if (isCharging) "and charging" else "not charging"
        voiceEngine.speak("Battery is at $currentBatteryPct percent $chargingText", force = true)
    }

    private fun setupBatteryMonitor() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct    = if (scale > 0) (level * 100 / scale) else -1
                isCharging = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ==
                        BatteryManager.BATTERY_STATUS_CHARGING
                currentBatteryPct = pct
                val icon = if (isCharging) "⚡" else ""
                runOnUiThread { tvBattery.text = "BAT: $pct% $icon" }
                if (pct in 1..20 && !isCharging) {
                    voiceEngine.speak("Warning: Battery low at $pct percent", force = false)
                }
            }
        }
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e("VIP", "Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(imageProxy: ImageProxy) {
        if (isProcessing) { imageProxy.close(); return }
        isProcessing = true
        try {
            val bitmap = imageProxy.toBitmap()
            lastFrameWidth  = bitmap.width
            lastFrameHeight = bitmap.height
            val detections = yoloDetector.detect(bitmap)
            lastDetections = detections
            runOnUiThread {
                boundingBoxView.updateDetections(detections, lastFrameWidth, lastFrameHeight)
            }
            when (currentMode) {
                "NAV" -> {
                    val depthMap = depthEstimator.estimate(bitmap)
                    if (depthMap != null) {
                        val result = navEngine.analyze(depthMap, detections)
                        runOnUiThread {
                            tvStatus.text = result.message.uppercase()
                            tvStatus.setTextColor(0xFF00E676.toInt())
                        }
                        if (result.criticalHazard) voiceEngine.vibrate(500)
                        voiceEngine.speak(result.message)
                    }
                }
                "DESCRIBE" -> {
                    val description = if (detections.isEmpty()) "No objects detected"
                    else {
                        val counts = detections.groupBy { it.label }
                            .map { "${it.value.size} ${it.key}" }
                            .joinToString(", ")
                        "I see $counts"
                    }
                    runOnUiThread {
                        tvStatus.text = description.uppercase()
                        tvStatus.setTextColor(0xFF00E676.toInt())
                    }
                    voiceEngine.speak(description, force = true)
                    returnToStandby()
                }
                "STANDBY" -> {
                    runOnUiThread {
                        if (detections.isEmpty()) {
                            tvStatus.text = "NO OBJECTS"
                        } else {
                            tvStatus.text = detections.take(3)
                                .joinToString(" | ") { it.label.uppercase() }
                        }
                        tvStatus.setTextColor(0xFF00E676.toInt())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VIP", "Frame error: ${e.message}")
        } finally {
            isProcessing = false
            imageProxy.close()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        yoloDetector.close()
        depthEstimator.close()
        voiceEngine.shutdown()
        speechRecognizer?.destroy()
    }
}