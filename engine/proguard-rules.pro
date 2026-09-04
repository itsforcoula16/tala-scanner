# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep engine public API
-keep public class com.tala.engine.core.BarcodeEngine { *; }
-keep public class com.tala.engine.core.BarcodeEngineConfig { *; }
-keep public class com.tala.engine.model.** { *; }
-keep public class com.tala.engine.interfaces.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
