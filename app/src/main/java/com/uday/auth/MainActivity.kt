package com.uday.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.uday.auth.data.Account
import com.uday.auth.data.AccountRepository
import com.uday.auth.otp.Base32
import com.uday.auth.otp.InvalidOtpUriException
import com.uday.auth.otp.OtpAuthParser
import com.uday.auth.ui.AccountAdapter
import com.uday.auth.ui.AccountDetailActivity
import com.uday.auth.ui.ScannerActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var repository: AccountRepository
    private lateinit var adapter: AccountAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var noResults: TextView
    private lateinit var summary: TextView
    private lateinit var searchLayout: TextInputLayout
    private lateinit var searchInput: TextInputEditText
    private lateinit var addButton: ExtendedFloatingActionButton

    private var allAccounts: List<Account> = emptyList()

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        when {
            result.contents != null -> onQrCodeScanned(result.contents)
            result.originalIntent?.getBooleanExtra(ScannerActivity.EXTRA_ENTER_MANUALLY, false) == true ->
                showManualEntryDialog()
        }
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner() else onCameraPermissionDenied()
        }

    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.getStringExtra(AccountDetailActivity.EXTRA_REMOVED_NAME)?.let { name ->
                snackbar(getString(R.string.account_removed, name))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        repository = AccountRepository.get(this)

        recyclerView = findViewById(R.id.accounts)
        emptyView = findViewById(R.id.empty_view)
        noResults = findViewById(R.id.no_results)
        summary = findViewById(R.id.summary)
        searchLayout = findViewById(R.id.search_layout)
        searchInput = findViewById(R.id.search_input)
        addButton = findViewById(R.id.add_account)

        applyWindowInsets()
        setUpList()

        addButton.setOnClickListener { showAddAccountOptions() }
        findViewById<View>(R.id.add_first_account).setOnClickListener { showAddAccountOptions() }
        searchInput.doAfterTextChanged { render() }
    }

    override fun onStart() {
        super.onStart()
        // Reload every time, so renames and removals made on the detail screen show up
        loadAccounts()
    }

    private fun applyWindowInsets() {
        val emptyContent = findViewById<View>(R.id.empty_content)

        // Remember the XML values so repeated inset passes don't keep adding to them
        val listBottom = recyclerView.paddingBottom
        val emptyBottom = emptyContent.paddingBottom
        val fabMargin = (addButton.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { root, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Top and sides go on the root rather than the app bar: the app bar scrolls,
            // and its padding would scroll with it, letting the header slide under the status bar.
            root.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            recyclerView.updatePadding(bottom = listBottom + bars.bottom)
            emptyContent.updatePadding(bottom = emptyBottom + bars.bottom)
            addButton.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = fabMargin + bars.bottom }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setUpList() {
        adapter = AccountAdapter(onClick = ::openAccount)
        // One column on phones, more on tablets, foldables and landscape (see values-w600dp etc.)
        recyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.account_columns))
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) addButton.shrink() else if (dy < 0) addButton.extend()
            }
        })
    }

    private fun openAccount(account: Account) {
        detailLauncher.launch(AccountDetailActivity.intent(this, account))
    }

    private fun loadAccounts() {
        allAccounts = repository.getAll()
        render()
    }

    private fun render() {
        val query = searchInput.text?.toString().orEmpty().trim()
        val visible = if (query.isEmpty()) {
            allAccounts
        } else {
            allAccounts.filter {
                it.issuer.contains(query, ignoreCase = true) || it.label.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(visible)

        val hasAccounts = allAccounts.isNotEmpty()
        recyclerView.isVisible = hasAccounts
        searchLayout.isVisible = hasAccounts
        summary.isVisible = hasAccounts
        addButton.isVisible = hasAccounts
        emptyView.isVisible = !hasAccounts

        noResults.isVisible = hasAccounts && visible.isEmpty()
        noResults.text = getString(R.string.no_results, query)

        val count = resources.getQuantityString(R.plurals.account_count, allAccounts.size, allAccounts.size)
        summary.text = getString(R.string.accounts_summary, count)
    }

    private fun showAddAccountOptions() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_account, null)
        view.findViewById<View>(R.id.option_scan).setOnClickListener {
            dialog.dismiss()
            startScan()
        }
        view.findViewById<View>(R.id.option_manual).setOnClickListener {
            dialog.dismiss()
            showManualEntryDialog()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun startScan() {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.no_camera_title)
                .setMessage(R.string.no_camera_message)
                .setPositiveButton(R.string.enter_key) { _, _ -> showManualEntryDialog() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED -> launchScanner()

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.camera_permission_title)
                    .setMessage(R.string.camera_permission_rationale)
                    .setPositiveButton(R.string.continue_label) { _, _ ->
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton(R.string.not_now, null)
                    .show()

            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .setCaptureActivity(ScannerActivity::class.java)
        scanLauncher.launch(options)
    }

    private fun onCameraPermissionDenied() {
        // No rationale after a denial means "don't ask again", so only Settings can fix it
        val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.camera_permission_title)
            .setMessage(R.string.camera_permission_denied)
            .setNegativeButton(R.string.enter_key) { _, _ -> showManualEntryDialog() }

        if (permanentlyDenied) {
            builder.setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                )
            }
        } else {
            builder.setPositiveButton(R.string.try_again) { _, _ -> startScan() }
        }
        builder.show()
    }

    private fun onQrCodeScanned(contents: String) {
        val account = try {
            OtpAuthParser.parse(contents)
        } catch (e: InvalidOtpUriException) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.invalid_qr_title)
                .setMessage(e.message)
                .setPositiveButton(R.string.try_again) { _, _ -> startScan() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        addAccount(account)
    }

    private fun addAccount(account: Account) {
        if (repository.contains(account)) {
            snackbar(getString(R.string.account_already_added))
            return
        }
        repository.add(account)
        searchInput.text = null
        searchInput.clearFocus()
        loadAccounts()
        recyclerView.scrollToPosition(0)
        snackbar(getString(R.string.account_added, account.displayName))
    }

    private fun showManualEntryDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_manual_entry, null)
        val issuerInput = view.findViewById<TextInputEditText>(R.id.issuer_input)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.account_name_layout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.account_name_input)
        val secretLayout = view.findViewById<TextInputLayout>(R.id.secret_layout)
        val secretInput = view.findViewById<TextInputEditText>(R.id.secret_input)

        nameInput.doAfterTextChanged { nameLayout.error = null }
        secretInput.doAfterTextChanged { secretLayout.error = null }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.enter_key)
            .setView(view)
            .setPositiveButton(R.string.add, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        // Set the listener after show() so a validation error doesn't close the dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val issuer = issuerInput.text.toString().trim()
            val name = nameInput.text.toString().trim()
            val secret = secretInput.text.toString()

            if (name.isEmpty()) nameLayout.error = getString(R.string.error_account_name_required)
            if (!Base32.isValid(secret)) secretLayout.error = getString(R.string.error_invalid_secret)
            if (nameLayout.error != null || secretLayout.error != null) return@setOnClickListener

            addAccount(
                Account(
                    id = UUID.randomUUID().toString(),
                    issuer = issuer,
                    label = name,
                    secret = Base32.normalize(secret),
                )
            )
            dialog.dismiss()
        }
    }

    private fun snackbar(message: String) {
        Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_SHORT)
            .apply { if (addButton.isVisible) anchorView = addButton }
            .show()
    }
}
