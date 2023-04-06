package bd.utsc.ac.convocation

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions

/**
 * Created by Mahfuz on 05-04-23
 * This is the main activity of the app.
 * This activity is a web view that displays the convocation website.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var noInternetLayout: RelativeLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var errorLayout: RelativeLayout
    private lateinit var retryButton: Button
    private lateinit var retryInternetButton: Button
    private var filePath: ValueCallback<Array<Uri>>? = null
    private lateinit var switchEnvironmentLayout: RelativeLayout

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* Using new Splash Screen API */
        installSplashScreen()

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        noInternetLayout = findViewById(R.id.noInternetLayout)
        progressBar = findViewById(R.id.progressBar)
        errorLayout = findViewById(R.id.errorLayout)
        retryButton = findViewById(R.id.retryButton)
        retryInternetButton = findViewById(R.id.retryInternetButton)
        switchEnvironmentLayout = findViewById(R.id.switchEnvironmentLayout)

        // check environment
        if (RuntimeData.systemEnv == Environment.STAGING) {
            switchEnvironmentLayout.visibility = View.VISIBLE
            val currentEnv: String = RuntimeData.env.name
            findViewById<TextView>(R.id.switchEnvironmentText).text =
                getString(R.string.environment, currentEnv)
            findViewById<Button>(R.id.switchEnvironmentButton).setOnClickListener {
                if (RuntimeData.env == Environment.STAGING) {
                    RuntimeData.env = Environment.PRODUCTION
                } else {
                    RuntimeData.env = Environment.STAGING
                }
                recreate()
            }
        }

        /* WebView settings */
        webView.settings.javaScriptEnabled = true
        webView.settings.setSupportZoom(false)
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.domStorageEnabled = true
        webView.settings.allowContentAccess = true
        webView.settings.allowFileAccess = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.setDownloadListener { url, _, _, _, _ ->
            val request = DownloadManager.Request(Uri.parse(url))
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
        }
        webView.webChromeClient = MyWebChromeClient()

        webView.webViewClient = MyWebViewClient()

        /* Check network and load URL */
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        // if network not available, show no internet layout
        if (connectivityManager.activeNetwork == null) {
            webView.visibility = View.GONE
            noInternetLayout.visibility = View.VISIBLE
        }
        connectivityManager.registerNetworkCallback(
            builder.build(), MyNetworkCallback()
        )

        retryButton.setOnClickListener {
            recreate()
        }

        retryInternetButton.setOnClickListener {
            recreate()
        }

        // Check for permissions
        hasPermissions()

        // Handle on Back pressed
        webView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        finish()
                    }
                    return@setOnKeyListener true
                }
            }
            return@setOnKeyListener false
        }
    }

    internal inner class MyNetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // Network is available
            runOnUiThread {
                webView.visibility = View.VISIBLE
                noInternetLayout.visibility = View.GONE
                webView.loadUrl(RuntimeData.getURL(RuntimeData.env))
            }
        }

        override fun onLost(network: Network) {
            // Network is lost
            runOnUiThread {
                webView.visibility = View.GONE
                noInternetLayout.visibility = View.VISIBLE
            }
        }

        override fun onUnavailable() {
            // Network is unavailable
            runOnUiThread {
                webView.visibility = View.GONE
                noInternetLayout.visibility = View.VISIBLE
            }
        }
    }

    internal inner class MyWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            webView.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE
            errorLayout.visibility = View.GONE
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            progressBar.visibility = View.GONE
            super.onPageFinished(view, url)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            progressBar.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE
            webView.visibility = View.GONE
            super.onReceivedError(view, request, error)
        }
    }

    internal inner class MyWebChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            if (hasPermissions()) {
                request.grant(request.resources)
            } else {
                request.deny()
            }
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            if (filePath != null) {
                filePath!!.onReceiveValue(null)
                filePath = null
            }
            filePath = filePathCallback

            //get input type
            val inputType = fileChooserParams?.acceptTypes?.get(0)
            if (inputType != null) {
                if (inputType.contains("image")) {
                    cropImage.launch(
                        CropImageContractOptions(
                            uri = null,
                            cropImageOptions = CropImageOptions(
                                imageSourceIncludeGallery = !fileChooserParams.isCaptureEnabled,
                            )
                        )
                    )
                } else {
                    // Getting Image
                    val pickImageIntent = Intent(Intent.ACTION_PICK)
                    pickImageIntent.setDataAndType(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "image/*"
                    )

                    val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
                    contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
                    contentSelectionIntent.type = "*/*"

                    val intentArray: Array<Intent?> = arrayOf(pickImageIntent)

                    val chooserIntent = Intent(Intent.ACTION_CHOOSER)
                    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                    chooserIntent.putExtra(Intent.EXTRA_TITLE, "Select File")
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)

                    fileChooserLauncher.launch(chooserIntent)
                }
            }
            return true
        }

        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progressBar.progress = newProgress
            super.onProgressChanged(view, newProgress)
        }
    }

    /// File Chooser Related Functions
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (filePath != null) {
                    val data = it.data?.data
                    if (data != null) {
                        filePath!!.onReceiveValue(
                            WebChromeClient.FileChooserParams.parseResult(
                                it.resultCode,
                                it.data
                            )
                        )
                    } else {
                        filePath!!.onReceiveValue(null)
                    }
                    filePath = null
                }
            } else {
                if (filePath != null) {
                    filePath!!.onReceiveValue(null)
                    filePath = null
                }
            }
        }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val uriContent = result.uriContent
            //val uriFilePath = result.getUriFilePath(this@WebViewActivity) // optional usage
            if (filePath != null) {
                filePath!!.onReceiveValue(arrayOf(uriContent!!))
                filePath = null
            }
        } else {
            val exception = result.error
            Log.e("CropImage", exception.toString())
            if (filePath != null) {
                filePath!!.onReceiveValue(null)
                filePath = null
            }
        }
    }


    /// Permission Related Functions

    private fun hasPermissions(): Boolean {
        var hasGranted = true
        for (permission in RuntimeData.permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d(permission, "not granted")
                hasGranted = false
            }
        }
        if (!hasGranted) {
            makeRequest(RuntimeData.permissions)
        }
        return true
    }

    private fun makeRequest(permissions: Array<String>) {
        requestPermissions(
            permissions,
            99
        )
    }
}