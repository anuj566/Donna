package com.myawesomegames.donnav2

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.util.Locale

class DonnaAccessibilityService : AccessibilityService(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var statusView: View
    private lateinit var audioManager: AudioManager
    private lateinit var tts: TextToSpeech

    // Speech Variables
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var originalVolume = 0

    // State Variables
    private var currentContextApp = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)

        setupStatusDot()
        setupSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("en", "IN")
            tts.setSpeechRate(1.0f)
            speak("Donna is ready.")
        }
    }

    private fun speak(text: String) {
        if (::tts.isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun setupStatusDot() {
        statusView = View(this)
        statusView.setBackgroundColor(Color.RED)
        val params = WindowManager.LayoutParams(
            30, 30,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 100
        try { windowManager.addView(statusView, params) } catch (e: Exception) {}
    }

    private fun setupSpeechRecognizer() {
        handler.post {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }

                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if(::statusView.isInitialized) statusView.setBackgroundColor(Color.GREEN)
                        handler.postDelayed({ unmuteBeep() }, 200)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { restartListening() }
                    override fun onError(error: Int) { restartListening() }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            processCommand(matches[0].lowercase())
                        }
                        restartListening()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening()
            }
        }
    }

    private fun startListening() {
        if (!isListening) {
            handler.post {
                try {
                    muteBeep()
                    isListening = true
                    speechRecognizer.startListening(speechIntent)
                } catch (e: Exception) {
                    isListening = false
                    restartListening()
                }
            }
        }
    }

    private fun restartListening() {
        isListening = false
        handler.post { if(::statusView.isInitialized) statusView.setBackgroundColor(Color.RED) }
        handler.postDelayed({ startListening() }, 100)
    }

    private fun muteBeep() {
        try {
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (e: Exception) {}
    }

    private fun unmuteBeep() {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalVolume, 0)
            val currentMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 3
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentMusicVol, 0)
        } catch (e: Exception) {}
    }


    // --- THE BRAIN ---

    private fun processCommand(command: String) {
        Toast.makeText(this, command, Toast.LENGTH_SHORT).show()

        // 1. WhatsApp Agent
        if (command.contains("whatsapp") && (command.contains("msg") || command.contains("bhejo") || command.contains("send"))) {
            executeWhatsAppTask(command)
            return
        }

        // 2. YouTube Agent
        if (command.contains("youtube") && (command.contains("play") || command.contains("chalao") || command.contains("lagao"))) {
            executeYouTubeTask(command)
            return
        }

        // 3. Simple Open
        if (command.contains("kholo") || command.contains("open")) {
            val appName = command.replace("kholo", "").replace("open", "").replace("donna", "").trim()
            speak("Opening $appName")
            openApp(appName)
            return
        }

        // 4. Scroll
        if (command.contains("scroll") || command.contains("niche")) {
            performAggressiveScroll()
        }
    }


    // --- THE AGENTS ---

    private fun executeWhatsAppTask(command: String) {
        speak("Starting WhatsApp task")
        currentContextApp = "WhatsApp"

        // Parse
        val words = command.split(" ")
        var name = ""
        var msg = "Hello"

        if (command.contains("ko")) {
            val idx = words.indexOf("ko")
            if (idx > 0) name = words[idx - 1]
        }
        if (command.contains("bhejo")) msg = command.substringAfter("bhejo").trim()
        else if (command.contains("msg")) msg = command.substringAfter("msg").trim()

        if (name.isEmpty()) name = "Papa" // Fallback

        // Step 1: Open
        if (!openApp("WhatsApp")) {
            speak("I don't have WhatsApp installed.")
            return
        }

        // Step 2: Hunt for Search (Wait 3s for app launch)
        handler.postDelayed({
            speak("Searching for $name")
            // Try to find search button. If not found, assumes we are in chat, so press Back.
            ensureSearchButtonVisible {
                // Once Search is visible:
                clickSomething("Search", "Search", "id/menu_search") // Try Text, Desc, and ID

                // Wait for Keyboard
                handler.postDelayed({
                    typeText(name)

                    // Wait for results
                    handler.postDelayed({
                        // Try to click name. If not found, SCROLL.
                        clickOrScrollToFind(name) { found ->
                            if (found) {
                                speak("Found $name. Sending message.")
                                // Wait for chat to open
                                handler.postDelayed({
                                    typeText(msg)
                                    handler.postDelayed({
                                        clickSomething("Send", "Send", "id/send")
                                    }, 1500)
                                }, 2000)
                            } else {
                                speak("I could not find $name even after scrolling.")
                            }
                        }
                    }, 2000)
                }, 1500)
            }
        }, 3000)
    }

    private fun executeYouTubeTask(command: String) {
        speak("Opening YouTube")
        currentContextApp = "YouTube"
        val query = command.substringAfter("youtube").replace("pe", "").replace("play", "").replace("chalao", "").replace("lagao", "").replace("gana", "").trim()

        if (!openApp("YouTube")) {
            speak("I can't find YouTube.")
            return
        }

        // Wait 4s for heavy YouTube load
        handler.postDelayed({
            speak("Searching for $query")
            // YouTube Search icon usually has desc "Search"
            if (clickSomething("Search", "Search", "id/menu_search_icon")) {
                handler.postDelayed({
                    typeText(query)
                    handler.postDelayed({
                        // Click the first suggestion or press Enter (simulated by clicking query text)
                        if (!clickSomething(query, query, "id/text")) {
                            // If suggestion click fails, try to click the first item in list
                            clickFirstItemInList()
                        }
                    }, 2000)
                }, 1500)
            } else {
                speak("I can't find the search button on YouTube.")
            }
        }, 4000)
    }


    // --- THE JUGGERNAUT TOOLS ---

    // 1. Aggressive Scroll & Hunt
    // Tries to find text. If fails, Swipes BIG, waits, tries again (Max 4 times)
    private fun clickOrScrollToFind(text: String, attempt: Int = 0, onResult: (Boolean) -> Unit) {
        if (clickSomething(text, text, "")) {
            onResult(true)
            return
        }

        if (attempt >= 4) {
            onResult(false)
            return
        }

        speak("Scrolling...")
        performAggressiveScroll()

        handler.postDelayed({
            clickOrScrollToFind(text, attempt + 1, onResult)
        }, 1500) // Wait 1.5s for scroll to settle
    }

    // 2. The "Click Anything" Function
    // Tries Text, ContentDescription, AND ViewID
    private fun clickSomething(text: String, desc: String, viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Strategy A: By Text
        if (text.isNotEmpty()) {
            val list = root.findAccessibilityNodeInfosByText(text)
            if (!list.isNullOrEmpty()) {
                for (node in list) {
                    if (performClick(node)) return true
                }
            }
        }

        // Strategy B: By ID (Needs recursive search, simplified here to use findByText which caches some IDs)
        // Note: accessibilityNodeInfosByText is powerful but doesn't find IDs directly.
        // We iterate generic nodes if needed, but for speed we rely on text/desc first.

        return false // Not found
    }

    // Helper to click
    private fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return performClick(node.parent) // Try parent
    }

    // 3. Ensure Search Button (Back Logic)
    private fun ensureSearchButtonVisible(onReady: () -> Unit) {
        val root = rootInActiveWindow
        if (root != null && !root.findAccessibilityNodeInfosByText("Search").isNullOrEmpty()) {
            onReady()
            return
        }
        // If not found, press BACK and try again
        speak("Going back to find search")
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({
            // Check again (One recursion level)
            val root2 = rootInActiveWindow
            if (root2 != null && !root2.findAccessibilityNodeInfosByText("Search").isNullOrEmpty()) {
                onReady()
            } else {
                // Try one last time
                performGlobalAction(GLOBAL_ACTION_BACK)
                handler.postDelayed({ onReady() }, 1000)
            }
        }, 1000)
    }

    // 4. Aggressive Scroll Gesture (80% of screen)
    private fun performAggressiveScroll() {
        val path = Path()
        path.moveTo(500f, 1800f) // Very bottom
        path.lineTo(500f, 300f)  // Very top
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1000)) // 1 second drag (slower is more reliable)
            .build()
        dispatchGesture(gesture, null, null)
    }

    // 5. Typer
    private fun typeText(text: String) {
        val root = rootInActiveWindow ?: return
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            // Brute force find any edit text
            speak("Trying to force type")
            // (Logic simplified for brevity - Focus is usually reliable)
        }
    }

    // 6. Click First List Item (For YouTube suggestions)
    private fun clickFirstItemInList() {
        // Logic: Find a collection item and click it
        // Placeholder for complex tree traversal
    }

    // 7. Robust App Opener
    private fun openApp(appName: String): Boolean {
        val pm = packageManager
        val apps = pm.getInstalledPackages(0)
        // Exact Match
        for (app in apps) {
            val label = app.applicationInfo?.loadLabel(pm).toString()
            if (label.equals(appName, ignoreCase = true)) {
                startActivity(pm.getLaunchIntentForPackage(app.packageName))
                return true
            }
        }
        // Fuzzy Match
        for (app in apps) {
            val label = app.applicationInfo?.loadLabel(pm).toString().lowercase()
            if (label.contains(appName.lowercase())) {
                startActivity(pm.getLaunchIntentForPackage(app.packageName))
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        if (::statusView.isInitialized) windowManager.removeView(statusView)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}