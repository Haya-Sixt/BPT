package il.hs.bpt.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import il.hs.bpt.BuildConfig
import il.hs.bpt.TAG
import java.lang.ref.WeakReference

object Log {
  private var lastToast: WeakReference<Toast>? = null

  fun i(msg: String) {
    if (!BuildConfig.DEBUG) return
    Log.i(TAG, msg)
    XposedBridge.log("Bpt logging: " + msg)
  }

  fun d(msg: String, full: Boolean = false) {
    if (!BuildConfig.DEBUG) return
    if (!full && msg.length > 300) {
      Log.d(TAG, msg.take(300) + " ...")
    } else {
      Log.d(TAG, msg)
    }
  }

  fun w(msg: String) {
    if (!BuildConfig.DEBUG) return
    Log.w(TAG, msg)
  }

  fun e(msg: String) {
    if (!BuildConfig.DEBUG) return
    Log.e(TAG, msg)
    XposedBridge.log("Bpt error: " + msg)
  }

  fun ex(thr: Throwable) {
    if (!BuildConfig.DEBUG) return
    Log.e(TAG, "", thr)
    XposedBridge.log("Bpt backtrace: " + thr.toString())
  }

  fun toast(context: Context, msg: String) {
    this.lastToast?.get()?.cancel()
    val duration = Toast.LENGTH_SHORT
    val toast = Toast.makeText(context, msg, duration)
    toast.show()
    this.lastToast = WeakReference(toast)
  }
}
