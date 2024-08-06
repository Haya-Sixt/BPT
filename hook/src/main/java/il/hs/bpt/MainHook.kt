package il.hs.bpt

import android.app.AndroidAppHelper
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import il.hs.bpt.hook.BaseHook
import il.hs.bpt.hook.ContextMenuHook
import il.hs.bpt.hook.PageInfoHook
import il.hs.bpt.hook.PageMenuHook
import il.hs.bpt.hook.PreferenceHook
import il.hs.bpt.hook.UserScriptHook
import il.hs.bpt.hook.WebViewHook
import il.hs.bpt.utils.Log
import il.hs.bpt.utils.hookAfter

val supportedPackages =
    arrayOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.canary",
        "com.chrome.dev",
        "com.kiwibrowser.browser",
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.canary",
        "com.microsoft.emmx.dev",
        "com.vivaldi.browser",
        "com.vivaldi.browser.snapshot")

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {

  external fun stringFromJNI(): String

  /*companion object {
    // Used to load the 'bpt' library on application startup.
    init {
      System.loadLibrary("bpt")  // err: couldn't find "libbpt.so" in nativeLibraryDirectories=[/data/app/~~6kzomz_qEv0ZA4TXkZZwGA==/il.hs.bpt-pJdm9R_hrSZOVG8BwwFcwQ==/base.apk!/lib/arm64-v8a, /system/lib64, /system_ext/lib64]]]]
    }
  }*/

  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    Log.d(lpparam.processName + " started")
    //Log.d("stringFromJNI(): " + stringFromJNI()) // cpp test

    if (lpparam.packageName == "il.hs.bpt") return
    if (supportedPackages.contains(lpparam.packageName)) {
      lpparam.classLoader
          .loadClass("org.chromium.ui.base.WindowAndroid")
          .declaredConstructors[1]
          .hookAfter {
            Chrome.init(it.args[0] as Context, lpparam.packageName)
            initHooks(UserScriptHook)
            if (ContextMenuHook.isInit) return@hookAfter
            runCatching {
              initHooks(
                PreferenceHook,
                PageMenuHook) //if (Chrome.isEdge || Chrome.isCocCoc) PageInfoHook else PageMenuHook)
              }
              .onFailure {
                initHooks(PageInfoHook)
                Log.ex(it)
              }
              .onFailure {
                initHooks(ContextMenuHook)
                Log.ex(it)
              }
          }
    } else {
      val ctx = AndroidAppHelper.currentApplication()

      Chrome.isMi =
          lpparam.packageName == "com.mi.globalbrowser" ||
              lpparam.packageName == "com.android.browser"
      Chrome.isQihoo = lpparam.packageName == "com.qihoo.contents"

      if (ctx == null && Chrome.isMi) return
      // Wait to get the browser context of Mi Browser

      if (ctx != null && lpparam.packageName != "android") Chrome.init(ctx, ctx.packageName)

      if (Chrome.isMi) {
        WebViewHook.WebView = Chrome.load("com.miui.webkit.WebView")
        WebViewHook.ViewClient = Chrome.load("com.android.browser.tab.TabWebViewClient")
        WebViewHook.ChromeClient = Chrome.load("com.android.browser.tab.TabWebChromeClient")
        hookWebView()
        return
      }

      if (Chrome.isQihoo) {
        WebViewHook.WebView = Chrome.load("com.qihoo.webkit.WebView")
        WebViewHook.ViewClient = Chrome.load("com.qihoo.webkit.WebViewClient")
        WebViewHook.ChromeClient = Chrome.load("com.qihoo.webkit.WebChromeClient")
        hookWebView()
        return
      }

      WebViewClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject::class != WebViewClient::class) {
          WebViewHook.ViewClient = it.thisObject::class.java
          hookWebView()
        }
      }

      WebChromeClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject::class != WebChromeClient::class) {
          WebViewHook.ChromeClient = it.thisObject::class.java
          hookWebView()
        }
      }
    }
  }

  private fun hookWebView() {
    if (WebViewHook.ChromeClient == null || WebViewHook.ViewClient == null) return
    if (WebViewHook.WebView == null) {
      runCatching {
            WebViewHook.WebView = WebView::class.java
            WebView.setWebContentsDebuggingEnabled(true)
          }
          .onFailure { Log.ex(it) }
    }
    initHooks(WebViewHook, ContextMenuHook)
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    Log.d("initZygote 0")
    Resource.init(startupParam.modulePath)
    Log.d("initZygote 1")
    Resource.dpi()
  }

  private fun initHooks(vararg hook: BaseHook) {
    hook.forEach {
      if (it.isInit) return@forEach
      it.init()
      if (it.isInit) Log.d("${it.javaClass.simpleName} hooked")
    }
  }
}
