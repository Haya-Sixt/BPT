package il.hs.bpt.hook

import android.app.Activity
import android.os.Build
import android.os.Handler
import java.lang.ref.WeakReference
import il.hs.bpt.Chrome
import il.hs.bpt.Listener
import il.hs.bpt.script.Local
import il.hs.bpt.script.ScriptDbManager
import il.hs.bpt.utils.Log
import il.hs.bpt.utils.findField
import il.hs.bpt.utils.findMethod
import il.hs.bpt.utils.hookAfter
import il.hs.bpt.utils.hookBefore
import il.hs.bpt.utils.invokeMethod

object WebViewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null
  var WebView: Class<*>? = null
  val records = mutableListOf<WeakReference<Any>>()

  fun evaluateJavascript(code: String?, view: Any?) {
    val webView = (view ?: Chrome.getTab())
    if (code != null && code.length > 0 && webView != null) {
      val webSettings = webView.invokeMethod { name == "getSettings" }
      if (webSettings?.invokeMethod { name == "getJavaScriptEnabled" } == true)
          Handler(Chrome.getContext().mainLooper).post {
            webView.invokeMethod(code, null) { name == "evaluateJavascript" }
          }
    }
  }

  override fun init() {

    findMethod(ChromeClient!!, true) { name == "onConsoleMessage" && parameterCount == 1 }
        // public boolean onConsoleMessage (ConsoleMessage consoleMessage)
        .hookAfter {
          // Don't use ConsoleMessage to specify this method since Mi Browser uses its own
          // implementation
          // This should be the way to communicate with the front-end of Bpt
          val chromeClient = it.thisObject
          val consoleMessage = it.args[0]
          val messageLevel = consoleMessage.invokeMethod { name == "messageLevel" }
          val sourceId = consoleMessage.invokeMethod { name == "sourceId" }
          val lineNumber = consoleMessage.invokeMethod { name == "lineNumber" }
          val message = consoleMessage.invokeMethod { name == "message" } as String
          if (messageLevel.toString() == "TIP" &&
              sourceId == "local://Bpt/init" &&
              lineNumber == Local.anchorInBpt) {
            val webView =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  records
                      .find {
                        if (Chrome.isQihoo) {
                              val mProvider = findField(WebView!!) { name == "mProvider" }
                              mProvider.get(it.get())
                            } else {
                              it.get()
                            }
                            ?.invokeMethod { name == "getWebChromeClient" } == chromeClient
                      }
                      ?.get()
                } else Chrome.getTab()
            Listener.startAction(message, webView)
          } else {
            Log.d(messageLevel.toString() + ": [${sourceId}@${lineNumber}] ${message}")
          }
        }

    fun onUpdateUrl(url: String, view: Any?) {
      if (url.startsWith("javascript") || view == null) return
      Chrome.updateTab(view)
      ScriptDbManager.invokeScript(url, view)
    }

    findMethod(WebView!!) { name == "setWebChromeClient" }
        .hookAfter {
          val webView = it.thisObject
          records.removeAll(records.filter { it.get() == null || it.get() == webView })
          if (it.args[0] != null) records.add(WeakReference(webView))
        }

    findMethod(WebView!!) { name == "onAttachedToWindow" }
        .hookAfter { Chrome.updateTab(it.thisObject) }

    findMethod(ViewClient!!, true) { name == "onPageStarted" }
        // public void onPageStarted (WebView view, String url, Bitmap favicon)
        .hookAfter {
          if (Chrome.isQihoo && it.thisObject::class.java.declaredMethods.size > 1) return@hookAfter
          onUpdateUrl(it.args[1] as String, it.args[0])
        }

    findMethod(Activity::class.java) { name == "onStop" }
        .hookBefore { ScriptDbManager.updateScriptStorage() }
    isInit = true
  }
}
