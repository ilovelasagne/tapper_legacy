package com.example.tapper

import com.example.tapper.R
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var vibrator: Vibrator
    private lateinit var soundPool: SoundPool

    // Views
    private lateinit var menuScreen: FrameLayout
    private lateinit var optionsScreen: FrameLayout
    private lateinit var gameScreen: FrameLayout
    private lateinit var startButton: TextView
    private lateinit var optionsButton: TextView
    private lateinit var backButton: TextView
    private lateinit var difficultyLeft: TextView
    private lateinit var difficultyRight: TextView
    private lateinit var buzzLeft: TextView
    private lateinit var buzzRight: TextView
    private lateinit var soundLeft: TextView
    private lateinit var soundRight: TextView
    private lateinit var themeLeft: TextView
    private lateinit var themeRight: TextView
    private lateinit var currentMode: TextView
    private lateinit var currentBuzz: TextView
    private lateinit var currentSound: TextView
    private lateinit var currentTheme: TextView
    private lateinit var highScoreDisplay: TextView
    private lateinit var highScoreMode: TextView
    private lateinit var scoreNumber: TextView
    private lateinit var gameOverText: TextView
    private lateinit var visualIndicator: View
    private lateinit var difficultyDescription: TextView
    private lateinit var buzzDescription: TextView
    private lateinit var soundDescription: TextView
    private lateinit var themeDescription: TextView

    private val handler = Handler(Looper.getMainLooper())

    // Game state
    private var score = 0
    private var gameOver = false
    private var gameRunning = false
    private var canTap = false
    private var expectingDouble = false
    private var currentDifficultyIndex = 0
    private var tapTimeout: Runnable? = null
    private var buzzIntensityIndex = 1 // 0=off, 1=normal, 2=strong
    private var soundEnabled = true
    private var currentThemeIndex = 0 // 0=dark, 1=light, 2=blue

    private val difficulties = arrayOf("EASY", "MEDIUM", "HARD")
    private val buzzIntensities = arrayOf("OFF", "NORMAL", "STRONG")
    private val themes = arrayOf("DARK", "LIGHT", "BLUE")

    // Difficulty settings
    private val difficultySettings = mapOf(
        "EASY" to DifficultySettings(
            tapWindow = 1200L..1800L,
            roundDelay = 2200L..3800L,
            doubleChance = 0.65f..0.75f
        ),
        "MEDIUM" to DifficultySettings(
            tapWindow = 900L..1500L,
            roundDelay = 1800L..3200L,
            doubleChance = 0.70f..0.80f
        ),
        "HARD" to DifficultySettings(
            tapWindow = 400L..800L,
            roundDelay = 1000L..2000L,
            doubleChance = 0.80f..0.90f
        )
    )

    data class DifficultySettings(
        val tapWindow: LongRange,
        val roundDelay: LongRange,
        val doubleChance: ClosedFloatingPointRange<Float>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupFullscreen()
        initializeViews()
        initializeComponents()
        setupEventListeners()
        loadSettings()
        showMenuScreen()
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initializeViews() {
        // Use FrameLayout for screens for simplicity with black background
        menuScreen = findViewById(R.id.menuScreen)
        optionsScreen = findViewById(R.id.optionsScreen)
        gameScreen = findViewById(R.id.gameScreen)

        // Change buttons to TextViews for white text-only appearance
        startButton = findViewById(R.id.startButton)
        optionsButton = findViewById(R.id.optionsButton)
        backButton = findViewById(R.id.backButton)
        difficultyLeft = findViewById(R.id.difficultyLeft)
        difficultyRight = findViewById(R.id.difficultyRight)
        buzzLeft = findViewById(R.id.buzzLeft)
        buzzRight = findViewById(R.id.buzzRight)
        soundLeft = findViewById(R.id.soundLeft)
        soundRight = findViewById(R.id.soundRight)
        themeLeft = findViewById(R.id.themeLeft)
        themeRight = findViewById(R.id.themeRight)

        // Find all TextViews for updating content
        currentMode = findViewById(R.id.currentMode)
        currentBuzz = findViewById(R.id.currentBuzz)
        currentSound = findViewById(R.id.currentSound)
        currentTheme = findViewById(R.id.currentTheme)
        highScoreDisplay = findViewById(R.id.highScoreDisplay)
        highScoreMode = findViewById(R.id.highScoreMode)

        // Game screen views
        scoreNumber = findViewById(R.id.scoreNumber)
        gameOverText = findViewById(R.id.gameOverText)
        visualIndicator = findViewById(R.id.visualIndicator)

        // Descriptions
        difficultyDescription = findViewById(R.id.difficultyDescription)
        buzzDescription = findViewById(R.id.buzzDescription)
        soundDescription = findViewById(R.id.soundDescription)
        themeDescription = findViewById(R.id.themeDescription)
    }

    private fun initializeComponents() {
        prefs = getSharedPreferences("tapper_prefs", Context.MODE_PRIVATE)

        // Initialize vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Initialize sound pool
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
    }

    private fun setupEventListeners() {
        // Back button handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    optionsScreen.visibility == View.VISIBLE -> showMenuScreen()
                    gameScreen.visibility == View.VISIBLE && gameOver -> showMenuScreen()
                    gameScreen.visibility == View.VISIBLE && gameRunning -> {
                        endGame()
                        handler.postDelayed({ showMenuScreen() }, 1000)
                    }
                    else -> finish()
                }
            }
        })

        // Menu buttons
        startButton.setOnClickListener { startGame() }
        optionsButton.setOnClickListener { showOptionsScreen() }
        backButton.setOnClickListener { showMenuScreen() }

        // Difficulty selector
        difficultyLeft.setOnClickListener { cycleDifficulty(-1) }
        difficultyRight.setOnClickListener { cycleDifficulty(1) }

        // Options selectors
        buzzLeft.setOnClickListener { cycleBuzzIntensity(-1) }
        buzzRight.setOnClickListener { cycleBuzzIntensity(1) }
        soundLeft.setOnClickListener { toggleSound() }
        soundRight.setOnClickListener { toggleSound() }
        themeLeft.setOnClickListener { cycleTheme(-1) }
        themeRight.setOnClickListener { cycleTheme(1) }

        // Game screen tap
        gameScreen.setOnClickListener { handleTap() }
    }

    private fun loadSettings() {
        buzzIntensityIndex = prefs.getInt("buzz_intensity", 1)
        soundEnabled = prefs.getBoolean("sound_enabled", true)
        currentThemeIndex = prefs.getInt("theme", 0)
        applyTheme()
        updateHighScoreDisplay()
    }

    private fun saveSettings() {
        prefs.edit().apply {
            putInt("buzz_intensity", buzzIntensityIndex)
            putBoolean("sound_enabled", soundEnabled)
            putInt("theme", currentThemeIndex)
            apply()
        }
    }

    private fun showMenuScreen() {
        menuScreen.visibility = View.VISIBLE
        optionsScreen.visibility = View.GONE
        gameScreen.visibility = View.GONE
        gameRunning = false
        gameOver = false
        updateHighScoreDisplay()
    }

    private fun showOptionsScreen() {
        menuScreen.visibility = View.GONE
        optionsScreen.visibility = View.VISIBLE
        gameScreen.visibility = View.GONE
        updateOptionsDisplay()
    }

    private fun showGameScreen() {
        menuScreen.visibility = View.GONE
        optionsScreen.visibility = View.GONE
        gameScreen.visibility = View.VISIBLE
    }

    private fun updateOptionsDisplay() {
        currentBuzz.text = buzzIntensities[buzzIntensityIndex]
        currentSound.text = if (soundEnabled) "ON" else "OFF"
        currentTheme.text = themes[currentThemeIndex]

        difficultyDescription.text = "Changes speed and difficulty of game."
        buzzDescription.text = "Adjusts vibration intensity."
        soundDescription.text = "Toggles sound effects."
        themeDescription.text = "Changes the app's color scheme."
    }

    private fun cycleDifficulty(direction: Int) {
        currentDifficultyIndex = (currentDifficultyIndex + direction + difficulties.size) % difficulties.size
        currentMode.text = difficulties[currentDifficultyIndex]
        updateHighScoreDisplay()
    }

    private fun cycleBuzzIntensity(direction: Int) {
        buzzIntensityIndex = (buzzIntensityIndex + direction + buzzIntensities.size) % buzzIntensities.size
        currentBuzz.text = buzzIntensities[buzzIntensityIndex]
        saveSettings()
    }

    private fun toggleSound() {
        soundEnabled = !soundEnabled
        currentSound.text = if (soundEnabled) "ON" else "OFF"
        saveSettings()
    }

    private fun cycleTheme(direction: Int) {
        currentThemeIndex = (currentThemeIndex + direction + themes.size) % themes.size
        currentTheme.text = themes[currentThemeIndex]
        applyTheme()
        saveSettings()
    }

    private fun applyTheme() {
        // Force black background for all screens and white text
        menuScreen.setBackgroundColor(android.graphics.Color.BLACK)
        optionsScreen.setBackgroundColor(android.graphics.Color.BLACK)
        gameScreen.setBackgroundColor(android.graphics.Color.BLACK)
    }

    private fun getHighScore(difficulty: String): Int {
        return prefs.getInt("high_score_$difficulty", 0)
    }

    private fun saveHighScore(difficulty: String, score: Int) {
        prefs.edit().putInt("high_score_$difficulty", score).apply()
    }

    private fun updateHighScoreDisplay() {
        val currentDifficulty = difficulties[currentDifficultyIndex]
        val highScore = getHighScore(currentDifficulty)
        highScoreDisplay.text = highScore.toString()
        highScoreMode.text = if (highScore > 0) "on $currentDifficulty" else ""
    }

    private fun startGame() {
        showGameScreen()
        score = 0
        gameOver = false
        gameRunning = true
        canTap = false
        expectingDouble = false
        tapTimeout?.let { handler.removeCallbacks(it) }
        scoreNumber.text = score.toString()
        gameOverText.visibility = View.GONE
        visualIndicator.visibility = View.GONE
        handler.postDelayed({ buzzRound() }, 1000)
    }

    private fun buzzRound() {
        if (gameOver || !gameRunning) return

        val currentDifficulty = difficulties[currentDifficultyIndex]
        val settings = difficultySettings[currentDifficulty]!!
        val progressionMultiplier = getProgressionMultiplier()

        // Determine if this should be a double tap
        val baseDoubleChance = Random.nextFloat() * (settings.doubleChance.endInclusive - settings.doubleChance.start) + settings.doubleChance.start
        val progressionBonus = min(0.15f, score * 0.01f)
        val doubleChance = min(0.95f, baseDoubleChance + progressionBonus)
        expectingDouble = Random.nextFloat() < doubleChance

        // Vibrate and play sound
        if (expectingDouble) {
            val patterns = arrayOf(
                longArrayOf(200, 100, 200),
                longArrayOf(150, 120, 180),
                longArrayOf(180, 80, 220),
                longArrayOf(160, 110, 200)
            )
            val pattern = patterns[Random.nextInt(patterns.size)]
            vibratePattern(pattern)
            playDoubleBeep()
        } else {
            val durations = arrayOf(180L, 200L, 220L, 160L)
            val duration = durations[Random.nextInt(durations.size)]
            vibrateSingle(duration)
            playSingleBeep()
        }

        canTap = true

        // Set tap window timeout
        val baseTapWindow = Random.nextLong(settings.tapWindow.first, settings.tapWindow.last + 1)
        val tapWindow = (baseTapWindow * progressionMultiplier).toLong()

        tapTimeout = Runnable {
            if (canTap && gameRunning) {
                // If the user missed the tap (timeout occurred), lose all points
                endGame(pointsLost = true)
            }
        }
        handler.postDelayed(tapTimeout!!, tapWindow)

        // Schedule next round
        val baseNextRoundDelay = Random.nextLong(settings.roundDelay.first, settings.roundDelay.last + 1)
        val nextRoundDelay = (baseNextRoundDelay * progressionMultiplier).toLong()
        handler.postDelayed({ buzzRound() }, nextRoundDelay)
    }

    private fun getProgressionMultiplier(): Float {
        // Calculate the number of buzzes to reach max difficulty (50% harder) in 12 minutes
        // Assuming an average round takes ~3 seconds (mid-range of EASY difficulty)
        // 12 minutes = 720 seconds. 720 / 3 = 240 rounds
        val maxRounds = 240f
        val progressionFactor = min(1f, score / maxRounds)
        // Progression is from 1.0 (no change) to 0.5 (50% harder)
        return 1f - (0.5f * progressionFactor)
    }

    private fun handleTap() {
        if (gameOver) {
            gameOverText.visibility = View.GONE
            startGame()
            return
        }

        if (!gameRunning || !canTap) return

        tapTimeout?.let { handler.removeCallbacks(it) }

        if (expectingDouble) {
            score++
            scoreNumber.text = score.toString()
            canTap = false
        } else {
            endGame(pointsLost = true)
        }
    }

    private fun endGame(pointsLost: Boolean = false) {
        gameOver = true
        gameRunning = false
        canTap = false
        tapTimeout?.let { handler.removeCallbacks(it) }

        if (pointsLost) {
            score = 0
            scoreNumber.text = score.toString()
        }

        // Long vibration for game over
        vibrateSingle(2000)

        gameOverText.visibility = View.VISIBLE

        val currentDifficulty = difficulties[currentDifficultyIndex]
        val highScore = getHighScore(currentDifficulty)

        if (score > highScore) {
            saveHighScore(currentDifficulty, score)
        }
    }

    private fun vibrateSingle(duration: Long) {
        if (buzzIntensityIndex == 0) {
            showVisualIndicator()
            return
        }

        val multiplier = when (buzzIntensityIndex) {
            2 -> 1.5f
            else -> 1f
        }

        val adjustedDuration = (duration * multiplier).toLong()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(adjustedDuration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(adjustedDuration)
        }
    }

    private fun vibratePattern(pattern: LongArray) {
        if (buzzIntensityIndex == 0) {
            showVisualIndicator()
            return
        }

        val multiplier = when (buzzIntensityIndex) {
            2 -> 1.5f
            else -> 1f
        }

        val adjustedPattern = pattern.map { (it * multiplier).toLong() }.toLongArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(adjustedPattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(adjustedPattern, -1)
        }
    }

    private fun showVisualIndicator() {
        visualIndicator.visibility = View.VISIBLE
        visualIndicator.alpha = 1f
        handler.postDelayed({
            visualIndicator.animate().alpha(0f).setDuration(300).start()
        }, 100)
    }

    private fun playSingleBeep() {
        if (!soundEnabled) return
        // Placeholder for single beep sound
    }

    private fun playDoubleBeep() {
        if (!soundEnabled) return
        // Placeholder for double beep sound
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
        handler.removeCallbacksAndMessages(null)
    }
}