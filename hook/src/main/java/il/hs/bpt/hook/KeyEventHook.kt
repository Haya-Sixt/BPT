package il.hs.bpt.hook

import android.view.KeyEvent
import de.robv.android.xposed.XposedBridge
import il.hs.bpt.Chrome
import il.hs.bpt.utils.findMethod
import il.hs.bpt.utils.hookBefore


object KeyEventHook : BaseHook() {
    override fun init() {
        try {
            //disable alt-tab
            //this works for any android version
            findMethod(Chrome.load("com.android.server.policy.PhoneWindowManager")) {
                name == "interceptKeyBeforeDispatching"
            }.hookBefore { param ->
                run {
                    val arg1: KeyEvent = param.args[1] as KeyEvent
                    // alt-tab
                    if ((arg1.isAltPressed && arg1.keyCode == 61)) {
                        param.result = 0L
                    }
                }
            }
            XposedBridge.log("XC: KeyEventHook success!")
        } catch (e: Throwable) {
            XposedBridge.log("XC: KeyEventHook failed!")
            XposedBridge.log(e)
        }
    }
}