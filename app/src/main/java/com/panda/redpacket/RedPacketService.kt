@file:Suppress("DEPRECATION")

package com.panda.redpacket

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader


@TargetApi(Build.VERSION_CODES.DONUT)
/**
 * 抢红包Service,继承AccessibilityService
 */
class RedPacketService : AccessibilityService() {

    private val TAG = "tDebug"

    /**
     * 微信几个页面的包名+地址。用于判断在哪个页面
     * LAUCHER-微信聊天界面
     * LUCKEY_MONEY_RECEIVER-点击红包弹出的界面
     * LUCKEY_MONEY_DETAIL-红包领取后的详情界面
     */
    private val LAUCHER = "com.tencent.mm.ui.LauncherUI"
    private val LUCKEY_MONEY_RECEIVER = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNotHookReceiveUI"
    private val LUCKEY_MONEY_DETAIL = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI"

    /**
     * 用于判断是否点击过红包了
     */
    private var isOpenRP: Boolean = false

    private var isOpenDetail = false

    /**
     * 用于判断是否屏幕是亮着的
     */
    private var isScreenOn: Boolean = false

    /**
     * 获取PowerManager.WakeLock对象
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * KeyguardManager.KeyguardLock对象
     */
    private var keyguardLock: KeyguardManager.KeyguardLock? = null

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventType = event.eventType
        Log.d(TAG, "et : $eventType")
        if (!MainActivity.onOff) {
            return
        }
        when (eventType) {
            //通知栏来信息，判断是否含有微信红包字样，是的话跳转
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                val texts = event.text
                for (text in texts) {
                    val content = text.toString()
                    if (!TextUtils.isEmpty(content)) {
                        //判断是否含有[微信红包]字样
                        if ("微信红包" in content) {
                            Log.d(TAG, "找到通知栏的微信红包")
                            if (!isScreenOn()) {
                                wakeUpScreen()
                            }
                            //如果有则打开微信红包页面
                            openWeChatPage(event)

                            isOpenRP = false
                        }
                    }
                }
            }
            //界面跳转的监听
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val className = event.className.toString()
                Log.d(TAG, "class : $className")
                //判断是否是微信聊天界面
                if (LAUCHER == className) {
                    //获取当前聊天页面的根布局
                    val rootNode = rootInActiveWindow
                    //开始找红包
                    Log.d(TAG, "开始找红包")
                    findRedPacket(rootNode)
                }

                //判断是否是显示‘开’的那个红包界面
                if (LUCKEY_MONEY_RECEIVER.trim() == className.trim()) {
                    val rootNode = rootInActiveWindow
                    //开始开红包
                    Log.d(TAG, "开始开红包")
                    openRedPacket(rootNode)
                }

                //判断是否是红包领取后的详情界面
                if (isOpenDetail && LUCKEY_MONEY_DETAIL == className) {

                    isOpenDetail = false
                    //返回桌面
                    back2Home()
                    //如果之前是锁着屏幕的则重新锁回去
                    release()
                }
            }
            else -> {
            }
        }
    }

    /**
     * 遍历查找红包
     */
    private fun findRedPacket(rootNode: AccessibilityNodeInfo?) {
        if (rootNode == null) {
            return
        }
        for (i in rootNode.childCount - 1 downTo 0) {
            val node = rootNode.getChild(i) ?: continue
            //如果node为空则跳过该节点
            Log.d(TAG, "node : $node")
            val text = node.text
            Log.d(TAG, "text : ${node.text}")
            if (text != null && text.toString() == "微信红包") {
                var parent: AccessibilityNodeInfo? = node.parent
                //while循环,遍历"微信红包"的各个父布局，直至找到可点击的为止
                while (parent != null) {
                    if (parent.isClickable) {
                        //模拟点击
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        //isOpenRP用于判断该红包是否点击过
                        isOpenRP = true

                        break
                    }
                    parent = parent.parent
                }
            }
            //判断是否已经打开过那个最新的红包了，是的话就跳出for循环，不是的话继续遍历
//            Log.d(TAG, "isOpenRP: $isOpenRP")
//            if (isOpenRP) {
//                break
//            } else {
            findRedPacket(node)
//            }
        }
    }

    /**
     * 开始打开红包
     */
    private fun openRedPacket(rootNode: AccessibilityNodeInfo) {
        Log.d(TAG, "openRedPacket----")
        for (i in 0 until rootNode.childCount) {
            val node = rootNode.getChild(i)
            Log.d(TAG, "son  : $node")
            if ("android.widget.Button" == node.className) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                isOpenDetail = true
            }
            openRedPacket(node)
        }
    }

    /**
     * 开启红包所在的聊天页面
     */
    private fun openWeChatPage(event: AccessibilityEvent) {
        //A instanceof B 用来判断内存中实际对象A是不是B类型，常用于强制转换前的判断
        Log.d(TAG, "openWeChatPage")
        if (event.parcelableData != null && event.parcelableData is Notification) {
            val notification = event.parcelableData as Notification
            //打开对应的聊天界面
            val pendingIntent = notification.contentIntent
            try {
                pendingIntent.send()
            } catch (e: PendingIntent.CanceledException) {
                e.printStackTrace()
            }

        }
    }


    /**
     * 服务连接
     */
    override fun onServiceConnected() {
        Toast.makeText(this, "抢红包服务开启", Toast.LENGTH_SHORT).show()
        super.onServiceConnected()
    }

    /**
     * 必须重写的方法：系统要中断此service返回的响应时会调用。在整个生命周期会被调用多次。
     */
    override fun onInterrupt() {
        Toast.makeText(this, "我快被终结了啊-----", Toast.LENGTH_SHORT).show()
    }

    /**
     * 服务断开
     */
    override fun onUnbind(intent: Intent): Boolean {
        Toast.makeText(this, "抢红包服务已被关闭", Toast.LENGTH_SHORT).show()
        return super.onUnbind(intent)
    }

    /**
     * 返回桌面
     */
    private fun back2Home() {
        val home = Intent(Intent.ACTION_MAIN)
        home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        home.addCategory(Intent.CATEGORY_HOME)
        startActivity(home)

//        object : Thread() {
//            override fun run() {
//                execByRuntime("input keyevent 61")
//            }
//        }.start()
    }


    /**
     * 判断是否处于亮屏状态
     *
     * @return true-亮屏，false-暗屏
     */
    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isScreenOn = pm.isScreenOn
        Log.e("isScreenOn", isScreenOn.toString() + "")
        return isScreenOn
    }

    /**
     * 执行shell 命令， 命令中不必再带 adb shell
     *
     * @param cmd
     * @return Sting  命令执行在控制台输出的结果
     */
    fun execByRuntime(cmd: String?): String? {
        var process: Process? = null
        var bufferedReader: BufferedReader? = null
        var inputStreamReader: InputStreamReader? = null
        return try {
            process = Runtime.getRuntime().exec(cmd)
            inputStreamReader = InputStreamReader(process.inputStream)
            bufferedReader = BufferedReader(inputStreamReader)
            var read = 0
            val buffer = CharArray(4096)
            val output = StringBuilder()
            while (bufferedReader.read(buffer).also { read = it } > 0) {
                output.append(buffer, 0, read)
            }
            output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (null != inputStreamReader) {
                try {
                    inputStreamReader.close()
                } catch (t: Throwable) { //
                }
            }
            if (null != bufferedReader) {
                try {
                    bufferedReader.close()
                } catch (t: Throwable) { //
                }
            }
            if (null != process) {
                try {
                    process.destroy()
                } catch (t: Throwable) { //
                }
            }
        }
    }

    /**
     * 解锁屏幕
     */
    @SuppressLint("InvalidWakeLockTag", "WakelockTimeout")
    private fun wakeUpScreen() {

        //获取电源管理器对象
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        //后面的参数|表示同时传入两个值，最后的是调试用的Tag
        wakeLock = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.FULL_WAKE_LOCK, "bright")

        //点亮屏幕
        wakeLock!!.acquire()

        //得到键盘锁管理器
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardLock = km.newKeyguardLock("unlock")

        //解锁
        keyguardLock!!.disableKeyguard()
    }

    /**
     * 释放keyguardLock和wakeLock
     */
    private fun release() {
        if (keyguardLock != null) {
            keyguardLock!!.reenableKeyguard()
            keyguardLock = null
        }
        if (wakeLock != null) {
            wakeLock!!.release()
            wakeLock = null
        }
    }

}