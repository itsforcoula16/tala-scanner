#include <cstdint>
#include <cmath>
#include <algorithm>
#include <vector>

namespace tala {

// Enhance barcode region for better decoding
void enhanceBarcodeRegion(
    const uint8_t* input, uint8_t* output,
    int width, int height
) {
    // 1. Local contrast enhancement
    int blockSize = 31;
    int half = blockSize / 2;

    // Compute local min/max using integral images
    std::vector<int> integral(width * height);
    std::vector<int> integralSq(width * height);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int val = input[y * width + x];
            integral[y * width + x] = val
                + (y > 0 ? integral[(y - 1) * width + x] : 0)
                + (x > 0 ? integral[y * width + (x - 1)] : 0)
                - (y > 0 && x > 0 ? integral[(y - 1) * width + (x - 1)] : 0);

            integralSq[y * width + x] = val * val
                + (y > 0 ? integralSq[(y - 1) * width + x] : 0)
                + (x > 0 ? integralSq[y * width + (x - 1)] : 0)
                - (y > 0 && x > 0 ? integralSq[(y - 1) * width + (x - 1)] : 0);
        }
    }

    // Apply local normalization
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int y1 = std::max(0, y - half);
            int y2 = std::min(height - 1, y + half);
            int x1 = std::max(0, x - half);
            int x2 = std::min(width - 1, x + half);

            int area = (y2 - y1 + 1) * (x2 - x1 + 1);

            int sum = integral[y2 * width + x2]
                - (y1 > 0 ? integral[(y1 - 1) * width + x2] : 0)
                - (x1 > 0 ? integral[y2 * width + (x1 - 1)] : 0)
                + (y1 > 0 && x1 > 0 ? integral[(y1 - 1) * width + (x1 - 1)] : 0);

            int sumSq = integralSq[y2 * width + x2]
                - (y1 > 0 ? integralSq[(y1 - 1) * width + x2] : 0)
                - (x1 > 0 ? integralSq[y2 * width + (x1 - 1)] : 0)
                + (y1 > 0 && x1 > 0 ? integralSq[(y1 - 1) * width + (x1 - 1)] : 0);

            double mean = (double)sum / area;
            double variance = (double)sumSq / area - mean * mean;
            double stddev = std::sqrt(std::max(0.0, variance));

            // Normalize to [0, 255] using local statistics
            if (stddev > 1.0) {
                double normalized = ((input[y * width + x] - mean) / stddev) * 64.0 + 128.0;
                output[y * width + x] = (uint8_t)std::max(0.0, std::min(255.0, normalized));
            } else {
                output[y * width + x] = input[y * width + x];
            }
        }
    }
}

// Sharpen barcode edges
void sharpenEdges(
    const uint8_t* input, uint8_t* output,
    int width, int height
) {
    // Laplacian sharpening kernel: [0, -1, 0; -1, 5, -1; 0, -1, 0]
    for (int y = 1; y < height - 1; y++) {
        for (int x = 1; x < width - 1; x++) {
            int center = input[y * width + x] * 5;
            int neighbors =
                input[(y - 1) * width + x] +
                input[(y + 1) * width + x] +
                input[y * width + (x - 1)] +
                input[y * width + (x + 1)];

            int result = center - neighbors;
            output[y * width + x] = (uint8_t)std::max(0, std::min(255, result));
        }
    }

    // Copy edges
    for (int x = 0; x < width; x++) {
        output[x] = input[x];
        output[(height - 1) * width + x] = input[(height - 1) * width + x];
    }
    for (int y = 0; y < height; y++) {
        output[y * width] = input[y * width];
        output[y * width + (width - 1)] = input[y * width + (width - 1)];
    }
}

} // namespace tala
