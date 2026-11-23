
package com.example.acheronos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    private var terminalInput: EditText? = null
    private var terminalPrompt: TextView? = null
    private var terminalScroll: ScrollView? = null

    private var isResumed = false
    private var launchingApp = false
    private var inHiddenFolder = false
    private var returnedHome = false

    private val commands = listOf(
        "help",
        "apps",
        "hide",
        "unhide",
        "back"
    )

    private val preferences by lazy {
        getSharedPreferences(
            "acheron_settings",
            Context.MODE_PRIVATE
        )
    }

    private var hiddenPackages = mutableSetOf<String>()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        launchingApp = false
        showKeyboard()
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            showKeyboard()
        }
    }

    private fun showKeyboard() {
        val input = terminalInput ?: return

        input.postDelayed({
            if (
                isResumed &&
                !launchingApp &&
                hasWindowFocus() &&
                input.isAttachedToWindow
            ) {
                if (!input.hasFocus()) {
                    input.requestFocus()
                }

                WindowCompat.getInsetsController(
                    window,
                    input
                ).show(WindowInsetsCompat.Type.ime())
            }
        }, 150)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hiddenPackages = preferences.getStringSet(
            "hidden_packages",
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).apply {
            isAppearanceLightNavigationBars = false
            isAppearanceLightStatusBars = false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val shortestDimension = minOf(
            resources.configuration.screenWidthDp,
            resources.configuration.screenHeightDp
        )

        if (shortestDimension < 600) {
            coverScreen()
        } else {
            innerScreen()
        }
    }

    private fun coverScreen() {

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_NEVER

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        terminalScroll = scroll

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val stats = TextView(this).apply {
            text = getCoverStats()
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f
            gravity = Gravity.TOP or Gravity.START
            setPadding(0, 0, 0, 0)
        }

        val promptRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val prompt = TextView(this).apply {
            text = "[Ranveer@ZFold8U ~] \$ "
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f
            setPadding(0, 0, 0, 0)
        }

        terminalPrompt = prompt

        val commandInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            setSingleLine(true)
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f
            this.background = null
            setPadding(0, 0, 0, 0)

            imeOptions = EditorInfo.IME_ACTION_DONE or
                    EditorInfo.IME_FLAG_NO_EXTRACT_UI

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            if (Build.VERSION.SDK_INT >= 29) {
                textCursorDrawable?.setTint(Color.WHITE)
            }
        }

        terminalInput = commandInput

        val output = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f
            setPadding(0, 0, 0, 0)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        promptRow.addView(prompt)
        promptRow.addView(commandInput)

        content.addView(stats)
        content.addView(promptRow)
        content.addView(output)

        scroll.addView(content)
        root.addView(scroll)

        prompt.setOnClickListener {
            commandInput.requestFocus()
            showKeyboard()
        }

        commandInput.setOnEditorActionListener { _, actionId, event ->

            val enterPressed =
                event?.keyCode == KeyEvent.KEYCODE_ENTER

            val submit =
                if (enterPressed) {
                    event?.action == KeyEvent.ACTION_DOWN
                } else {
                    actionId == EditorInfo.IME_ACTION_DONE
                }

            if (submit) {
                val command =
                    commandInput.text.toString().trim()

                if (command.isNotEmpty()) {

                    returnedHome = false

                    output.text = runCommand(command)
                    commandInput.text.clear()

                    if (returnedHome) {
                        scroll.post {
                            scroll.scrollTo(0, 0)
                        }
                    } else {
                        scroll.post {
                            scroll.smoothScrollTo(
                                0,
                                promptRow.top
                            )
                        }
                    }
                }

                if (!launchingApp) {
                    showKeyboard()
                }

                true
            } else {
                enterPressed
            }
        }

        var keyboardWasVisible = false

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { view, insets ->

            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val ime = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            val keyboardVisible = insets.isVisible(
                WindowInsetsCompat.Type.ime()
            )

            val padding =
                (16 * resources.displayMetrics.density).toInt()

            view.setPadding(
                bars.left + padding,
                bars.top + padding,
                bars.right + padding,
                maxOf(bars.bottom, ime.bottom) + padding
            )

            if (
                keyboardWasVisible &&
                !keyboardVisible &&
                !launchingApp &&
                isResumed
            ) {
                showKeyboard()
            }

            keyboardWasVisible = keyboardVisible

            insets
        }

        setContentView(root)
        commandInput.requestFocus()
        ViewCompat.requestApplyInsets(root)
        showKeyboard()
    }

    private fun innerScreen() {

        terminalInput = null
        terminalPrompt = null
        terminalScroll = null

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).hide(WindowInsetsCompat.Type.ime())

        val background = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        setContentView(background)
    }

    private fun normalize(value: String): String {
        return value.lowercase().filter {
            it.isLetterOrDigit()
        }
    }

    private fun updatePrompt() {
        val location = if (inHiddenFolder) "?" else "~"

        terminalPrompt?.text =
            "[Ranveer@ZFold8U $location] \$ "
    }

    private fun saveHiddenPackages() {
        preferences.edit()
            .putStringSet(
                "hidden_packages",
                hiddenPackages.toSet()
            )
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun installedApps(): List<ResolveInfo> {

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return packageManager
            .queryIntentActivities(launcherIntent, 0)
            .filter {
                it.activityInfo.packageName != packageName
            }
            .distinctBy {
                it.activityInfo.packageName + "/" +
                        it.activityInfo.name
            }
            .sortedBy {
                appName(it).lowercase()
            }
    }

    private fun appName(app: ResolveInfo): String {
        return app.loadLabel(packageManager).toString()
    }

    private fun visibleApps(): List<ResolveInfo> {
        return installedApps().filter {
            it.activityInfo.packageName !in hiddenPackages
        }
    }

    private fun hiddenApps(): List<ResolveInfo> {
        return installedApps().filter {
            it.activityInfo.packageName in hiddenPackages
        }
    }

    private fun activeApps(): List<ResolveInfo> {
        return if (inHiddenFolder) {
            hiddenApps()
        } else {
            visibleApps()
        }
    }

    private fun findMatches(
        command: String,
        apps: List<ResolveInfo>
    ): List<ResolveInfo> {

        val query = normalize(command)

        if (query.length < 2) {
            return emptyList()
        }

        val exactMatches = apps.filter {
            normalize(appName(it)) == query
        }

        return if (exactMatches.isNotEmpty()) {
            exactMatches
        } else {
            apps.filter {
                normalize(appName(it)).startsWith(query)
            }
        }
    }

    private fun listApps(
        apps: List<ResolveInfo>
    ): String {

        val names = apps.map {
            appName(it)
        }.distinct().sortedBy {
            it.lowercase()
        }

        return if (names.isEmpty()) {
            "No apps found."
        } else {
            names.joinToString("\n")
        }
    }

    private fun moveApp(
        requested: String,
        hide: Boolean
    ): String {

        if (requested.isBlank()) {
            return if (hide) {
                "Usage: hide <app name>"
            } else {
                "Usage: unhide <app name>"
            }
        }

        if (normalize(requested).length < 2) {
            return "Enter at least 2 letters."
        }

        val source = if (hide) {
            visibleApps()
        } else {
            hiddenApps()
        }

        val matches = findMatches(
            requested,
            source
        ).distinctBy {
            it.activityInfo.packageName
        }

        if (matches.isEmpty()) {
            return "App not found: $requested"
        }

        if (matches.size > 1) {
            return "Provide more letters."
        }

        val app = matches.first()
        val name = appName(app)
        val appPackage = app.activityInfo.packageName

        return if (hide) {

            hiddenPackages.add(appPackage)
            saveHiddenPackages()

            "$name moved to ?"

        } else {

            hiddenPackages.remove(appPackage)
            saveHiddenPackages()

            "$name moved to home"
        }
    }

    private fun runCommand(command: String): String {

        val cleaned = command.trim()
        val lower = cleaned.lowercase()

        if (cleaned == "?") {
            return executeCommand("?", "")
        }

        val parts = cleaned.split(
            Regex("\\s+"),
            limit = 2
        )

        val firstWord = parts[0].lowercase()

        val argument =
            parts.getOrNull(1)?.trim() ?: ""

        // Full command names take priority.

        if (firstWord in commands) {
            return executeCommand(
                firstWord,
                argument
            )
        }

        val query = normalize(cleaned)

        if (query.length < 2) {
            return "Enter at least 2 letters."
        }

        val apps = activeApps()

        // Find matching command prefixes.

        val commandMatches =
            if (firstWord.length >= 2) {

                commands.filter { candidate ->

                    candidate.startsWith(firstWord) &&
                            (
                                    argument.isEmpty() ||
                                            candidate == "hide" ||
                                            candidate == "unhide"
                                    )
                }

            } else {
                emptyList()
            }

        // Find matching app prefixes.

        val appMatches = apps.filter {
            normalize(appName(it)).startsWith(query)
        }

        // Commands and apps share the prefix system.

        val totalMatches =
            commandMatches.size + appMatches.size

        if (totalMatches > 1) {
            return "Provide more letters."
        }

        if (commandMatches.size == 1) {
            return executeCommand(
                commandMatches.first(),
                argument
            )
        }

        return launchApp(cleaned)
    }

    private fun executeCommand(
        command: String,
        argument: String
    ): String {

        if (
            argument.isNotEmpty() &&
            command != "hide" &&
            command != "unhide"
        ) {
            return "Usage: $command"
        }

        return when (command) {

            "help" -> """
                COMMANDS

                help - List all commands
                apps - List main directory apps
                ? - Open ? folder
                hide <app> - Move app to ?
                unhide <app> - Move app to home
                back - Return home and clear output
                <app name> - Launch an app

                PREFIXES

                Type at least 2 letters of
                any command or app name.

                Provide more letters if
                multiple matches exist.
            """.trimIndent()

            "apps" -> {
                listApps(visibleApps())
            }

            "hide" -> {
                moveApp(argument, true)
            }

            "unhide" -> {
                moveApp(argument, false)
            }

            "?" -> {
                inHiddenFolder = true
                updatePrompt()

                val apps = hiddenApps()

                if (apps.isEmpty()) {
                    "? folder is empty."
                } else {
                    listApps(apps)
                }
            }

            "back" -> {
                inHiddenFolder = false
                returnedHome = true
                updatePrompt()

                terminalScroll?.post {
                    terminalScroll?.scrollTo(0, 0)
                }

                ""
            }

            else -> {
                "Unknown command: $command"
            }
        }
    }

    private fun launchApp(command: String): String {

        if (normalize(command).length < 2) {
            return "Enter at least 2 letters."
        }

        val matches = findMatches(
            command,
            activeApps()
        )

        if (matches.isEmpty()) {
            return "App not found: $command"
        }

        if (matches.size > 1) {
            return "Provide more letters."
        }

        val app = matches.first()
        val name = appName(app)

        return try {

            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)

                setClassName(
                    app.activityInfo.packageName,
                    app.activityInfo.name
                )

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            launchingApp = true

            val keyboard = getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

            keyboard.hideSoftInputFromWindow(
                window.decorView.windowToken,
                0
            )

            startActivity(intent)

            "Launching $name..."

        } catch (_: Exception) {

            launchingApp = false
            showKeyboard()

            "Unable to launch $name."
        }
    }

    private fun getCoverStats(): String {

        val activityManager =
            getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalRam =
            memoryInfo.totalMem / 1073741824.0

        val usedRam =
            (memoryInfo.totalMem - memoryInfo.availMem) /
                    1073741824.0

        val uptime = SystemClock.elapsedRealtime()
        val hours = uptime / 3600000
        val minutes = (uptime / 60000) % 60

        val oneUI = try {
            val version = Build.VERSION::class.java
                .getField("SEM_PLATFORM_INT")
                .getInt(null) - 90000

            "${version / 10000}.${(version % 10000) / 100}"
        } catch (_: Exception) {
            "N/A"
        }

        val metrics = resources.displayMetrics

        val cpu = if (Build.VERSION.SDK_INT >= 31) {
            Build.SOC_MODEL
        } else {
            Build.HARDWARE
        }

        return "AcheronOS\n" +
                "Type help for all commands\n" +
                "OS: Android ${Build.VERSION.RELEASE}\n" +
                "One UI: $oneUI\n" +
                "Kernel: ${System.getProperty("os.version")?.substringBefore("-")}\n" +
                "Uptime: ${hours}h ${minutes}m" +
                (if (uptime > 604800000L)
                    ", Phone restart recommended"
                else "") + "\n" +
                "Resolution: ${metrics.widthPixels}x${metrics.heightPixels}\n" +
                "AcheronOS: ${BuildConfig.ACHERONOS_VERSION}\n" +
                "CPU: $cpu (${Runtime.getRuntime().availableProcessors()} cores)\n" +
                "GPU: ${getGpuName()}\n" +
                "Memory: ${"%.1f".format(usedRam)}GB/${"%.1f".format(totalRam)}GB"
    }

    private fun getGpuName(): String {

        val egl = android.opengl.EGL14.eglGetDisplay(
            android.opengl.EGL14.EGL_DEFAULT_DISPLAY
        )

        val version = IntArray(2)

        android.opengl.EGL14.eglInitialize(
            egl, version, 0, version, 1
        )

        val attributes = intArrayOf(
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, 4,
            android.opengl.EGL14.EGL_SURFACE_TYPE,
            android.opengl.EGL14.EGL_PBUFFER_BIT,
            android.opengl.EGL14.EGL_NONE
        )

        val configs =
            arrayOfNulls<android.opengl.EGLConfig>(1)

        val count = IntArray(1)

        android.opengl.EGL14.eglChooseConfig(
            egl, attributes, 0, configs, 0, 1, count, 0
        )

        val config = configs[0] ?: return "Unknown"

        val glContext = android.opengl.EGL14.eglCreateContext(
            egl,
            config,
            android.opengl.EGL14.EGL_NO_CONTEXT,
            intArrayOf(
                android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                android.opengl.EGL14.EGL_NONE
            ),
            0
        )

        val surface = android.opengl.EGL14.eglCreatePbufferSurface(
            egl,
            config,
            intArrayOf(
                android.opengl.EGL14.EGL_WIDTH, 1,
                android.opengl.EGL14.EGL_HEIGHT, 1,
                android.opengl.EGL14.EGL_NONE
            ),
            0
        )

        android.opengl.EGL14.eglMakeCurrent(
            egl, surface, surface, glContext
        )

        val gpu = android.opengl.GLES20.glGetString(
            android.opengl.GLES20.GL_RENDERER
        ) ?: "Unknown"

        android.opengl.EGL14.eglMakeCurrent(
            egl,
            android.opengl.EGL14.EGL_NO_SURFACE,
            android.opengl.EGL14.EGL_NO_SURFACE,
            android.opengl.EGL14.EGL_NO_CONTEXT
        )

        android.opengl.EGL14.eglDestroySurface(
            egl, surface
        )

        android.opengl.EGL14.eglDestroyContext(
            egl, glContext
        )

        android.opengl.EGL14.eglTerminate(egl)

        return gpu
    }
}
