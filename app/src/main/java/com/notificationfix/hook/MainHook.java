package com.notificationfix.hook;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "NotificationFix";
    private static final String PREFS_NAME = "notification_fix_prefs";
    
    // SMS apps that commonly cause notification flood
    private static final Set<String> SMS_PACKAGES = new HashSet<>(Arrays.asList(
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.textra",
        "com.p1.mobile.putong",
        "com.dialer.dialer",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.facebook.orca"
    ));
    
    // Critical apps that should never be blocked
    private static final Set<String> CRITICAL_PACKAGES = new HashSet<>(Arrays.asList(
        "com.android.phone",
        "com.android.dialer",
        "com.android.servertelecom",
        "com.google.android.dialer",
        "com.android.incallui"
    ));
    
    // Default settings - AGGRESSIVE for 1000+ SMS scenario
    private static int NOTIFICATION_THRESHOLD = 5; // Very low threshold
    private static long BATCH_DELAY_MS = 2000; // 2 seconds delay
    private static long COOLDOWN_MS = 5000; // 5 seconds cooldown after flood
    private static int MAX_NOTIFICATIONS_PER_APP = 10; // Max 10 visible per app
    private static int MAX_TOTAL_NOTIFICATIONS = 50; // Max 50 total notifications
    private static boolean ENABLE_GROUPING = true;
    private static boolean ENABLE_LIMITER = true;
    private static boolean ENABLE_MEMORY_OPTIMIZATION = true;
    private static boolean ENABLE_UNISOC_FIX = true;
    private static boolean ENABLE_SMS_FLOOD_PROTECTION = true;
    private static boolean ENABLE_DUPLICATE_BLOCK = true;
    private static boolean ENABLE_AGGRESSIVE_COMPRESSION = true;
    
    // Track notifications per app
    private final Map<String, NotificationQueue> appQueues = new ConcurrentHashMap<>();
    private final Map<String, Long> lastNotificationTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> rapidFireCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cooldownActive = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> cooldownTimers = new ConcurrentHashMap<>();
    
    // Global notification tracking
    private final AtomicInteger totalNotifications = new AtomicInteger(0);
    private final AtomicLong lastGlobalNotificationTime = new AtomicLong(0);
    private final AtomicBoolean globalFloodActive = new AtomicBoolean(false);
    
    // Duplicate detection
    private final Map<String, Long> notificationHashes = new ConcurrentHashMap<>();
    
    // Batch executor
    private final ScheduledExecutorService batchExecutor = Executors.newScheduledThreadPool(2);
    private final ScheduledExecutorService cooldownExecutor = Executors.newSingleThreadScheduledExecutor();
    
    private Context systemContext;
    private boolean hooked = false;
    private int sdkVersion = Build.VERSION.SDK_INT;
    private Handler mainHandler;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android")) return;
        
        Log.d(TAG, "=== NotificationFix AGGRESSIVE MODE ===");
        Log.d(TAG, "Loading for Android " + sdkVersion + "...");
        Log.d(TAG, "Device: Symphony Z60+ (Unisoc T616)");
        Log.d(TAG, "SMS Flood Protection: ENABLED");
        
        try {
            mainHandler = new Handler(Looper.getMainLooper());
            
            // Load preferences
            loadPreferences();
            
            // Get system context
            systemContext = getSystemContext(lpparam);
            
            // Hook SystemServer
            hookSystemServer(lpparam);
            
            // Apply Unisoc specific fixes
            if (ENABLE_UNISOC_FIX) {
                applyUnisocFixes(lpparam);
            }
            
            // Start cleanup thread
            startCleanupThread();
            
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize module", t);
        }
    }

    private Context getSystemContext(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass(
                "android.app.ActivityThread",
                lpparam.classLoader
            );
            Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
            Object activityThread = currentActivityThread.invoke(null);
            
            Field contextField = activityThreadClass.getDeclaredField("mSystemContext");
            contextField.setAccessible(true);
            return (Context) contextField.get(activityThread);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get system context", t);
            return null;
        }
    }

    private void loadPreferences() {
        try {
            XSharedPreferences prefs = new XSharedPreferences("com.notificationfix", PREFS_NAME);
            prefs.makeWorldReadable();
            
            NOTIFICATION_THRESHOLD = prefs.getInt("threshold", 5);
            BATCH_DELAY_MS = prefs.getLong("batch_delay", 2000);
            COOLDOWN_MS = prefs.getLong("cooldown", 5000);
            MAX_NOTIFICATIONS_PER_APP = prefs.getInt("max_per_app", 10);
            MAX_TOTAL_NOTIFICATIONS = prefs.getInt("max_total", 50);
            ENABLE_GROUPING = prefs.getBoolean("enable_grouping", true);
            ENABLE_LIMITER = prefs.getBoolean("enable_limiter", true);
            ENABLE_MEMORY_OPTIMIZATION = prefs.getBoolean("enable_memory_optimization", true);
            ENABLE_UNISOC_FIX = prefs.getBoolean("enable_unisoc_fix", true);
            ENABLE_SMS_FLOOD_PROTECTION = prefs.getBoolean("enable_sms_flood", true);
            ENABLE_DUPLICATE_BLOCK = prefs.getBoolean("enable_duplicate_block", true);
            ENABLE_AGGRESSIVE_COMPRESSION = prefs.getBoolean("enable_aggressive_compression", true);
            
            Log.d(TAG, "=== AGGRESSIVE Settings Loaded ===");
            Log.d(TAG, "Threshold: " + NOTIFICATION_THRESHOLD);
            Log.d(TAG, "Batch Delay: " + BATCH_DELAY_MS + "ms");
            Log.d(TAG, "Cooldown: " + COOLDOWN_MS + "ms");
            Log.d(TAG, "Max Per App: " + MAX_NOTIFICATIONS_PER_APP);
            Log.d(TAG, "Max Total: " + MAX_TOTAL_NOTIFICATIONS);
            Log.d(TAG, "SMS Flood Protection: " + ENABLE_SMS_FLOOD_PROTECTION);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load prefs, using AGGRESSIVE defaults", t);
        }
    }

    private void hookSystemServer(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        Class<?> nmsClass = XposedHelpers.findClass(
            "com.android.server.notification.NotificationManagerService",
            lpparam.classLoader
        );

        // MAIN HOOK: enqueueNotificationInternal - The core flood protection
        try {
            XposedHelpers.findAndHookMethod(
                nmsClass,
                "enqueueNotificationInternal",
                String.class,
                String.class,
                int.class,
                Notification.class,
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!ENABLE_LIMITER) return;
                        
                        String pkg = (String) param.args[0];
                        Notification notification = (Notification) param.args[3];
                        
                        if (notification == null || pkg == null) return;
                        
                        // Skip critical apps (calls, etc.)
                        if (CRITICAL_PACKAGES.contains(pkg)) {
                            return;
                        }
                        
                        // Check global flood state
                        if (globalFloodActive.get()) {
                            if (!isAllowedDuringFlood(pkg, notification)) {
                                Log.d(TAG, "BLOCKED during global flood: " + pkg);
                                param.setResult(null);
                                return;
                            }
                        }
                        
                        // Check cooldown for this app
                        AtomicBoolean cooldown = cooldownActive.get(pkg);
                        if (cooldown != null && cooldown.get()) {
                            if (!isCriticalNotification(notification)) {
                                Log.d(TAG, "BLOCKED during cooldown: " + pkg);
                                param.setResult(null);
                                return;
                            }
                        }
                        
                        // Check for duplicates
                        if (ENABLE_DUPLICATE_BLOCK && isDuplicate(pkg, notification)) {
                            Log.d(TAG, "BLOCKED duplicate: " + pkg);
                            param.setResult(null);
                            return;
                        }
                        
                        // Track and analyze notification
                        FloodResult result = trackAndAnalyze(pkg, notification);
                        
                        if (result.shouldBlock) {
                            Log.d(TAG, "BLOCKED by flood protection: " + pkg + " - " + result.reason);
                            param.setResult(null);
                            return;
                        }
                        
                        if (result.shouldBatch) {
                            Log.d(TAG, "BATCHING notification from: " + pkg);
                            handleBatchedNotification(pkg, notification, param, result);
                        }
                        
                        // Aggressive memory optimization
                        if (ENABLE_MEMORY_OPTIMIZATION) {
                            optimizeMemory(notification);
                        }
                        
                        // Check if we need to trigger global flood protection
                        checkGlobalFlood();
                    }
                }
            );
            Log.d(TAG, "CORE flood protection hook installed!");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook enqueueNotificationInternal", t);
            tryAlternativeEnqueueHook(nmsClass);
        }

        // Hook postNotification for additional optimization
        try {
            XposedHelpers.findAndHookMethod(
                nmsClass,
                "postNotification",
                String.class,
                int.class,
                Notification.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!ENABLE_GROUPING) return;
                        
                        Notification notification = (Notification) param.args[2];
                        if (notification == null) return;
                        
                        optimizeNotification(notification);
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "postNotification hook failed", t);
        }

        // Hook cancelNotification for cleanup
        try {
            XposedHelpers.findAndHookMethod(
                nmsClass,
                "cancelNotification",
                String.class,
                String.class,
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String pkg = (String) param.args[0];
                        if (pkg != null) {
                            cleanupApp(pkg);
                            totalNotifications.decrementAndGet();
                        }
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "cancelNotification hook failed", t);
        }

        // Hook updateNotification for throttling
        try {
            XposedHelpers.findAndHookMethod(
                nmsClass,
                "updateNotification",
                String.class,
                int.class,
                Notification.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!ENABLE_LIMITER) return;
                        
                        String pkg = (String) param.args[0];
                        
                        // Throttle all updates during flood
                        if (globalFloodActive.get() && !CRITICAL_PACKAGES.contains(pkg)) {
                            param.setResult(null);
                            return;
                        }
                        
                        if (shouldThrottleUpdate(pkg)) {
                            param.setResult(null);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            Log.e(TAG, "updateNotification hook failed", t);
        }

        // Android 12 specific hooks
        if (sdkVersion >= Build.VERSION_CODES.S) {
            hookRankingAndGrouping(nmsClass);
        }

        hooked = true;
        Log.d(TAG, "=== ALL AGGRESSIVE HOOKS INSTALLED ===");
    }

    private void tryAlternativeEnqueueHook(Class<?> nmsClass) {
        try {
            XposedHelpers.findAndHookMethod(
                nmsClass,
                "enqueueNotificationInternal",
                String.class,
                String.class,
                int.class,
                Notification.class,
                int.class,
                int.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!ENABLE_LIMITER) return;
                        
                        String pkg = (String) param.args[0];
                        Notification notification = (Notification) param.args[3];
                        
                        if (notification == null || pkg == null) return;
                        if (CRITICAL_PACKAGES.contains(pkg)) return;
                        
                        if (globalFloodActive.get() && !isAllowedDuringFlood(pkg, notification)) {
                            param.setResult(null);
                            return;
                        }
                        
                        FloodResult result = trackAndAnalyze(pkg, notification);
                        if (result.shouldBlock) {
                            param.setResult(null);
                            return;
                        }
                        
                        if (ENABLE_MEMORY_OPTIMIZATION) {
                            optimizeMemory(notification);
                        }
                    }
                }
            );
            Log.d(TAG, "Alternative enqueue hook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Alternative enqueue hook also failed", t);
        }
    }

    private void hookRankingAndGrouping(Class<?> nmsClass) {
        try {
            XposedHelpers.findAndHookMethod(
                nmsClass,
                "calculateRanking",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Skip ranking during flood to save CPU
                        if (globalFloodActive.get()) {
                            param.setResult(null);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            Log.d(TAG, "Ranking hook skipped");
        }
    }

    private void applyUnisocFixes(XC_LoadPackage.LoadPackageParam lpparam) {
        Log.d(TAG, "Applying Unisoc T616 AGGRESSIVE fixes...");
        
        try {
            Class<?> unisocNmsClass = null;
            
            try {
                unisocNmsClass = XposedHelpers.findClass(
                    "com.android.server.notification.UnisocNotificationManagerService",
                    lpparam.classLoader
                );
            } catch (Throwable t) {
                try {
                    unisocNmsClass = XposedHelpers.findClass(
                        "com.unisoc.server.notification.NotificationManagerService",
                        lpparam.classLoader
                    );
                } catch (Throwable t2) {
                    Log.d(TAG, "No Unisoc notification service found");
                }
            }
            
            if (unisocNmsClass != null) {
                hookUnisocNotificationService(unisocNmsClass);
            }
            
            applyUnisocMemoryFixes(lpparam);
            
        } catch (Throwable t) {
            Log.e(TAG, "Failed to apply Unisoc fixes", t);
        }
    }

    private void hookUnisocNotificationService(Class<?> unisocNmsClass) {
        try {
            XposedHelpers.findAndHookMethod(
                unisocNmsClass,
                "processNotification",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Block during flood
                        if (globalFloodActive.get()) {
                            param.setResult(null);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            Log.d(TAG, "Unisoc hook skipped");
        }
    }

    private void applyUnisocMemoryFixes(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> notifCacheClass = XposedHelpers.findClass(
                "com.android.server.notification.NotificationCache",
                lpparam.classLoader
            );
            
            XposedHelpers.findAndHookMethod(
                notifCacheClass,
                "setMaxCacheSize",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // Aggressively reduce cache for budget phone
                        param.args[0] = Math.min((int) param.args[0], 30);
                        Log.d(TAG, "Cache reduced to 30 for Unisoc T616");
                    }
                }
            );
        } catch (Throwable t) {
            Log.d(TAG, "Unisoc memory fix skipped");
        }
    }

    private void startCleanupThread() {
        cooldownExecutor.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                
                // Clean old notification hashes (older than 30 seconds)
                notificationHashes.entrySet().removeIf(entry -> 
                    (now - entry.getValue()) > 30000
                );
                
                // Clean old cooldowns
                cooldownActive.entrySet().removeIf(entry -> {
                    AtomicBoolean cooldown = entry.getValue();
                    if (!cooldown.get()) {
                        // Check if cooldown timer expired
                        ScheduledFuture<?> timer = cooldownTimers.get(entry.getKey());
                        return timer != null && timer.isDone();
                    }
                    return false;
                });
                
                // Reset global flood if no notifications for 10 seconds
                if (globalFloodActive.get() && (now - lastGlobalNotificationTime.get()) > 10000) {
                    globalFloodActive.set(false);
                    Log.d(TAG, "Global flood protection DEACTIVATED");
                }
                
            } catch (Throwable t) {
                Log.e(TAG, "Cleanup thread error", t);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    // ==================== NOTIFICATION TRACKING ====================
    
    private FloodResult trackAndAnalyze(String pkg, Notification notification) {
        FloodResult result = new FloodResult();
        long now = System.currentTimeMillis();
        
        // Get or create queue for this app
        appQueues.computeIfAbsent(pkg, k -> new NotificationQueue(MAX_NOTIFICATIONS_PER_APP));
        NotificationQueue queue = appQueues.get(pkg);
        
        // Add to queue
        queue.add(notification, now);
        
        // Track timing
        long lastTime = lastNotificationTime.getOrDefault(pkg, 0L);
        long timeSinceLast = now - lastTime;
        lastNotificationTime.put(pkg, now);
        
        // Track rapid fire
        rapidFireCount.computeIfAbsent(pkg, k -> new AtomicInteger(0));
        AtomicInteger rapidCount = rapidFireCount.get(pkg);
        
        if (timeSinceLast < 50) { // Less than 50ms between notifications
            int count = rapidCount.incrementAndGet();
            if (count >= 3) {
                result.shouldBlock = true;
                result.reason = "Rapid fire detected: " + count + " in " + timeSinceLast + "ms";
                startCooldown(pkg);
                return result;
            }
        } else if (timeSinceLast > 1000) {
            rapidCount.set(0);
        }
        
        // Check queue size
        if (queue.size() >= NOTIFICATION_THRESHOLD) {
            result.shouldBatch = true;
            result.batchDelay = calculateBatchDelay(queue.size(), timeSinceLast);
        }
        
        // Check if too many notifications from this app
        if (queue.size() > MAX_NOTIFICATIONS_PER_APP) {
            result.shouldBlock = true;
            result.reason = "Queue overflow: " + queue.size() + " notifications";
            startCooldown(pkg);
            return result;
        }
        
        // Check total notifications
        int total = totalNotifications.incrementAndGet();
        if (total > MAX_TOTAL_NOTIFICATIONS) {
            result.shouldBlock = true;
            result.reason = "Global limit reached: " + total + " total";
            triggerGlobalFlood();
            return result;
        }
        
        // SMS flood detection
        if (ENABLE_SMS_FLOOD_PROTECTION && isSmsApp(pkg)) {
            if (queue.size() >= 3 || rapidCount.get() >= 2) {
                result.shouldBlock = true;
                result.reason = "SMS flood detected from: " + pkg;
                startCooldown(pkg, COOLDOWN_MS * 2); // Double cooldown for SMS
                return result;
            }
        }
        
        return result;
    }

    private long calculateBatchDelay(int queueSize, long timeSinceLast) {
        // Aggressive delay calculation
        long baseDelay = BATCH_DELAY_MS;
        
        // Increase delay based on queue size
        if (queueSize > 10) {
            baseDelay *= 3;
        } else if (queueSize > 5) {
            baseDelay *= 2;
        }
        
        // Increase delay if notifications are very rapid
        if (timeSinceLast < 10) {
            baseDelay *= 5;
        } else if (timeSinceLast < 50) {
            baseDelay *= 3;
        } else if (timeSinceLast < 100) {
            baseDelay *= 2;
        }
        
        // Cap at 10 seconds
        return Math.min(baseDelay, 10000);
    }

    private void startCooldown(String pkg) {
        startCooldown(pkg, COOLDOWN_MS);
    }

    private void startCooldown(String pkg, long duration) {
        AtomicBoolean cooldown = new AtomicBoolean(true);
        cooldownActive.put(pkg, cooldown);
        
        // Cancel existing timer if any
        ScheduledFuture<?> existingTimer = cooldownTimers.get(pkg);
        if (existingTimer != null) {
            existingTimer.cancel(false);
        }
        
        // Schedule cooldown end
        ScheduledFuture<?> timer = cooldownExecutor.schedule(() -> {
            cooldown.set(false);
            Log.d(TAG, "Cooldown ended for: " + pkg);
        }, duration, TimeUnit.MILLISECONDS);
        
        cooldownTimers.put(pkg, timer);
        Log.d(TAG, "Cooldown started for: " + pkg + " (" + duration + "ms)");
    }

    private void checkGlobalFlood() {
        int total = totalNotifications.get();
        
        if (total > MAX_TOTAL_NOTIFICATIONS / 2) {
            if (!globalFloodActive.get()) {
                triggerGlobalFlood();
            }
        }
    }

    private void triggerGlobalFlood() {
        globalFloodActive.set(true);
        lastGlobalNotificationTime.set(System.currentTimeMillis());
        Log.w(TAG, "=== GLOBAL FLOOD PROTECTION ACTIVATED ===");
        
        // Auto-deactivate after 30 seconds
        cooldownExecutor.schedule(() -> {
            globalFloodActive.set(false);
            Log.d(TAG, "Global flood protection DEACTIVATED (timeout)");
        }, 30, TimeUnit.SECONDS);
    }

    // ==================== HELPER METHODS ====================
    
    private boolean isSmsApp(String pkg) {
        return SMS_PACKAGES.contains(pkg);
    }

    private boolean isAllowedDuringFlood(String pkg, Notification notification) {
        // Always allow critical notifications
        if (CRITICAL_PACKAGES.contains(pkg)) {
            return true;
        }
        
        // Allow calls and alarms
        if (isCallNotification(notification) || isAlarmNotification(notification)) {
            return true;
        }
        
        return false;
    }

    private boolean isCriticalNotification(Notification notification) {
        if (notification == null) return false;
        
        int priority = notification.priority;
        int flags = notification.flags;
        
        // High priority or ongoing notifications are critical
        return priority >= Notification.PRIORITY_HIGH ||
               (flags & Notification.FLAG_ONGOING_EVENT) != 0 ||
               (flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
    }

    private boolean isCallNotification(Notification notification) {
        if (notification == null || notification.extras == null) return false;
        
        String category = notification.category;
        return Notification.CATEGORY_CALL.equals(category) ||
               Notification.CATEGORY_TRANSPORT.equals(category);
    }

    private boolean isAlarmNotification(Notification notification) {
        if (notification == null) return false;
        
        String category = notification.category;
        return Notification.CATEGORY_ALARM.equals(category) ||
               Notification.CATEGORY_RECOMMENDATION.equals(category);
    }

    private boolean isDuplicate(String pkg, Notification notification) {
        if (!ENABLE_DUPLICATE_BLOCK) return false;
        
        long hash = computeNotificationHash(pkg, notification);
        Long lastTime = notificationHashes.get(String.valueOf(hash));
        
        if (lastTime != null && (System.currentTimeMillis() - lastTime) < 1000) {
            return true; // Duplicate within 1 second
        }
        
        notificationHashes.put(String.valueOf(hash), System.currentTimeMillis());
        return false;
    }

    private long computeNotificationHash(String pkg, Notification notification) {
        // Simple hash based on package, id, and content
        long hash = pkg.hashCode();
        hash = 31 * hash + notification.id;
        
        if (notification.extras != null) {
            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            
            if (title != null) hash = 31 * hash + title.hashCode();
            if (text != null) hash = 31 * hash + text.hashCode();
        }
        
        return hash;
    }

    private void handleBatchedNotification(String pkg, Notification notification, 
                                         MethodHookParam param, FloodResult result) {
        long delay = result.batchDelay > 0 ? result.batchDelay : BATCH_DELAY_MS;
        
        batchExecutor.schedule(() -> {
            try {
                NotificationQueue queue = appQueues.get(pkg);
                if (queue != null) {
                    queue.clear();
                }
                
                AtomicInteger rapidCount = rapidFireCount.get(pkg);
                if (rapidCount != null) {
                    rapidCount.set(0);
                }
            } catch (Throwable t) {
                Log.e(TAG, "Batch handling failed", t);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private boolean shouldThrottleUpdate(String pkg) {
        long lastTime = lastNotificationTime.getOrDefault(pkg, 0L);
        long timeSinceLast = System.currentTimeMillis() - lastTime;
        
        // Very aggressive throttling during flood
        if (globalFloodActive.get()) {
            return timeSinceLast < 500; // 500ms throttle during flood
        }
        
        return timeSinceLast < 100; // Normal throttling
    }

    // ==================== OPTIMIZATION METHODS ====================
    
    private void optimizeNotification(Notification notification) {
        try {
            if (notification.extras != null && notification.extras.size() > 5) {
                if (notification.priority > Notification.PRIORITY_DEFAULT) {
                    notification.priority = Notification.PRIORITY_DEFAULT;
                }
            }
            
            // Strip all heavy data
            if (notification.extras != null) {
                notification.extras.remove(Notification.EXTRA_PICTURE);
                notification.extras.remove(Notification.EXTRA_LARGE_ICON);
                notification.extras.remove(Notification.EXTRA_BIG_PICTURE);
            }
            
            // Remove custom views (biggest memory hog)
            notification.contentView = null;
            notification.bigContentView = null;
            notification.headsUpContentView = null;
        } catch (Throwable t) {
            // Ignore
        }
    }

    private void optimizeMemory(Notification notification) {
        try {
            if (notification.extras == null) return;
            
            // Remove ALL bitmaps
            notification.extras.remove(Notification.EXTRA_PICTURE);
            notification.extras.remove(Notification.EXTRA_LARGE_ICON);
            notification.extras.remove(Notification.EXTRA_BIG_PICTURE);
            notification.extras.remove(Notification.EXTRA_SMALL_ICON);
            notification.extras.remove(Notification.EXTRA_SUB_TEXT);
            notification.extras.remove(Notification.EXTRA_INFO_TEXT);
            notification.extras.remove(Notification.EXTRA_SUMMARY_TEXT);
            notification.extras.remove(Notification.EXTRA_REMOTE_INPUT_HISTORY);
            
            // Aggressively truncate text
            CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null && title.length() > 30) {
                notification.extras.putCharSequence(Notification.EXTRA_TITLE, 
                    title.subSequence(0, 30));
            }
            
            CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text != null && text.length() > 50) {
                notification.extras.putCharSequence(Notification.EXTRA_TEXT, 
                    text.subSequence(0, 50));
            }
            
            // Clear message text for SMS
            if (ENABLE_AGGRESSIVE_COMPRESSION) {
                notification.extras.remove(Notification.EXTRA_TEXT_LINES);
                notification.extras.remove(Notification.EXTRA_CONVERSATION_TITLE);
            }
            
            // Remove custom views
            notification.contentView = null;
            notification.bigContentView = null;
            notification.headsUpContentView = null;
            
        } catch (Throwable t) {
            // Ignore
        }
    }

    private void cleanupApp(String pkg) {
        NotificationQueue queue = appQueues.remove(pkg);
        if (queue != null) {
            queue.clear();
        }
        lastNotificationTime.remove(pkg);
        rapidFireCount.remove(pkg);
    }

    // ==================== INNER CLASSES ====================
    
    private static class FloodResult {
        boolean shouldBlock = false;
        boolean shouldBatch = false;
        long batchDelay = 0;
        String reason = "";
    }

    private static class NotificationQueue {
        private final LinkedHashMap<Long, Notification> queue;
        private final int maxSize;
        
        NotificationQueue(int maxSize) {
            this.maxSize = maxSize;
            this.queue = new LinkedHashMap<Long, Notification>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Notification> eldest) {
                    return size() > maxSize;
                }
            };
        }
        
        synchronized void add(Notification notification, long timestamp) {
            queue.put(timestamp, notification);
        }
        
        synchronized int size() {
            return queue.size();
        }
        
        synchronized void clear() {
            queue.clear();
        }
        
        synchronized List<Notification> getAll() {
            return new ArrayList<>(queue.values());
        }
    }
}
