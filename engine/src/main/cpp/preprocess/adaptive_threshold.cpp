#include <cstdint>
#include <cmath>
#include <algorithm>
#include <vector>

namespace tala {

void adaptiveThreshold(
    const uint8_t* input, uint8_t* output,
    int width, int height,
    int blockSize = 15, double C = 10.0
) {
    // Mean-based adaptive threshold
    std::vector<double> integral((width + 1) * (height + 1), 0.0);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            integral[(y + 1) * (width + 1) + (x + 1)] = input[y * width + x]
                + integral[y * (width + 1) + (x + 1)]
                + integral[(y + 1) * (width + 1) + x]
                - integral[y * (width + 1) + x];
        }
    }

    int half = blockSize / 2;

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int y1 = std::max(0, y - half);
            int y2 = std::min(height - 1, y + half);
            int x1 = std::max(0, x - half);
            int x2 = std::min(width - 1, x + half);

            int area = (y2 - y1 + 1) * (x2 - x1 + 1);

            double sum = integral[(y2 + 1) * (width + 1) + (x2 + 1)]
                - integral[y1 * (width + 1) + (x2 + 1)]
                - integral[(y2 + 1) * (width + 1) + x1]
                + integral[y1 * (width + 1) + x1];

            double mean = sum / area;

            output[y * width + x] = (input[y * width + x] > (mean - C)) ? 255 : 0;
        }
    }
}

} // namespace tala
