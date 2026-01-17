package com.myawesomegames.donnav2

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.Uri
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
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent

    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private var originalVolume = 0

    // STATE
    private var appState = "NONE"

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
            speak("Donna is active.")
        }
    }

    private fun speak(text: String) {
        if (!::tts.isInitialized) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // --- UI SETUP ---
    private fun setupStatusDot() {
        try {
            statusView = View(this)
            statusView.setBackgroundColor(Color.RED)
            val params = WindowManager.LayoutParams(
                30, 30,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 20; params.y = 100
            windowManager.addView(statusView, params)
        } catch (e: Exception) {}
    }

    // --- SPEECH RECOGNITION ---
    private fun setupSpeechRecognizer() {
        handler.post {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
                speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (::statusView.isInitialized) statusView.setBackgroundColor(Color.GREEN)
                        handler.postDelayed({ try { audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalVolume, 0) } catch (e: Exception){} }, 200)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { restartListening() }
                    override fun onError(error: Int) { restartListening() }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) processCommand(matches[0].lowercase())
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
                    originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                    audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
                    isListening = true
                    speechRecognizer.startListening(speechIntent)
                } catch (e: Exception) { isListening = false; handler.postDelayed({ startListening() }, 1000) }
            }
        }
    }

    private fun restartListening() {
        isListening = false
        handler.post { if (::statusView.isInitialized) statusView.setBackgroundColor(Color.RED) }
        handler.postDelayed({ startListening() }, 100)
    }

    // --- MAIN LOGIC (FIXED ORDER) ---
    private fun processCommand(cmd: String) {
        val cleanCmd = cmd.replace("donna", "").replace("hello", "").trim()

        Toast.makeText(this, cleanCmd, Toast.LENGTH_SHORT).show()

        // 1. GLOBAL COMMANDS (Check these FIRST)
        if (cleanCmd.contains("stop") || cleanCmd.contains("cancel") || cleanCmd.contains("ruko")) {
            speak("Stopped")
            appState = "NONE"
            return
        }

        if (cleanCmd.contains("back") || cleanCmd.contains("peeche")) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        if (cleanCmd.contains("home") || cleanCmd.contains("ghar")) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            appState = "NONE"
            return
        }

        // 2. OPEN APPS (Priority over searching inside current app)
        // Checks for "Open WhatsApp", "YouTube kholo", etc.
        if (cleanCmd.contains("open") || cleanCmd.contains("kholo") || cleanCmd.contains("chalao")) {
            val appName = extractAppName(cleanCmd)
            if (appName.isNotEmpty()) {
                speak("Opening $appName")
                if (openApp(appName)) {
                    // Reset specific state unless it's YouTube (for search context)
                    appState = if (appName.equals("youtube", true)) "YOUTUBE" else "NONE"
                } else {
                    speak("App not found")
                }
                return
            }
        }

        // 3. MAKE CALLS (Phone)
        if (cleanCmd.contains("call") || cleanCmd.contains("phone") || cleanCmd.contains("milao")) {
            // Check if it's a WhatsApp call specifically
            if (cleanCmd.contains("whatsapp")) {
                handleWhatsAppCall(cleanCmd)
            } else {
                handleNormalCall(cleanCmd)
            }
            return
        }

        // 4. SEND MESSAGES (WhatsApp)
        if (cleanCmd.contains("whatsapp") && (cleanCmd.contains("msg") || cleanCmd.contains("message") || cleanCmd.contains("bhejo") || cleanCmd.contains("send"))) {
            handleWhatsAppMessage(cleanCmd)
            return
        }

        // 5. CONTEXT SPECIFIC (Only runs if no app switch was requested)
        if (appState == "YOUTUBE") {
            // Assume everything else while in YouTube is a search/play command
            val query = cleanCmd.replace("search", "").replace("dhundo", "").replace("play", "").trim()
            if (query.isNotEmpty()) {
                executeYouTubeSearch(query)
                return
            }
        }

        // 6. FALLBACK SEARCH (If app is not YouTube but user says search)
        if (cleanCmd.contains("search") || cleanCmd.contains("dhundo")) {
            val query = cleanCmd.replace("search", "").replace("dhundo", "").trim()
            executeGenericSearch(query)
        }
    }

    // --- FEATURE IMPLEMENTATION ---

    private fun handleNormalCall(cmd: String) {
        val name = extractName(cmd)
        speak("Calling $name")

        // 1. Open Phone App
        if (openApp("Phone") || openApp("Contacts") || openApp("Dialer")) {
            handler.postDelayed({
                // 2. Click Search Button
                if (clickElementByContentDesc("search") || clickElementByText("search")) {
                    handler.postDelayed({
                        // 3. Type Name
                        if (typeText(name)) {
                            handler.postDelayed({
                                // 4. Click the FIRST result in the list (Solves Hindi/English issue)
                                if (clickFirstListResult()) {
                                    speak("Dialing")
                                } else {
                                    speak("Contact not found")
                                }
                            }, 2000)
                        }
                    }, 1000)
                }
            }, 1500)
        }
    }

    private fun handleWhatsAppCall(cmd: String) {
        val name = extractName(cmd)
        val isVideo = cmd.contains("video")
        speak("WhatsApp calling $name")

        if (openApp("WhatsApp")) {
            handler.postDelayed({
                // 1. Find Search in WhatsApp
                if (clickElementByContentDesc("search") || clickElementById("menu_search")) {
                    handler.postDelayed({
                        // 2. Type Name
                        typeText(name)
                        handler.postDelayed({
                            // 3. Click first contact result
                            if (clickFirstListResult()) {
                                handler.postDelayed({
                                    // 4. Click Call Button inside chat
                                    val callKeywords = if(isVideo) listOf("video call") else listOf("voice call", "call")
                                    if (clickElementByContentDescList(callKeywords)) {
                                        speak("Calling")
                                    } else {
                                        speak("Call button not found")
                                    }
                                }, 1500)
                            }
                        }, 2000)
                    }, 1000)
                }
            }, 1500)
        }
    }

    private fun handleWhatsAppMessage(cmd: String) {
        val name = extractName(cmd)
        // Basic parser: assume message is NOT in the voice command for now to be safe,
        // or just open the chat so user can type/speak.
        speak("Opening chat with $name")

        if (openApp("WhatsApp")) {
            handler.postDelayed({
                if (clickElementByContentDesc("search") || clickElementById("menu_search")) {
                    handler.postDelayed({
                        typeText(name)
                        handler.postDelayed({
                            if (clickFirstListResult()) {
                                speak("Chat opened")
                                // Optional: You can add logic here to type a message if you extract it
                            }
                        }, 2000)
                    }, 1000)
                }
            }, 1500)
        }
    }

    private fun executeYouTubeSearch(query: String) {
        speak("Searching $query")
        // Try clicking search button
        if (clickElementByContentDesc("search") || clickElementByText("search")) {
            handler.postDelayed({
                typeText(query)
                handler.postDelayed({
                    // Press enter/search on keyboard
                    pressEnter()
                }, 1000)
            }, 1000)
        }
    }

    private fun executeGenericSearch(query: String) {
        // Generic search for other apps (Blinkit, etc)
        if (clickElementByContentDesc("search") || clickElementByText("search") || clickElementById("search")) {
            handler.postDelayed({
                typeText(query)
                handler.postDelayed({ pressEnter() }, 1000)
            }, 1000)
        }
    }

    // --- ACCESSIBILITY HELPERS (The "Magic") ---

    private fun clickElementByContentDesc(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(desc) // Sometimes text matches desc
        if (nodes.isNotEmpty()) {
            for (node in nodes) if (performClick(node)) return true
        }
        // If not found by text helper, search tree manually for ContentDescription
        return searchTreeForAction(root, desc, true)
    }

    private fun clickElementByContentDescList(descs: List<String>): Boolean {
        for (desc in descs) {
            if (clickElementByContentDesc(desc)) return true
        }
        return false
    }

    private fun clickElementByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNotEmpty()) {
            for (node in nodes) if (performClick(node)) return true
        }
        return false
    }

    private fun clickElementById(idPart: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return searchTreeForAction(root, idPart, false)
    }

    private fun searchTreeForAction(node: AccessibilityNodeInfo?, keyword: String, isDesc: Boolean): Boolean {
        if (node == null) return false

        val textToCheck = if (isDesc) node.contentDescription?.toString() else node.viewIdResourceName
        if (textToCheck?.contains(keyword, true) == true) {
            if (performClick(node)) return true
        }

        for (i in 0 until node.childCount) {
            if (searchTreeForAction(node.getChild(i), keyword, isDesc)) return true
        }
        return false
    }

    // THE SOLVER FOR HINDI/ENGLISH CONTACTS
    private fun clickFirstListResult(): Boolean {
        val root = rootInActiveWindow ?: return false
        // Look for a list or recycler view
        return findListAndClickChild(root)
    }

    private fun findListAndClickChild(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // If we find a list with children, click the first one
        if ((node.className?.contains("List") == true || node.className?.contains("Recycler") == true) && node.childCount > 0) {
            // Child 0 often is not the item, try 0, 1, 2
            for (i in 0 until minOf(node.childCount, 3)) {
                val child = node.getChild(i)
                // We want to click the container, but sometimes we need to click the text inside
                if (performClick(child)) return true
            }
        }

        for (i in 0 until node.childCount) {
            if (findListAndClickChild(node.getChild(i))) return true
        }
        return false
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // If node isn't clickable, check parent
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    private fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        // Try finding focused element first
        var focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus == null) {
            // Search specifically for EditText
            focus = findEditableNode(root)
        }

        if (focus != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            return focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val res = findEditableNode(node.getChild(i))
            if (res != null) return res
        }
        return null
    }

    private fun pressEnter() {
        val root = rootInActiveWindow ?: return
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val args = Bundle()
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE)
        focus?.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args)
        // Note: Real "Enter" key press requires InputMethodService or root,
        // but often clicking the "search" icon near the text box is easier.
    }

    // --- UTILS ---
    private fun extractAppName(cmd: String): String {
        return cmd.replace("open", "").replace("kholo", "").replace("chalao", "").replace("app", "").trim()
    }

    private fun extractName(cmd: String): String {
        // Remove keywords to leave just the name
        val words = listOf("call", "phone", "milao", "whatsapp", "message", "msg", "bhejo", "send", "video", "to", "ko")
        var res = cmd
        for (w in words) res = res.replace(w, "")
        return res.trim()
    }

    private fun openApp(appName: String): Boolean {
        val pm = packageManager
        // 1. Exact Intent
        val intent = pm.getLaunchIntentForPackage(appName)
        if (intent != null) {
            startActivity(intent)
            return true
        }
        // 2. Search Installed Packages
        val packages = pm.getInstalledPackages(0)
        for (pkg in packages) {
            val label = pkg.applicationInfo?.loadLabel(pm).toString()
            if (label.equals(appName, ignoreCase = true) || label.lowercase().contains(appName.lowercase())) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        if (::tts.isInitialized) tts.shutdown()
        if (::speechRecognizer.isInitialized) speechRecognizer.destroy()
        super.onDestroy()
    }
}
