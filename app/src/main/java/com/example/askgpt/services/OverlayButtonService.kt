package com.example.askgpt.services

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.askgpt.MainActivity
import com.example.askgpt.utils.LogManager
import com.example.askgpt.utils.LogLevel
import com.example.askgpt.utils.GlobalClipboardManager

/**
 * Floating overlay button service for clipboard processing
 */
class OverlayButtonService : Service() {
    
    companion object {
        private const val TAG = "OverlayButtonService"
        const val ACTION_START_OVERLAY = "START_OVERLAY"
        const val ACTION_REMOVE_OVERLAY = "REMOVE_OVERLAY"
    }
    
    private var windowManager: WindowManager? = null
    private var overlayButton: ImageView? = null
    
    // Touch handling variables for smooth movement
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false
    private var isAnimating: Boolean = false
    
    // Animation properties
    private var currentScaleAnimator: ObjectAnimator? = null
    private var currentMoveAnimator: ValueAnimator? = null
    
    // Service state tracking
    private var isButtonCreated: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        LogManager.addLog(TAG, "🎯 Modern OverlayButtonService created", LogLevel.INFO)
        // Don't create button here - wait for explicit action
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REMOVE_OVERLAY -> {
                LogManager.addLog(TAG, "📴 Removing overlay button", LogLevel.INFO)
                removeOverlayButton()
                stopSelf()
            }
            ACTION_START_OVERLAY -> {
                LogManager.addLog(TAG, "🎯 Starting overlay button", LogLevel.INFO)
                if (!isButtonCreated) {
                    createOverlayButton()
                } else {
                    LogManager.addLog(TAG, "⚠️ Button already exists, skipping creation", LogLevel.WARN)
                }
            }
            else -> {
                LogManager.addLog(TAG, "🔄 Overlay button service started", LogLevel.INFO)
                // Create button only if not already created
                if (!isButtonCreated) {
                    createOverlayButton()
                }
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        LogManager.addLog(TAG, "💀 OverlayButtonService destroyed", LogLevel.INFO)
        removeOverlayButton()
    }
    
    private fun createOverlayButton() {
        try {
            // Prevent duplicate button creation
            if (isButtonCreated || overlayButton != null) {
                LogManager.addLog(TAG, "⚠️ Button already exists, removing old one first", LogLevel.WARN)
                removeOverlayButtonImmediate() // Remove without animation
            }
            
            LogManager.addLog(TAG, "🎨 Creating beautiful water drop overlay button", LogLevel.INFO)
            
            // Get window manager
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // Create button with water drop appearance
            overlayButton = ImageView(this).apply {
                // Create transparent water drop drawable
                background = createWaterDropDrawable()
                
                // No icon for clean water drop look - pure transparent circle
                setImageDrawable(null)
                scaleType = ImageView.ScaleType.CENTER
                alpha = 0.85f // Slightly transparent for glass effect
                elevation = 16f // More shadow for floating water drop effect
                
                // Add subtle ripple effect on transparent background
                isClickable = true
                isFocusable = false
                
                // Initial scale for entrance animation
                scaleX = 0f
                scaleY = 0f
            }
            
            // Set layout parameters for water drop shape (oval)
            val buttonWidth = 75  // Slightly narrower width
            val buttonHeight = 85  // Taller height for water drop shape
            val params = WindowManager.LayoutParams(
                buttonWidth, buttonHeight, // Oval shape: wider than tall for water drop effect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, // Enable hardware acceleration for smooth animations
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50  // Better initial position
                y = 200
            }
            
            // Add touch listener for smooth dragging and clicking
            overlayButton?.setOnTouchListener { view, event ->
                handleSmoothTouch(view, event, params)
            }
            
            // Add to window
            windowManager?.addView(overlayButton, params)
            
            // Mark button as created
            isButtonCreated = true
            
            // Entrance animation - water drop effect
            createEntranceAnimation()
            
            LogManager.addLog(TAG, "✅ Beautiful water drop button created successfully", LogLevel.SUCCESS)
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Failed to create overlay button: ${e.message}", LogLevel.ERROR)
            isButtonCreated = false
        }
    }
    
    /**
     * Create a beautiful transparent water drop drawable
     */
    private fun createWaterDropDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            // Oval shape for water drop appearance
            shape = GradientDrawable.OVAL
            
            // Enhanced transparent gradient like a real water drop
            colors = intArrayOf(
                Color.parseColor("#90F3F4F6"), // Very light with high transparency (top highlight)
                Color.parseColor("#A5E1F5FE"), // Light blue center
                Color.parseColor("#C0BBDEFB"), // Medium blue
                Color.parseColor("#D52196F3"), // Deeper blue
                Color.parseColor("#EA1976D2")  // Deep blue edge (bottom shadow)
            )
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 130f // Slightly larger radius for oval shape
            
            // Very subtle glassy stroke for water drop effect
            setStroke(1, Color.parseColor("#3001579B")) // More transparent stroke
            
            // High transparency for realistic water drop
            alpha = 175 // More transparent like real water drop
        }
    }
    
    /**
     * Create smooth entrance animation like a water drop falling
     */
    private fun createEntranceAnimation() {
        overlayButton?.let { button ->
            // Scale animation with overshoot for bounce effect
            val scaleAnimator = ObjectAnimator.ofFloat(button, "scaleX", 0f, 1.2f, 1f).apply {
                duration = 600
                interpolator = OvershootInterpolator(2f)
            }
            
            val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", 0f, 1.2f, 1f).apply {
                duration = 600
                interpolator = OvershootInterpolator(2f)
            }
            
            // Alpha animation for smooth fade in
            val alphaAnimator = ObjectAnimator.ofFloat(button, "alpha", 0f, 0.9f).apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            // Start animations
            scaleAnimator.start()
            scaleYAnimator.start()
            alphaAnimator.start()
            
            LogManager.addLog(TAG, "💧 Water drop entrance animation started", LogLevel.DEBUG)
        }
    }
    
    /**
     * Handle smooth touch interactions with animations
     */
    private fun handleSmoothTouch(@Suppress("UNUSED_PARAMETER") view: android.view.View, event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Stop any ongoing animations
                currentScaleAnimator?.cancel()
                currentMoveAnimator?.cancel()
                
                // Smooth press animation - slight scale down with color change
                createPressAnimation()
                
                LogManager.addLog(TAG, "👆 Smooth button touch down", LogLevel.DEBUG)
                
                // Store initial position
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                isAnimating = false
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                // Calculate movement with smooth threshold
                val deltaX = kotlin.math.abs(event.rawX - initialTouchX)
                val deltaY = kotlin.math.abs(event.rawY - initialTouchY)
                
                if (deltaX > 15 || deltaY > 15) { // Slightly higher threshold for smoother experience
                    if (!isDragging) {
                        isDragging = true
                        // Start drag animation
                        createDragAnimation()
                        LogManager.addLog(TAG, "🔄 Smooth button dragging started", LogLevel.DEBUG)
                    }
                    
                    // Smooth position update with interpolation
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    
                    // Smooth movement animation
                    animateToPosition(params, newX, newY)
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                // Stop any ongoing animations
                currentScaleAnimator?.cancel()
                currentMoveAnimator?.cancel()
                
                if (!isDragging) {
                    // Normal click - process clipboard
                    createClickAnimation()
                    LogManager.addLog(TAG, "🎯 Click detected - processing clipboard", LogLevel.SUCCESS)
                    
                    // Small delay for animation to complete
                    overlayButton?.postDelayed({
                        triggerClipboardProcessing()
                    }, 150)
                } else {
                    // End drag with settle animation
                    createSettleAnimation()
                    LogManager.addLog(TAG, "🏁 Smooth button drag completed", LogLevel.DEBUG)
                }
                
                // Reset to normal appearance
                createReleaseAnimation()
                return true
            }
        }
        return false
    }
    
    /**
     * Animate button to new position smoothly
     */
    private fun animateToPosition(params: WindowManager.LayoutParams, targetX: Int, targetY: Int) {
        if (isAnimating) return
        
        isAnimating = true
        val startX = params.x
        val startY = params.y
        
        currentMoveAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16 // Very short for smooth real-time movement
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                params.x = (startX + (targetX - startX) * progress).toInt()
                params.y = (startY + (targetY - startY) * progress).toInt()
                
                try {
                    windowManager?.updateViewLayout(overlayButton, params)
                } catch (e: Exception) {
                    // Ignore layout exceptions during animation
                }
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                }
            })
        }
        currentMoveAnimator?.start()
    }
    
    /**
     * Create simple press down animation without color change
     */
    private fun createPressAnimation() {
        overlayButton?.let { button ->
            currentScaleAnimator = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.95f).apply {
                duration = 100
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.95f).apply {
                duration = 100
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            // No color change - keep original water drop appearance
            
            currentScaleAnimator?.start()
            scaleYAnimator.start()
        }
    }
    
    /**
     * Create drag animation
     */
    private fun createDragAnimation() {
        overlayButton?.let { button ->
            // Slightly increase alpha and scale during drag
            val alphaAnimator = ObjectAnimator.ofFloat(button, "alpha", 0.9f, 1f).apply {
                duration = 150
                interpolator = AccelerateDecelerateInterpolator()
            }
            alphaAnimator.start()
        }
    }
    
    /**
     * Create click animation with ripple effect
     */
    private fun createClickAnimation() {
        overlayButton?.let { button ->
            // Quick scale pulse
            val scaleAnimator = ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1.1f, 1f).apply {
                duration = 300
                interpolator = OvershootInterpolator(1.5f)
            }
            val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1.1f, 1f).apply {
                duration = 300
                interpolator = OvershootInterpolator(1.5f)
            }
            
            scaleAnimator.start()
            scaleYAnimator.start()
        }
    }
    
    /**
     * Create settle animation after drag
     */
    private fun createSettleAnimation() {
        overlayButton?.let { button ->
            val settleAnimator = ObjectAnimator.ofFloat(button, "scaleX", button.scaleX, 1f).apply {
                duration = 200
                interpolator = OvershootInterpolator(1.2f)
            }
            val settleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", button.scaleY, 1f).apply {
                duration = 200
                interpolator = OvershootInterpolator(1.2f)
            }
            
            settleAnimator.start()
            settleYAnimator.start()
        }
    }
    
    /**
     * Create release animation
     */
    private fun createReleaseAnimation() {
        overlayButton?.let { button ->
            // Return to normal appearance
            button.background = createWaterDropDrawable()
            
            val scaleAnimator = ObjectAnimator.ofFloat(button, "scaleX", button.scaleX, 1f).apply {
                duration = 150
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", button.scaleY, 1f).apply {
                duration = 150
                interpolator = AccelerateDecelerateInterpolator()
            }
            val alphaAnimator = ObjectAnimator.ofFloat(button, "alpha", button.alpha, 0.9f).apply {
                duration = 150
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            scaleAnimator.start()
            scaleYAnimator.start()
            alphaAnimator.start()
        }
    }
    
    /**
     * Process clipboard in background and auto-switch to Chrome
     * This allows seamless workflow while processing happens behind the scenes
     */
    private fun triggerClipboardProcessing() {
        try {
            LogManager.addLog(TAG, "🌐 Float button touched - starting enhanced workflow", LogLevel.SUCCESS)
            
            // Step 1: AGGRESSIVE clipboard refresh (like we did before)
            LogManager.addLog(TAG, "📋 Performing aggressive clipboard refresh (3 attempts)", LogLevel.INFO)
            
            // First attempt - immediate refresh
            var clipboardText = GlobalClipboardManager.refreshClipboardNow()
            LogManager.addLog(TAG, "📋 Attempt 1: ${clipboardText?.take(30)}...", LogLevel.DEBUG)
            
            // Second attempt after small delay with force sync
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    GlobalClipboardManager.forceSyncFromSystem()
                    val secondAttempt = GlobalClipboardManager.getCurrentClipboardText()
                    LogManager.addLog(TAG, "📋 Attempt 2 (force sync): ${secondAttempt?.take(30)}...", LogLevel.DEBUG)
                    
                    // Third attempt after another delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val finalAttempt = GlobalClipboardManager.refreshClipboardNow()
                            LogManager.addLog(TAG, "📋 Attempt 3 (final): ${finalAttempt?.take(30)}...", LogLevel.DEBUG)
                            
                            // Now trigger the processing with Chrome return
                            LogManager.addLog(TAG, "� Starting background clipboard processing with Chrome return", LogLevel.INFO)
                            val processIntent = Intent(this, ClipboardMonitoringService::class.java).apply {
                                action = ClipboardMonitoringService.ACTION_TEST_CLIPBOARD
                                putExtra("RETURN_TO_CHROME_AFTER_APP", true) // Signal to return to Chrome
                            }
                            startForegroundService(processIntent)
                            
                            LogManager.addLog(TAG, "✅ Enhanced workflow initiated: Refresh → Process → App → Chrome → Notification", LogLevel.SUCCESS)
                            
                        } catch (e: Exception) {
                            LogManager.addLog(TAG, "❌ Error in attempt 3: ${e.message}", LogLevel.ERROR)
                        }
                    }, 200) // 200ms delay for third attempt
                    
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "❌ Error in attempt 2: ${e.message}", LogLevel.ERROR)
                }
            }, 100) // 100ms delay for second attempt
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error in float button processing: ${e.message}", LogLevel.ERROR)
            
            // Fallback: Just process clipboard with single refresh
            LogManager.addLog(TAG, "🔄 Falling back to simple processing", LogLevel.WARN)
            try {
                GlobalClipboardManager.refreshClipboardNow()
                val fallbackIntent = Intent(this, ClipboardMonitoringService::class.java).apply {
                    action = ClipboardMonitoringService.ACTION_TEST_CLIPBOARD
                }
                startForegroundService(fallbackIntent)
                LogManager.addLog(TAG, "⚠️ Processing with simple workflow", LogLevel.WARN)
            } catch (fallbackError: Exception) {
                LogManager.addLog(TAG, "❌ Fallback also failed: ${fallbackError.message}", LogLevel.ERROR)
            }
        }
    }
    
    /**
     * Launch Chrome browser app
     */
    private fun launchChromeApp() {
        try {
            // Try multiple Chrome package names for different Chrome variants
            val chromePackages = listOf(
                "com.android.chrome",           // Chrome
                "com.chrome.beta",              // Chrome Beta
                "com.chrome.dev",               // Chrome Dev
                "com.chrome.canary",            // Chrome Canary
                "com.google.android.apps.chrome" // Alternative Chrome package
            )
            
            var chromeLaunched = false
            
            for (packageName in chromePackages) {
                try {
                    val chromeIntent = packageManager.getLaunchIntentForPackage(packageName)
                    if (chromeIntent != null) {
                        chromeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(chromeIntent)
                        LogManager.addLog(TAG, "✅ Chrome launched successfully: $packageName", LogLevel.SUCCESS)
                        chromeLaunched = true
                        break
                    }
                } catch (e: Exception) {
                    LogManager.addLog(TAG, "⚠️ Failed to launch $packageName: ${e.message}", LogLevel.DEBUG)
                }
            }
            
            // Fallback: Try to open any browser
            if (!chromeLaunched) {
                LogManager.addLog(TAG, "🔄 Chrome not found, trying default browser", LogLevel.WARN)
                val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://www.google.com")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(browserIntent)
                LogManager.addLog(TAG, "✅ Default browser launched as fallback", LogLevel.INFO)
            }
            
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Failed to launch any browser: ${e.message}", LogLevel.ERROR)
            throw e // Re-throw to trigger fallback in main function
        }
    }
    
    private fun removeOverlayButton() {
        try {
            // Cancel any ongoing animations
            currentScaleAnimator?.cancel()
            currentMoveAnimator?.cancel()
            
            overlayButton?.let { button ->
                // Exit animation - fade out and scale down
                val exitScaleAnimator = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val exitScaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                }
                val exitAlphaAnimator = ObjectAnimator.ofFloat(button, "alpha", button.alpha, 0f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                // Remove from window after animation
                exitAlphaAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        try {
                            windowManager?.removeView(button)
                            overlayButton = null
                            isButtonCreated = false
                            LogManager.addLog(TAG, "💧 Beautiful water drop button removed", LogLevel.INFO)
                        } catch (e: Exception) {
                            LogManager.addLog(TAG, "❌ Error removing button from window: ${e.message}", LogLevel.ERROR)
                        }
                    }
                })
                
                exitScaleAnimator.start()
                exitScaleYAnimator.start()
                exitAlphaAnimator.start()
            }
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error removing overlay button: ${e.message}", LogLevel.ERROR)
        }
    }
    
    /**
     * Remove overlay button immediately without animation (for cleanup)
     */
    private fun removeOverlayButtonImmediate() {
        try {
            // Cancel any ongoing animations
            currentScaleAnimator?.cancel()
            currentMoveAnimator?.cancel()
            
            overlayButton?.let { button ->
                windowManager?.removeView(button)
                overlayButton = null
                isButtonCreated = false
                LogManager.addLog(TAG, "🧹 Button removed immediately for cleanup", LogLevel.DEBUG)
            }
        } catch (e: Exception) {
            LogManager.addLog(TAG, "❌ Error in immediate button removal: ${e.message}", LogLevel.ERROR)
        }
    }
}