package com.tala.demo

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tala.engine.core.BarcodeEngine
import com.tala.engine.core.BarcodeEngineConfig
import com.tala.engine.interfaces.ScanCallback
import com.tala.engine.interfaces.ScanError
import com.tala.engine.model.BarcodeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryScannerActivity : AppCompatActivity() {

    private lateinit var engine: BarcodeEngine
    private lateinit var previewView: PreviewView
    private lateinit var tvStats: TextView
    private lateinit var tvScanRate: TextView
    private lateinit var tvLastBarcode: TextView
    private lateinit var tvInventoryProgress: TextView
    private lateinit var progressInventory: ProgressBar
    private lateinit var btnReset: Button
    private lateinit var btnExport: Button

    private val scannedBarcodes = mutableListOf<BarcodeResult>()
    private var frameCount = 0L

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory_scanner)

        previewView = findViewById(R.id.previewView)
        tvStats = findViewById(R.id.tvStats)
        tvScanRate = findViewById(R.id.tvScanRate)
        tvLastBarcode = findViewById(R.id.tvLastBarcode)
        tvInventoryProgress = findViewById(R.id.tvInventoryProgress)
        progressInventory = findViewById(R.id.progressInventory)
        btnReset = findViewById(R.id.btnReset)
        btnExport = findViewById(R.id.btnExport)

        // Configure engine for small barcode labels on jewelry
        // forSmallBarcodes(): لیبل‌های کوچک (گوشواره، النگو، گردنبند)
        // forGoldInventory(): اسکن سریع انبار
        engine = BarcodeEngine(
            context = this,
            config = BarcodeEngineConfig.forSmallBarcodes()
        )

        // Set callback
        engine.setCallback(object : ScanCallback {
            override fun onBarcodeConfirmed(result: BarcodeResult) {
                runOnUiThread {
                    onNewBarcodeScanned(result)
                }
            }

            override fun onFrameProcessed(detectedBarcodes: List<BarcodeResult>, frameProcessingTimeMs: Long) {
                frameCount++
                if (frameCount % 10 == 0L) {
                    runOnUiThread {
                        val stats = engine.getPerformanceStats()
                        tvStats.text = "Latency: ${frameProcessingTimeMs}ms | Active: ${engine.getSessionResults().size}"
                    }
                }
            }

            override fun onError(error: ScanError) {
                runOnUiThread {
                    Toast.makeText(this@InventoryScannerActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        })

        // Observe session results
        lifecycleScope.launch {
            engine.sessionResults.collectLatest { results ->
                withContext(Dispatchers.Main) {
                    updateInventoryCount(results.size)
                }
            }
        }

        // Observe individual barcodes
        lifecycleScope.launch {
            engine.barcodeFlow.collectLatest { result ->
                withContext(Dispatchers.Main) {
                    tvLastBarcode.text = "${result.format.displayName}: ${result.value}"
                    tvLastBarcode.setTextColor(ContextCompat.getColor(this@InventoryScannerActivity, android.R.color.holo_green_light))
                }
            }
        }

        // Button handlers
        btnReset.setOnClickListener {
            engine.resetSession()
            scannedBarcodes.clear()
            updateInventoryCount(0)
            tvLastBarcode.text = "Reset - Start scanning..."
            tvLastBarcode.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            Toast.makeText(this, "Session reset", Toast.LENGTH_SHORT).show()
        }

        btnExport.setOnClickListener {
            exportInventoryList()
        }

        // Check camera permission
        if (hasCameraPermission()) {
            startEngine()
        } else {
            requestCameraPermission()
        }
    }

    private fun startEngine() {
        lifecycleScope.launch {
            try {
                engine.initialize()
                engine.startScanning(this@InventoryScannerActivity, previewView)
                tvLastBarcode.text = " ready! Point camera at barcodes..."
                tvLastBarcode.setTextColor(ContextCompat.getColor(this@InventoryScannerActivity, android.R.color.holo_green_light))
            } catch (e: Exception) {
                Toast.makeText(this@InventoryScannerActivity, "Failed to start: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onNewBarcodeScanned(result: BarcodeResult) {
        // Check if already scanned
        val existing = scannedBarcodes.find {
            it.value == result.value && it.format == result.format
        }

        if (existing == null) {
            scannedBarcodes.add(result)
            val count = scannedBarcodes.size

            tvScanRate.text = "Scanned: $count items"
            progressInventory.progress = count.coerceAtMost(progressInventory.max)

            // Vibrate for feedback
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun updateInventoryCount(count: Int) {
        tvInventoryProgress.text = "$count items scanned"
        progressInventory.progress = count.coerceAtMost(progressInventory.max)
        tvScanRate.text = "Scanned: $count items"
    }

    private fun exportInventoryList() {
        if (scannedBarcodes.isEmpty()) {
            Toast.makeText(this, "No barcodes scanned yet", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        sb.appendLine("=== Tala Inventory Export ===")
        sb.appendLine("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("Total items: ${scannedBarcodes.size}")
        sb.appendLine()
        sb.appendLine("No.|Format|Value")
        sb.appendLine("---|------|-----")

        scannedBarcodes.forEachIndexed { index, result ->
            sb.appendLine("${index + 1}|${result.format.displayName}|${result.value}")
        }

        // Copy to clipboard
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Inventory List", sb.toString())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, "Inventory copied to clipboard (${scannedBarcodes.size} items)", Toast.LENGTH_LONG).show()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startEngine()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        engine.pause()
    }

    override fun onResume() {
        super.onResume()
        engine.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.shutdown()
    }
}
