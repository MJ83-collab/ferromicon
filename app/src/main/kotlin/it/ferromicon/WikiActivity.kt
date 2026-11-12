package it.ferromicon

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

/** Wiki Ferrovia: la pagina sta dentro l'app (assets/wiki.html) e funziona senza connessione. */
class WikiActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Wiki Ferrovia"
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/wiki.html")
    }
}
