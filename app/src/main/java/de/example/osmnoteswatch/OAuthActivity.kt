package de.example.osmnoteswatch

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

class OAuthActivity : Activity() {
    private val redirect = "http://127.0.0.1:17321/callback"
    private lateinit var verifier: String

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.OSM_CLIENT_ID.isBlank()) {
            Toast.makeText(this, "OSM_CLIENT_ID fehlt in local.properties", Toast.LENGTH_LONG).show()
            finish(); return
        }
        verifier = randomVerifier()
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val url = "https://www.openstreetmap.org/oauth2/authorize" +
            "?response_type=code&client_id=${OsmApi.enc(BuildConfig.OSM_CLIENT_ID)}" +
            "&redirect_uri=${OsmApi.enc(redirect)}&scope=write_notes" +
            "&code_challenge=${OsmApi.enc(challenge)}&code_challenge_method=S256"

        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url ?: return false
                if (u.toString().startsWith(redirect)) {
                    val code = u.getQueryParameter("code")
                    if (code != null) exchange(code) else fail("Kein OAuth-Code erhalten")
                    return true
                }
                return false
            }
        }
        setContentView(web)
        web.loadUrl(url)
    }

    private fun exchange(code: String) {
        Thread {
            try {
                val body = OsmApi.request(
                    "https://www.openstreetmap.org/oauth2/token", "POST",
                    mapOf(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "redirect_uri" to redirect,
                        "client_id" to BuildConfig.OSM_CLIENT_ID,
                        "code_verifier" to verifier
                    )
                )
                val token = JSONObject(body).getString("access_token")
                getSharedPreferences("auth", MODE_PRIVATE).edit().putString("token", token).apply()
                runOnUiThread {
                    Toast.makeText(this, "OSM-Login erfolgreich", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK); finish()
                }
            } catch (e: Exception) { fail(e.message ?: "OAuth fehlgeschlagen") }
        }.start()
    }

    private fun fail(msg: String) = runOnUiThread {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); setResult(RESULT_CANCELED); finish()
    }

    private fun randomVerifier(): String {
        val b = ByteArray(48); SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
