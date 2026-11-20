# gomobile bindings are called from native code by name.
-keep class go.** { *; }
-keep class com.glasschat.go.** { *; }
-keepclassmembers class * implements com.glasschat.go.wa.Listener { *; }
-dontwarn go.**
# Don't fail the build over references to classes that aren't on Android
# (common in libraries); real problems still show up as compile errors.
-ignorewarnings
