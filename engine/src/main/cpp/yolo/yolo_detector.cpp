#include <jni.h>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <vector>
#include <cstring>
#include <algorithm>

#define TAG "TalaYolo"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace tala {

struct Detection {
    float cx, cy, w, h;
    float confidence;
    int classId;
};

// YOLOv8 output parsing
// Input: raw model output tensor [1, 84, 8400] for 640x640 input
// 84 = 4 (bbox) + 80 (class scores) for COCO
// For barcode detection: 84 = 4 (bbox) + 1 (barcode confidence)
std::vector<Detection> parseYoloOutput(
    const float* output,
    int outputSize,
    int inputWidth, int inputHeight,
    int imageWidth, int imageHeight,
    float confThreshold = 0.5f,
    float nmsThreshold = 0.45f
) {
    std::vector<Detection> candidates;

    // YOLOv8 format: transposed output [1, 84, 8400]
    // For each of 8400 predictions:
    // output[0*8400 + i] = cx
    // output[1*8400 + i] = cy
    // output[2*8400 + i] = w
    // output[3*8400 + i] = h
    // output[4*8400 + i] = confidence (for single class)

    int numPredictions = outputSize / 4; // Simplified for single-class

    if (outputSize >= 8400 * 5) {
        // Standard YOLOv8-nano output shape
        numPredictions = 8400;
        for (int i = 0; i < numPredictions; i++) {
            float cx = output[0 * numPredictions + i];
            float cy = output[1 * numPredictions + i];
            float w  = output[2 * numPredictions + i];
            float h  = output[3 * numPredictions + i];
            float conf = output[4 * numPredictions + i];

            if (conf < confThreshold) continue;

            // Scale to image coordinates
            float scaleX = (float)imageWidth / inputWidth;
            float scaleY = (float)imageHeight / inputHeight;

            Detection det;
            det.cx = cx * scaleX;
            det.cy = cy * scaleY;
            det.w = w * scaleX;
            det.h = h * scaleX;
            det.confidence = conf;
            det.classId = 0;

            candidates.push_back(det);
        }
    }

    return candidates;
}

// Non-Maximum Suppression
std::vector<Detection> nms(
    std::vector<Detection>& detections,
    float threshold
) {
    if (detections.empty()) return {};

    // Sort by confidence descending
    std::sort(detections.begin(), detections.end(),
        [](const Detection& a, const Detection& b) {
            return a.confidence > b.confidence;
        });

    std::vector<bool> suppressed(detections.size(), false);
    std::vector<Detection> result;

    for (size_t i = 0; i < detections.size(); i++) {
        if (suppressed[i]) continue;
        result.push_back(detections[i]);

        for (size_t j = i + 1; j < detections.size(); j++) {
            if (suppressed[j]) continue;

            float interLeft = std::max(detections[i].cx - detections[i].w/2,
                                       detections[j].cx - detections[j].w/2);
            float interTop = std::max(detections[i].cy - detections[i].h/2,
                                      detections[j].cy - detections[j].h/2);
            float interRight = std::min(detections[i].cx + detections[i].w/2,
                                        detections[j].cx + detections[j].w/2);
            float interBottom = std::min(detections[i].cy + detections[i].h/2,
                                         detections[j].cy + detections[j].h/2);

            float interArea = std::max(0.0f, interRight - interLeft) *
                             std::max(0.0f, interBottom - interTop);
            float areaI = detections[i].w * detections[i].h;
            float areaJ = detections[j].w * detections[j].h;
            float unionArea = areaI + areaJ - interArea;

            float iou = unionArea > 0 ? interArea / unionArea : 0;
            if (iou > threshold) {
                suppressed[j] = true;
            }
        }
    }

    return result;
}

} // namespace tala

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_tala_engine_detect_YoloV8Detector_nativeParseOutput(
    JNIEnv* env,
    jobject thiz,
    jfloatArray modelOutput,
    jint inputWidth,
    jint inputHeight,
    jint imageWidth,
    jint imageHeight,
    jfloat confThreshold,
    jfloat nmsThreshold
) {
    jfloat* output = env->GetFloatArrayElements(modelOutput, nullptr);
    jint outputSize = env->GetArrayLength(modelOutput);

    auto detections = tala::parseYoloOutput(
        output, outputSize,
        inputWidth, inputHeight,
        imageWidth, imageHeight,
        confThreshold, nmsThreshold
    );

    env->ReleaseFloatArrayElements(modelOutput, output, JNI_ABORT);

    auto nmsResult = tala::nms(detections, nmsThreshold);

    // Pack results: [count, cx1, cy1, w1, h1, conf1, cx2, cy2, w2, h2, conf2, ...]
    int resultSize = 1 + nmsResult.size() * 5;
    jfloatArray result = env->NewFloatArray(resultSize);

    std::vector<float> packed(resultSize);
    packed[0] = (float)nmsResult.size();
    for (size_t i = 0; i < nmsResult.size(); i++) {
        packed[1 + i * 5 + 0] = nmsResult[i].cx;
        packed[1 + i * 5 + 1] = nmsResult[i].cy;
        packed[1 + i * 5 + 2] = nmsResult[i].w;
        packed[1 + i * 5 + 3] = nmsResult[i].h;
        packed[1 + i * 5 + 4] = nmsResult[i].confidence;
    }

    env->SetFloatArrayRegion(result, 0, resultSize, packed.data());
    return result;
}

} // extern "C"
