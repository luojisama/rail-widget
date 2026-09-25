# Proguard rules for RailWidget
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
