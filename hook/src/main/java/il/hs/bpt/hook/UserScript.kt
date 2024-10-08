package il.hs.bpt.hook

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.http.HttpResponseCache
import il.hs.bpt.BuildConfig
import il.hs.bpt.Chrome
import il.hs.bpt.Listener
import il.hs.bpt.proxy.UserScriptProxy
import il.hs.bpt.script.Local
import il.hs.bpt.script.ScriptDbManager
import il.hs.bpt.utils.Log
import il.hs.bpt.utils.findField
import il.hs.bpt.utils.findMethod
import il.hs.bpt.utils.findMethodOrNull
import il.hs.bpt.utils.hookAfter
import il.hs.bpt.utils.hookBefore

object UserScriptHook : BaseHook() {

  override fun init() {

    val proxy = UserScriptProxy

    // proxy.tabModelJniBridge.declaredConstructors[0].hookAfter {
    //   Chrome.addTabModel(it.thisObject)
    // }

    // findMethod(proxy.tabModelJniBridge) { name == "destroy" }
    //     .hookBefore { Chrome.dropTabModel(it.thisObject) }

    if (Chrome.isSamsung) {
      findMethodOrNull(proxy.tabWebContentsDelegateAndroidImpl) { name == "onDidFinishNavigation" }
          .let {
            if (it == null)
                findMethod(proxy.tabWebContentsDelegateAndroidImpl) {
                  name == "updateBrowserControlsState"
                }
            else it
          }
          .hookAfter { Chrome.updateTab(it.thisObject) }

      runCatching {
        // Avoid exceptions thrown due to signature conficts while binding services
        val ConnectionManager =
            Chrome.load("com.samsung.android.sdk.scs.base.connection.ConnectionManager")
        val mServiceConnection =
            findField(ConnectionManager) { name == "mServiceConnection" }
                .also { it.isAccessible = true }

        findMethod(ConnectionManager) { name == "connectToService" }
            // (Landroid/content/Context;Landroid/content/Intent;)Z
            .hookBefore {
              val hook = it
              val ctx = hook.args[0] as Context
              val intent = hook.args[1] as Intent
              val connection = mServiceConnection.get(hook.thisObject) as ServiceConnection
              runCatching {
                    if (BuildConfig.DEBUG) Log.d("Binding service ${intent} with ${ctx}")
                    val bound = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    hook.result = bound
                  }
                  .onFailure {
                    if (BuildConfig.DEBUG) Log.ex(it)
                    hook.result = false
                  }
            }
      }
    }

    findMethod(if (Chrome.isSamsung) proxy.tabImpl else proxy.tabWebContentsDelegateAndroidImpl) {
          name == "onUpdateUrl"
        }
        // public void onUpdateUrl(GURL url)
        .hookAfter {
          val tab = proxy.getTab(it.thisObject)!!
          if (!Chrome.isSamsung) Chrome.updateTab(tab)
          val url = proxy.parseUrl(it.args[0])!!
          val isLoading = proxy.mIsLoading.get(tab) as Boolean
          if (!url.startsWith("chrome") && isLoading) {
//              ???
//              try {
//                  ScriptDbManager.invokeScript(url)
//              }
//              catch (e: Exception) {
//                Log.ex(e)
//              }
          }
        }

    findMethod(proxy.tabWebContentsDelegateAndroidImpl) {
          name == if (Chrome.isSamsung) "onAddMessageToConsole" else "addMessageToConsole"
        }
        // public boolean addMessageToConsole(int level, String message, int lineNumber,
        // String sourceId)
        .hookAfter {
          // This should be the way to communicate with the front-end of Bpt
          val lineNumber = it.args[2] as Int
          val sourceId = it.args[3] as String
          if (it.args[0] as Int == 0 &&
              sourceId == "local://Bpt/init" &&
              lineNumber == Local.anchorInBpt) {
            Listener.startAction(it.args[1] as String, proxy.getTab(it.thisObject))
          } else {
            Log.d(
                when (it.args[0] as Int) {
                  0 -> "D"
                  2 -> "W"
                  3 -> "E"
                  else -> "V"
                } + ": [${sourceId}@${lineNumber}] ${it.args[1]}")
          }
        }

    findMethod(proxy.navigationControllerImpl) {
          name == "loadUrl" || parameterTypes contentDeepEquals arrayOf(proxy.loadUrlParams)
        }
        // public void loadUrl(LoadUrlParams params)
        .hookBefore {
          val url = proxy.parseUrl(it.args[0])!!
          proxy.userAgentHook(url, it.args[0])
        }

    findMethod(proxy.chromeTabbedActivity, true) { name == "onResume" }
        .hookBefore { Chrome.init(it.thisObject as Context) }

    findMethod(proxy.chromeTabbedActivity) { name == "onStop" }
        .hookBefore {
          ScriptDbManager.updateScriptStorage()
          val cache = HttpResponseCache.getInstalled()
          Log.d("HttpResponseCache: Hit ${cache.hitCount} / NetWork ${cache.networkCount}")
          cache.flush()
        }
    isInit = true
  }
}
