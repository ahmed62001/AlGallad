# Firestore data models. The app maps snapshots manually (DocumentSnapshot.toProduct()
# etc.), but @Keep + keeping the data classes makes any reflective/POJO path R8-safe and
# prevents fields from being stripped/renamed to null in release. @Keep annotations are on
# the classes in data/Models.kt; this belt-and-suspenders rule keeps them + members too.
-keep class com.algallad.store.data.** { *; }

# Attributes required by Firestore (generic collection types), Firebase Auth, Compose and
# any reflective/serialization path. Without *Annotation* Firestore field metadata and
# without Signature generic List<OrderItem> types break (fields become null in release).
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
# Kotlin metadata (data-class copy/componentN, coroutines internals via ServiceLoader).
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-dontwarn kotlinx.coroutines.**
-keep class kotlin.Metadata { *; }

# Enums are looked up by name()/valueOf() (e.g. PriceCheckResult, ImageFailure, ThemeMode,
# AuthErrorScope). Keep values()/valueOf() so enum fields don't become null after R8.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Firebase Auth + Firestore SDKs ship their own consumer rules; keep annotations-bearing
# model types only (no blanket keep of the whole SDK — verify.sh forbids that).
-keep class com.google.firebase.firestore.PropertyName { *; }
-keep class com.google.firebase.firestore.Exclude { *; }
-keep class com.google.firebase.firestore.IgnoreExtraProperties { *; }
-keep class com.google.firebase.firestore.ThrowOnExtraProperties { *; }
-keep class com.google.firebase.firestore.DocumentId { *; }
-keep class com.google.firebase.firestore.ServerTimestamp { *; }

# Compose runtime reflection (savedInstanceState/rememberSaveable, movement).
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# DataStore Preferences: keys are referenced by name reflectively by the framework.
-keep class androidx.datastore.preferences.core.** { *; }
-dontwarn androidx.datastore.**

# Strip verbose/debug/info logging in release (assume no side effects -> removed by R8).
# Log.w/Log.e are intentionally kept for crash diagnostics.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

