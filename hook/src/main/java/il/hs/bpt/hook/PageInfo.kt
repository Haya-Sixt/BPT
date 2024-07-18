package il.hs.bpt.hook

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import il.hs.bpt.Chrome
import il.hs.bpt.Listener
import il.hs.bpt.R
import il.hs.bpt.proxy.PageInfoProxy
import il.hs.bpt.proxy.UserScriptProxy
import il.hs.bpt.script.Local
import il.hs.bpt.utils.Log
import il.hs.bpt.utils.hookAfter
import il.hs.bpt.utils.invokeMethod
import il.hs.bpt.utils.isBptFrontEnd
import il.hs.bpt.utils.isChromeScheme
import il.hs.bpt.utils.isUserScript
import il.hs.bpt.utils.shouldBypassSandbox

object PageInfoHook : BaseHook() {

  override fun init() {

    if (ContextMenuHook.isInit) return
    var controller: Any? = null
    val proxy = PageInfoProxy

    fun addErudaRow(url: String): ViewGroup {
      val infoRow =
          proxy.pageInfoRowView.declaredConstructors[0].newInstance(Chrome.getContext(), null)
              as ViewGroup
      infoRow.setVisibility(View.VISIBLE)
      val icon = proxy.mIcon.get(infoRow) as ImageView
//      // ???
//      try {
//        icon.setImageResource(R.drawable.ic_devtools)
//      } catch (ex: Exception) {
//        Log.ex(ex)
//      }
      val subTitle = proxy.mSubtitle.get(infoRow) as TextView
      (subTitle.getParent() as? ViewGroup)?.removeView(subTitle)
      val title = proxy.mTitle.get(infoRow) as TextView
      if (isBptFrontEnd(url)) {
        title.setText(R.string.main_menu_developer_tools)
        infoRow.setOnClickListener {
          Listener.on("inspectPages")
          controller!!.invokeMethod() { name == "destroy" }
        }
      } else if (isUserScript(url)) {
        title.setText(R.string.main_menu_install_script)
        infoRow.setOnClickListener {
          val sandBoxed = shouldBypassSandbox(url)
          Chrome.evaluateJavascript(listOf("Symbol.installScript(true);"), null, sandBoxed)
          controller!!.invokeMethod() { name == "destroy" }
        }
      } else {
        try {
          title.setText(R.string.main_menu_eruda_console)
        }
        catch (ex: Exception) {
          title.setText("R.string.main_menu_eruda_console")
          Log.ex(ex)
        }
        infoRow.setOnClickListener {
          UserScriptProxy.evaluateJavascript(Local.openEruda)
          controller!!.invokeMethod() { name == "destroy" }
        }
      }
      return infoRow
    }

    proxy.pageInfoControllerRef.declaredConstructors[0].hookAfter { controller = it.thisObject }

    proxy.pageInfoController.declaredConstructors[0].hookAfter {
      val url = Chrome.getUrl()!!
      if (isChromeScheme(url) || controller == null) return@hookAfter
      (proxy.mRowWrapper.get(proxy.mView.get(it.thisObject)) as LinearLayout).addView(
          addErudaRow(url))
    }

    // readerMode.init(Chrome.load("org.chromium.chrome.browser.dom_distiller.ReaderModeManager"))
    isInit = true
  }
}
