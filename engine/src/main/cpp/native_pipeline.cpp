#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstring>

#define TAG "TalaNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Decode functions declared in zxing_wrapper.cpp
extern "C" {
    JNIEXPORT jobjectArray JNICALL
    Java_com_tala_engine_decode_MultiFormatDecoder_nativeDecodeGray(
        JNIEnv *env,
        jobject thiz,
        jbyteArray grayData,
        jint width,
        jint height,
        jint formatHint,
        jboolean enablePreprocessing
    );
}

// Preprocessing functions declared in preprocessing files
namespace tala {

struct BarcodeRegion {
    int left, top, width, height;
    float confidence;
};

// Adaptive threshold using Sauvola's method
void sauvolaThreshold(
    const uint8_t* gray, uint8_t* binary,
    int width, int height,
    int windowSize = 15, float k = 0.2f, float R = 128.0f
) {
    // Integral image for fast mean computation
    std::vector<double> integral((width + 1) * (height + 1), 0.0);
    std::vector<double> integralSq((width + 1) * (height + 1), 0.0);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            double val = gray[y * width + x];
            integral[(y + 1) * (width + 1) + (x + 1)] = val
                + integral[y * (width + 1) + (x + 1)]
                + integral[(y + 1) * (width + 1) + x]
                - integral[y * (width + 1) + x];

            integralSq[(y + 1) * (width + 1) + (x + 1)] = val * val
                + integralSq[y * (width + 1) + (x + 1)]
                + integralSq[(y + 1) * (width + 1) + x]
                - integralSq[y * (width + 1) + x];
        }
    }

    int halfWin = windowSize / 2;

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int y1 = std::max(0, y - halfWin);
            int y2 = std::min(height - 1, y + halfWin);
            int x1 = std::max(0, x - halfWin);
            int x2 = std::min(width - 1, x + halfWin);

            int area = (y2 - y1 + 1) * (x2 - x1 + 1);

            double sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)]
                - integral[y1 * (width + 1) + (x2 + 1)]
                - integral[(y2 + 1) * (width + 1) + x1]
                + integral[y1 * (width + 1) + x1];

            double sumSq = integralSq[(y2 + 1) * (width + 1) + (x2 + 1)]
                - integralSq[y1 * (width + 1) + (x2 + 1)]
                - integralSq[(y2 + 1) * (width + 1) + x1]
                + integralSq[y1 * (width + 1) + x1];

            double mean = sum / area;
            double variance = (sumSq / area) - (mean * mean);
            double stddev = std::sqrt(std::max(0.0, variance));

            double threshold = mean * (1.0 + k * (stddev / R - 1.0));

            binary[y * width + x] = (gray[y * width + x] > threshold) ? 255 : 0;
        }
    }
}

// CLAHE (Contrast Limited Adaptive Histogram Equalization)
void clahe(
    const uint8_t* gray, uint8_t* output,
    int width, int height,
    int clipLimit = 3
) {
    int tileSize = 8;
    int tilesX = (width + tileSize - 1) / tileSize;
    int tilesY = (height + tileSize - 1) / tileSize;

    // For simplicity, use full-image histogram equalization with clipping
    int histogram[256] = {0};
    for (int i = 0; i < width * height; i++) {
        histogram[gray[i]]++;
    }

    // Clip histogram
    int clipPixels = clipLimit * width * height / 256;
    int excess = 0;
    for (int i = 0; i < 256; i++) {
        if (histogram[i] > clipPixels) {
            excess += histogram[i] - clipPixels;
            histogram[i] = clipPixels;
        }
    }

    // Redistribute excess
    int avgInc = excess / 256;
    for (int i = 0; i < 256; i++) {
        histogram[i] += avgInc;
    }

    // Build LUT
    int lut[256];
    int cumulative = 0;
    for (int i = 0; i < 256; i++) {
        cumulative += histogram[i];
        lut[i] = std::min(255, (cumulative * 255) / (width * height));
    }

    // Apply LUT
    for (int i = 0; i < width * height; i++) {
        output[i] = lut[gray[i]];
    }
}

} // namespace tala

extern "C" {

JNIEXPORT void JNICALL
Java_com_tala_engine_decode_MultiFormatDecoder_nativeSauvolaThreshold(
    JNIEnv *env, jobject thiz,
    jbyteArray inputGray, jbyteArray outputBinary,
    jint width, jint height
) {
    jbyte* gray = env->GetByteArrayElements(inputGray, nullptr);
    jbyte* binary = env->GetByteArrayElements(outputBinary, nullptr);

    tala::sauvolaThreshold(
        reinterpret_cast<const uint8_t*>(gray),
        reinterpret_cast<uint8_t*>(binary),
        width, height
    );

    env->ReleaseByteArrayElements(inputGray, gray, JNI_ABORT);
    env->ReleaseByteArrayElements(outputBinary, binary, 0);
}

JNIEXPORT void JNICALL
Java_com_tala_engine_decode_MultiFormatDecoder_nativeClahe(
    JNIEnv *env, jobject thiz,
    jbyteArray inputGray, jbyteArray outputGray,
    jint width, jint height
) {
    jbyte* gray = env->GetByteArrayElements(inputGray, nullptr);
    jbyte* output = env->GetByteArrayElements(outputGray, nullptr);

    tala::clahe(
        reinterpret_cast<const uint8_t*>(gray),
        reinterpret_cast<uint8_t*>(output),
        width, height
    );

    env->ReleaseByteArrayElements(inputGray, gray, JNI_ABORT);
    env->ReleaseByteArrayElements(outputGray, output, 0);
}

} // extern "C"
