package il.hs.bpt.hook

abstract class BaseHook {
    var isInit: Boolean = false
    abstract fun init()
}