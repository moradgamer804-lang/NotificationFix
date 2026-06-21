# Proguard rules for NotificationFix

-keep class com.notificationfix.hook.MainHook { *; }
-keep class com.notificationfix.hook.MainHook$NotificationInfo { *; }
-keep class com.notificationfix.** { *; }
-keepattributes *Annotation*
-dontwarn de.robv.android.xposed.**
