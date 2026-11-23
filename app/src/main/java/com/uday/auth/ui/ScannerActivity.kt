package com.uday.auth.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.uday.auth.R

/**
 * Full screen QR scanner. zxing's CaptureManager does the camera and decoding work;
 * this activity only supplies our own layout around it and forwards lifecycle calls.
 *
 * Launched through zxing's ScanContract, so results come back in the format it expects.
 */
class ScannerActivity : AppCompatActivity() {

    private lateinit var capture: CaptureManager
    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var torchButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Camera preview is dark whatever the system theme, so always use light bar icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContentView(R.layout.activity_scanner)

        val topBar = findViewById<View>(R.id.top_bar)
        val bottomBar = findViewById<View>(R.id.bottom_bar)
        val topPadding = topBar.paddingTop
        val bottomPadding = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scanner_root)) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            topBar.updatePadding(left = bars.left, top = topPadding + bars.top, right = bars.right)
            bottomBar.updatePadding(left = bars.left, right = bars.right, bottom = bottomPadding + bars.bottom)
            insets
        }

        barcodeView = findViewById(R.id.barcode_view)
        torchButton = findViewById(R.id.torch)

        findViewById<MaterialButton>(R.id.close).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.enter_manually).setOnClickListener {
            setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ENTER_MANUALLY, true))
            finish()
        }

        setUpTorch()

        capture = CaptureManager(this, barcodeView)
        capture.initializeFromIntent(intent, savedInstanceState)
        capture.decode()
    }

    private fun setUpTorch() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            torchButton.isVisible = false
            return
        }

        var torchOn = false
        barcodeView.setTorchListener(object : DecoratedBarcodeView.TorchListener {
            override fun onTorchOn() {
                torchOn = true
                torchButton.setIconResource(R.drawable.ic_flash_on)
                torchButton.contentDescription = getString(R.string.turn_off_flashlight)
            }

            override fun onTorchOff() {
                torchOn = false
                torchButton.setIconResource(R.drawable.ic_flash_off)
                torchButton.contentDescription = getString(R.string.turn_on_flashlight)
            }
        })
        torchButton.setOnClickListener {
            if (torchOn) barcodeView.setTorchOff() else barcodeView.setTorchOn()
        }
    }

    override fun onResume() {
        super.onResume()
        capture.onResume()
    }

    override fun onPause() {
        super.onPause()
        capture.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        capture.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capture.onSaveInstanceState(outState)
    }

    @Deprecated("Forwarded to CaptureManager, which still uses the old permission callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        capture.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // Lets the volume keys toggle the torch, same as the stock zxing screen
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    companion object {
        const val EXTRA_ENTER_MANUALLY = "com.uday.auth.extra.ENTER_MANUALLY"
    }
}
