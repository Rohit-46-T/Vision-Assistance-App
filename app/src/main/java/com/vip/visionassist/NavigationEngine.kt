package com.vip.visionassist

class NavigationEngine {

    companion object {
        const val STOP_LEVEL = 0.75f
        const val CLEAR_LEVEL = 0.45f
    }

    enum class NavCommand {
        GO_STRAIGHT, MOVE_LEFT, MOVE_RIGHT, STOP_PATH_BLOCKED, STOP_OBSTACLE
    }

    data class NavResult(
        val command: NavCommand,
        val message: String,
        val criticalHazard: Boolean = false,
        val hazardLabel: String = ""
    )

    fun analyze(
        depthMap: Array<FloatArray>,
        detections: List<YoloDetector.Detection>
    ): NavResult {

        val h = depthMap.size
        val w = if (h > 0) depthMap[0].size else 0

        if (h == 0 || w == 0) {
            return NavResult(NavCommand.STOP_PATH_BLOCKED, "Unable to read depth")
        }

        // Normalize depth map to 0-1
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        for (row in depthMap) {
            for (v in row) {
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
        }
        val range = maxVal - minVal
        val normDepth = Array(h) { r ->
            FloatArray(w) { c ->
                if (range > 0) (depthMap[r][c] - minVal) / range else 0f
            }
        }

        // Define ROI: middle 40%-90% of frame height
        val roiYStart = (h * 0.4).toInt()
        val roiYEnd   = (h * 0.9).toInt()
        val colW = w / 3

        // Get 90th percentile depth for each column zone
        val lVals = mutableListOf<Float>()
        val cVals = mutableListOf<Float>()
        val rVals = mutableListOf<Float>()

        for (r in roiYStart until roiYEnd) {
            for (c in 0 until colW)       lVals.add(normDepth[r][c])
            for (c in colW until 2*colW)  cVals.add(normDepth[r][c])
            for (c in 2*colW until w)     rVals.add(normDepth[r][c])
        }

        val lVal = percentile90(lVals)
        val cVal = percentile90(cVals)
        val rVal = percentile90(rVals)

        // Check YOLO detections for critical close obstacles
        for (det in detections) {
            val boxCenterX = (det.x1 + det.x2) / 2f
            val boxCenterY = (det.y1 + det.y2) / 2f

            // Map box center to depth map coordinates
            val depthX = ((boxCenterX / 320f) * w).toInt().coerceIn(0, w - 1)
            val depthY = ((boxCenterY / 320f) * h).toInt().coerceIn(0, h - 1)

            val objDepth = normDepth[depthY][depthX]

            // Object is very close and in the central path
            if (objDepth > STOP_LEVEL && boxCenterX > w * 0.25f && boxCenterX < w * 0.75f) {
                return NavResult(
                    command = NavCommand.STOP_OBSTACLE,
                    message = "Stop! ${det.label} ahead",
                    criticalHazard = true,
                    hazardLabel = det.label
                )
            }
        }

        // Grid-based navigation decision
        return when {
            cVal < CLEAR_LEVEL -> NavResult(NavCommand.GO_STRAIGHT, "Go straight")
            lVal < CLEAR_LEVEL -> NavResult(NavCommand.MOVE_LEFT,   "Move left")
            rVal < CLEAR_LEVEL -> NavResult(NavCommand.MOVE_RIGHT,  "Move right")
            else               -> NavResult(NavCommand.STOP_PATH_BLOCKED, "Stop, path blocked")
        }
    }

    private fun percentile90(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val idx = (sorted.size * 0.9).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}