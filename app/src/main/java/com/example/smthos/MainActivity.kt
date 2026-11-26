package com.example.acheronos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
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
    private var terminalStats: TextView? = null

    private var isResumed = false
    private var launchingApp = false
    private var inHiddenFolder = false
    private var returnedHome = false
    private var commandFailed = false

    private val mascotSize = 15f
    private val landscapeCameraPadding = 48

    private val commands = listOf(
        "help",
        "apps",
        "hide",
        "unhide",
        "delete",
        "back"
    )

    private val androidMascot = """
        |        \            /
        |         \          /
        |         +hydNNNNdyh+
        |       +mMMMMMMMMMMMMm+
        |      dMMMMMMMMMMMMMMMMd
        |      hMMMMMMMMMMMMMMMMh
        |
        | dMMMb dMMMMMMMMMMMMMMb dmmmb
        | MMMMM MMMMMMMMMMMMMMMM MMMMM
        | MMMMM MMMMMMMMMMMMMMMM MMMMM
        | MMMMM MMMMMMMMMMMMMMMM MMMMM
        | MMMMM MMMMMMMMMMMMMMMM MMMMM
        | ""yy' MMMMMMMMMMMMMMMM 'yy""
        |
        |        dMMMb    dMMMb
        |        MMMMM    MMMMM
        |        MMMMM    MMMMM
        |        MMMMM    MMMMM
    """.trimMargin()

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
        terminalStats?.text = getCoverStats()
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

    private fun dp(value: Int): Int {
        return (
                value * resources.displayMetrics.density
                ).toInt()
    }

    private fun scrollToBottom() {
        val scroll = terminalScroll ?: return

        scroll.post {
            val content = scroll.getChildAt(0)

            if (content != null) {
                val bottom = (
                        content.bottom -
                                scroll.height +
                                scroll.paddingBottom
                        ).coerceAtLeast(0)

                scroll.scrollTo(0, bottom)
            }
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
        createTerminalScreen(false)
    }

    private fun innerScreen() {
        createTerminalScreen(true)
    }

    private fun createTerminalScreen(isInnerScreen: Boolean) {

        if (isInnerScreen && Build.VERSION.SDK_INT >= 28) {
            val attributes = window.attributes

            attributes.layoutInDisplayCutoutMode =
                if (Build.VERSION.SDK_INT >= 30) {
                    WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

            window.attributes = attributes
        }

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        val screenLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.BLACK)

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_NEVER

            layoutParams = if (isInnerScreen) {
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0.30f
                )
            } else {
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
        }

        terminalScroll = scroll

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.START

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

        terminalStats = stats

        val output = TextView(this).apply {
            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f

            includeFontPadding = false

            setPadding(0, 0, 0, 0)

            visibility = android.view.View.GONE

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val promptRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.START

            isBaselineAligned = false

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val prompt = TextView(this).apply {
            text = ">>>"

            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f

            gravity = Gravity.TOP or Gravity.START

            includeFontPadding = false

            setPadding(0, 0, 0, 0)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        terminalPrompt = prompt

        val commandInput = EditText(this).apply {

            setSingleLine(false)

            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

            setHorizontallyScrolling(false)

            minLines = 1
            maxLines = Int.MAX_VALUE

            gravity = Gravity.TOP or Gravity.START

            setTextColor(Color.WHITE)
            typeface = Typeface.MONOSPACE
            textSize = 15f

            includeFontPadding = false

            this.background = null

            setPadding(0, 0, 0, 0)

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

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

        promptRow.addView(prompt)
        promptRow.addView(commandInput)

        content.addView(stats)
        content.addView(output)
        content.addView(promptRow)

        scroll.addView(content)
        screenLayout.addView(scroll)

        if (isInnerScreen) {

            val centrePanel = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0.40f
                )
            }

            val mascot = TextView(this).apply {
                text = androidMascot

                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE

                textSize = mascotSize
                textScaleX = 0.8f

                includeFontPadding = false

                gravity = Gravity.TOP or Gravity.START

                setPadding(0, 0, 0, 0)
                setHorizontallyScrolling(true)

                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.CENTER_HORIZONTAL
                )
            }

            centrePanel.addView(mascot)

            val rightPanel = FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0.30f
                )
            }

            screenLayout.addView(centrePanel)
            screenLayout.addView(rightPanel)
        }

        root.addView(screenLayout)

        prompt.setOnClickListener {
            commandInput.requestFocus()
            showKeyboard()
            scrollToBottom()
        }

        content.addOnLayoutChangeListener {
                _, _, top, _, bottom,
                _, oldTop, _, oldBottom ->

            val newHeight = bottom - top
            val oldHeight = oldBottom - oldTop

            if (newHeight != oldHeight) {
                scrollToBottom()
            }
        }

        commandInput.addOnLayoutChangeListener {
                _, _, top, _, bottom,
                _, oldTop, _, oldBottom ->

            val newHeight = bottom - top
            val oldHeight = oldBottom - oldTop

            if (
                newHeight != oldHeight &&
                commandInput.hasFocus()
            ) {
                scrollToBottom()
            }
        }

        fun appendHistory(
            command: String,
            result: String
        ) {
            val entry = buildString {
                append(">>>")
                append(command)

                result.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        append('\n')
                        append(line)
                    }
            }

            val previous = output.text.toString().trimEnd()

            output.text = if (previous.isEmpty()) {
                entry
            } else {
                "$previous\n$entry"
            }

            output.visibility = android.view.View.VISIBLE
        }

        fun submitCommand() {

            val command = commandInput.text
                .toString()
                .replace(Regex("[\\r\\n]+"), " ")
                .trim()

            if (command.isNotEmpty()) {

                returnedHome = false
                commandFailed = false

                val result = runCommand(command)

                commandInput.text.clear()

                if (returnedHome) {

                    output.text = ""
                    output.visibility = android.view.View.GONE

                    scroll.post {
                        scroll.scrollTo(0, 0)
                    }

                } else {

                    appendHistory(
                        command,
                        result
                    )

                    scrollToBottom()
                }
            }

            if (!launchingApp) {
                commandInput.requestFocus()
                showKeyboard()
            }
        }

        commandInput.setOnEditorActionListener {
                _, actionId, event ->

            val enterPressed =
                event?.keyCode == KeyEvent.KEYCODE_ENTER

            when {
                enterPressed -> {
                    if (
                        event?.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                    ) {
                        submitCommand()
                    }

                    true
                }

                actionId == EditorInfo.IME_ACTION_DONE ||
                        actionId == EditorInfo.IME_ACTION_GO ||
                        actionId == EditorInfo.IME_ACTION_SEND -> {
                    submitCommand()
                    true
                }

                else -> false
            }
        }

        var handlingNewline = false

        commandInput.addTextChangedListener(
            object : android.text.TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {}

                override fun afterTextChanged(
                    editable: android.text.Editable?
                ) {
                    if (handlingNewline || editable == null) {
                        return
                    }

                    if (
                        editable.contains('\n') ||
                        editable.contains('\r')
                    ) {
                        handlingNewline = true

                        try {
                            val cleaned = editable.toString()
                                .replace(
                                    Regex("[\\r\\n]+"),
                                    " "
                                )

                            editable.replace(
                                0,
                                editable.length,
                                cleaned
                            )

                            submitCommand()

                        } finally {
                            handlingNewline = false
                        }
                    }
                }
            }
        )

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

            val padding = dp(16)

            if (isInnerScreen) {

                val cutout = insets.displayCutout
                    ?: ViewCompat.getRootWindowInsets(root)
                        ?.displayCutout

                val isLandscape =
                    resources.configuration.orientation ==
                            Configuration.ORIENTATION_LANDSCAPE

                val cameraFallback =
                    if (isLandscape) {
                        dp(landscapeCameraPadding)
                    } else {
                        0
                    }

                val safeLeft = maxOf(
                    bars.left + padding,
                    (cutout?.safeInsetLeft ?: 0) + padding,
                    cameraFallback
                )

                val safeRight = maxOf(
                    bars.right + padding,
                    (cutout?.safeInsetRight ?: 0) + padding,
                    cameraFallback
                )

                val safeTop = maxOf(
                    bars.top,
                    cutout?.safeInsetTop ?: 0
                ) + padding

                val safeBottom = maxOf(
                    bars.bottom,
                    cutout?.safeInsetBottom ?: 0,
                    ime.bottom
                ) + padding

                view.setPadding(
                    safeLeft,
                    safeTop,
                    safeRight,
                    safeBottom
                )

            } else {

                view.setPadding(
                    bars.left + padding,
                    bars.top + padding,
                    bars.right + padding,
                    maxOf(bars.bottom, ime.bottom) + padding
                )
            }

            if (
                keyboardWasVisible &&
                !keyboardVisible &&
                !launchingApp &&
                isResumed
            ) {
                showKeyboard()
            }

            if (keyboardVisible && !keyboardWasVisible) {
                scrollToBottom()
            }

            keyboardWasVisible = keyboardVisible

            insets
        }

        scroll.addOnLayoutChangeListener {
                _, _, top, _, bottom,
                _, oldTop, _, oldBottom ->

            val newHeight = bottom - top
            val oldHeight = oldBottom - oldTop

            if (
                newHeight != oldHeight &&
                commandInput.hasFocus()
            ) {
                scrollToBottom()
            }
        }

        setContentView(root)

        commandInput.requestFocus()

        ViewCompat.requestApplyInsets(root)

        showKeyboard()
    }

    private fun normalize(value: String): String {
        return value.lowercase().filter {
            it.isLetterOrDigit()
        }
    }

    private fun commandError(message: String): String {
        commandFailed = true
        return message
    }

    private fun unknownCommand(command: String): String {
        return commandError("Unknown command: $command")
    }

    private fun updatePrompt() {
        terminalPrompt?.text = ">>>"
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

    private fun launchableApps(): List<ResolveInfo> {
        return if (inHiddenFolder) {
            installedApps()
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
            return commandError(
                if (hide) {
                    "Usage: hide <app name>"
                } else {
                    "Usage: unhide <app name>"
                }
            )
        }

        if (normalize(requested).length < 2) {
            return commandError("Enter at least 2 letters.")
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
            return commandError("App not found: $requested")
        }

        if (matches.size > 1) {
            return commandError("Provide more letters.")
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

    // Request Android's native uninstall confirmation.
    private fun deleteApp(requested: String): String {

        if (requested.isBlank()) {
            return commandError("Usage: delete <app name>")
        }

        if (normalize(requested).length < 2) {
            return commandError("Enter at least 2 letters.")
        }

        val matches = findMatches(
            requested,
            activeApps()
        ).distinctBy {
            it.activityInfo.packageName
        }

        if (matches.isEmpty()) {
            return commandError("App not found: $requested")
        }

        if (matches.size > 1) {
            return commandError("Provide more letters.")
        }

        val app = matches.first()
        val name = appName(app)
        val appPackage = app.activityInfo.packageName

        return try {

            val intent = Intent(
                Intent.ACTION_DELETE,
                Uri.fromParts(
                    "package",
                    appPackage,
                    null
                )
            )

            launchingApp = true

            val keyboard = getSystemService(
                Context.INPUT_METHOD_SERVICE
            ) as InputMethodManager

            keyboard.hideSoftInputFromWindow(
                window.decorView.windowToken,
                0
            )

            startActivity(intent)

            "Uninstall requested: $name"

        } catch (_: Exception) {

            launchingApp = false
            showKeyboard()

            commandError("Unable to uninstall $name.")
        }
    }

    private fun runCommand(command: String): String {

        val cleaned = command.trim()

        if (cleaned.isEmpty()) {
            return ""
        }

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

        if (firstWord in commands) {
            return executeCommand(
                firstWord,
                argument
            )
        }

        // Explicit shortcut for delete.
        if (firstWord == "del") {
            return executeCommand(
                "delete",
                argument
            )
        }

        val query = normalize(cleaned)

        if (query.length < 2) {
            return unknownCommand(cleaned)
        }

        val apps = launchableApps()

        val commandMatches =
            if (firstWord.length >= 2) {

                commands.filter { candidate ->

                    candidate.startsWith(firstWord) &&
                            (
                                    argument.isEmpty() ||
                                            candidate == "hide" ||
                                            candidate == "unhide" ||
                                            candidate == "delete"
                                    )
                }

            } else {
                emptyList()
            }

        val appMatches = apps.filter {
            normalize(appName(it)).startsWith(query)
        }

        val totalMatches =
            commandMatches.size + appMatches.size

        if (totalMatches == 0) {
            return unknownCommand(cleaned)
        }

        if (totalMatches > 1) {
            return commandError("Provide more letters.")
        }

        if (commandMatches.size == 1) {
            return executeCommand(
                commandMatches.first(),
                argument
            )
        }

        if (appMatches.size == 1) {
            return launchApp(cleaned)
        }

        return unknownCommand(cleaned)
    }

    private fun executeCommand(
        command: String,
        argument: String
    ): String {

        if (
            argument.isNotEmpty() &&
            command != "hide" &&
            command != "unhide" &&
            command != "delete"
        ) {
            return commandError("Usage: $command")
        }

        return when (command) {

            "help" -> """
                COMMANDS

                help - List all commands
                apps - List main directory apps
                ? - Open ? folder
                hide <app> - Move app to ?
                unhide <app> - Move app to home
                delete <app> - Uninstall app
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

            "delete" -> {
                deleteApp(argument)
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

                ""
            }

            else -> {
                unknownCommand(command)
            }
        }
    }

    private fun launchApp(command: String): String {

        if (normalize(command).length < 2) {
            return unknownCommand(command)
        }

        val matches = findMatches(
            command,
            launchableApps()
        )

        if (matches.isEmpty()) {
            return unknownCommand(command)
        }

        if (matches.size > 1) {
            return commandError("Provide more letters.")
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

            commandError("Unable to launch $name.")
        }
    }

    private fun getDeviceName(): String {

        if (Build.MODEL.equals(
                "SM-F976B",
                ignoreCase = true
            )
        ) {
            return "Galaxy Z Fold8 Ultra"
        }

        val manufacturer = Build.MANUFACTURER
            .replaceFirstChar { it.uppercase() }

        val model = Build.MODEL

        return if (
            model.startsWith(
                manufacturer,
                ignoreCase = true
            )
        ) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    private fun getSystemTemperature(): String {

        return try {

            val batteryIntent = registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val temperature = batteryIntent?.getIntExtra(
                BatteryManager.EXTRA_TEMPERATURE,
                Int.MIN_VALUE
            ) ?: Int.MIN_VALUE

            if (temperature == Int.MIN_VALUE) {
                "N/A"
            } else {
                "${"%.1f".format(temperature / 10.0)}°C"
            }

        } catch (_: Exception) {
            "N/A"
        }
    }

    private fun getInstalledAppCount(): Int {

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        @Suppress("DEPRECATION")
        val apps = packageManager.queryIntentActivities(
            launcherIntent,
            0
        )

        return apps
            .map { it.activityInfo.packageName }
            .distinct()
            .size
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

        val deviceName = getDeviceName()
        val deviceModel = Build.MODEL
        val temperature = getSystemTemperature()
        val appCount = getInstalledAppCount()

        return "AcheronOS\n" +
                "Type help for all commands\n" +
                "Device: $deviceName\n" +
                "Model: $deviceModel\n" +
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
                "Memory: ${"%.1f".format(usedRam)}GB/${"%.1f".format(totalRam)}GB\n" +
                "Battery temp: $temperature\n" +
                "Installed apps: $appCount"
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