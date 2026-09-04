# Tala Barcode Engine - Consumer ProGuard Rules
# Keep public API
-keep class com.tala.engine.core.BarcodeEngine { *; }
-keep class com.tala.engine.core.BarcodeEngineConfig { *; }
-keep class com.tala.engine.model.** { *; }
-keep class com.tala.engine.interfaces.** { *; }
-keep class com.tala.engine.pipeline.AutoSnappingCallback { *; }

# Keep ZXing
-keep class com.google.zxing.** { *; }

# Keep ONNX Runtime
-keep class ai.onnxruntime.** { *; }
