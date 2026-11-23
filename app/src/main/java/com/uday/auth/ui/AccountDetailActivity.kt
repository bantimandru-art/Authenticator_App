package com.uday.auth.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.uday.auth.R
import com.uday.auth.data.Account
import com.uday.auth.data.AccountRepository
import com.uday.auth.otp.TotpGenerator
import java.text.NumberFormat

/** One account and its current code, with a countdown to the next one. */
class AccountDetailActivity : AppCompatActivity() {

    private lateinit var repository: AccountRepository
    private lateinit var account: Account

    private lateinit var code: TextView
    private lateinit var seconds: TextView
    private lateinit var expires: TextView
    private lateinit var countdown: CircularProgressIndicator

    private var primaryColor = 0
    private var errorColor = 0
    private var codeColor = 0

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            showCode(animate = true)
            // Line up with the next whole second so the countdown ticks evenly
            handler.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the code out of screenshots and the recent apps preview
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContentView(R.layout.activity_account_detail)

        repository = AccountRepository.get(this)
        val found = intent.getStringExtra(EXTRA_ACCOUNT_ID)?.let(repository::find)
        if (found == null) {
            // Can happen if the account was removed while this screen sat in the back stack
            finish()
            return
        }
        account = found

        code = findViewById(R.id.code)
        seconds = findViewById(R.id.seconds)
        expires = findViewById(R.id.expires)
        countdown = findViewById(R.id.countdown)

        primaryColor = MaterialColors.getColor(code, androidx.appcompat.R.attr.colorPrimary)
        errorColor = MaterialColors.getColor(code, androidx.appcompat.R.attr.colorError)
        codeColor = MaterialColors.getColor(code, com.google.android.material.R.attr.colorOnSurface)

        applyWindowInsets()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename -> showRenameDialog()
                R.id.action_remove -> confirmRemove()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }

        findViewById<View>(R.id.copy).setOnClickListener { copyCode() }
        code.setOnClickListener { copyCode() }

        showAccount()
        showCode(animate = false)
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(ticker)
    }

    private fun applyWindowInsets() {
        val scroll = findViewById<NestedScrollView>(R.id.scroll)
        val scrollBottom = scroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detail_root)) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            root.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            scroll.updatePadding(bottom = scrollBottom + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun showAccount() {
        val name = account.displayName
        findViewById<TextView>(R.id.avatar).showAvatar(name)
        findViewById<TextView>(R.id.issuer).text = name
        findViewById<TextView>(R.id.label).apply {
            text = account.label
            isVisible = account.issuer.isNotBlank() && account.label.isNotBlank()
        }

        findViewById<TextView>(R.id.hint).text = getString(R.string.code_hint, name)
    }

    private fun showCode(animate: Boolean) {
        val now = System.currentTimeMillis()
        val periodMillis = account.period * 1000L
        val millisLeft = (periodMillis - now % periodMillis).toInt()
        val secondsLeft = TotpGenerator.secondsRemaining(account.period, now)
        val expiring = secondsLeft <= EXPIRING_SECONDS

        code.text = formatCode(TotpGenerator.generate(account, now))
        code.setTextColor(if (expiring) errorColor else codeColor)

        seconds.text = NumberFormat.getIntegerInstance().format(secondsLeft)
        expires.text = resources.getQuantityString(R.plurals.code_refreshes_in, secondsLeft, secondsLeft)

        countdown.max = periodMillis.toInt()
        // Animate the drain, but snap straight back to full when a new code starts
        countdown.setProgressCompat(millisLeft, animate && millisLeft < countdown.progress)
        countdown.setIndicatorColor(if (expiring) errorColor else primaryColor)
    }

    private fun copyCode() {
        val clip = ClipData.newPlainText(getString(R.string.clip_label), TotpGenerator.generate(account))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Hides the code from the clipboard preview on Android 13+
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)

        // Android 13+ already shows its own confirmation when something is copied
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(findViewById(R.id.detail_root), R.string.code_copied, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showRenameDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_rename, null)
        val input = view.findViewById<TextInputEditText>(R.id.rename_input)
        input.setText(account.label)
        input.setSelection(input.length())

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_account)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != account.label) {
                    account = account.copy(label = newName)
                    repository.update(account)
                    showAccount()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        input.requestFocus()
    }

    private fun confirmRemove() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.remove_account_title, account.displayName))
            .setMessage(R.string.remove_account_message)
            .setPositiveButton(R.string.remove) { _, _ ->
                repository.remove(account.id)
                setResult(RESULT_OK, Intent().putExtra(EXTRA_REMOVED_NAME, account.displayName))
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(errorColor)
    }

    companion object {
        private const val EXTRA_ACCOUNT_ID = "com.uday.auth.extra.ACCOUNT_ID"
        const val EXTRA_REMOVED_NAME = "com.uday.auth.extra.REMOVED_NAME"
        private const val EXPIRING_SECONDS = 5

        fun intent(context: Context, account: Account): Intent =
            Intent(context, AccountDetailActivity::class.java).putExtra(EXTRA_ACCOUNT_ID, account.id)

        /** "123456" -> "123 456", "12345678" -> "1234 5678" */
        fun formatCode(code: String): String {
            val middle = code.length / 2
            return code.substring(0, middle) + " " + code.substring(middle)
        }
    }
}
