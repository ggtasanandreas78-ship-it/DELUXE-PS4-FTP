package com.deluxe.ps4ftp

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.widget.FrameLayout
import android.graphics.drawable.Drawable
import android.content.Intent
import android.net.Uri
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var ftp: FTPClient? = null
    private var currentPath = "/"
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var status: TextView
    private lateinit var pathLabel: TextView
    private lateinit var list: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var connectButton: Button
    private var busy = false
    private var transferName = ""
    private lateinit var screenHost: FrameLayout
    private lateinit var mainRoot: LinearLayout
    private lateinit var navBar: LinearLayout
    private var currentScreen = "home"
    private val bg = Color.rgb(8, 13, 27)
    private val panel = Color.rgb(17, 27, 48)
    private val panel2 = Color.rgb(23, 37, 63)
    private val blue = Color.rgb(48, 135, 255)
    private val cyan = Color.rgb(45, 220, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 18, 28)
        window.navigationBarColor = Color.rgb(15, 18, 28)
        buildUi()
    }

    private fun buildUi() {
        mainRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val logo = ImageView(this).apply {
            setImageResource(com.deluxe.ps4ftp.R.drawable.deluxe_logo)
            contentDescription = "DELUXE logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(46), dp(46)))
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        titles.addView(label("DELUXE", 20f, cyan))
        titles.addView(label("PS4 FTP  •  GOLDHEN", 10f, Color.LTGRAY))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val live = TextView(this).apply {
            text = "● LIVE"; textSize = 11f; setTextColor(Color.rgb(100, 235, 160))
            setPadding(dp(10), dp(7), dp(10), dp(7)); background = rounded(panel2, 18)
        }
        header.addView(live)
        mainRoot.addView(header)
        val line = View(this).apply { setBackgroundColor(Color.rgb(29, 54, 91)) }
        mainRoot.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        screenHost = FrameLayout(this)
        mainRoot.addView(screenHost, LinearLayout.LayoutParams(-1, 0, 1f))
        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = rounded(Color.rgb(12, 20, 37), 20)
        }
        val navWrap = LinearLayout(this).apply { setPadding(dp(10), dp(5), dp(10), dp(8)); addView(navBar) }
        mainRoot.addView(navWrap)
        setContentView(mainRoot)
        showScreen("home")
    }

    private fun label(text: String, size: Float = 14f, color: Int = Color.WHITE) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
    }

    private fun dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = rounded(panel, 18)
    }

    private fun styledButton(text: String, primary: Boolean = false, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        background = rounded(if (primary) blue else panel2, 14)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        setOnClickListener { action() }
    }

    private fun showScreen(screen: String) {
        currentScreen = screen
        screenHost.removeAllViews()
        navBar.removeAllViews()
        val tabs = listOf("home" to "⌂\nالرئيسية", "files" to "▤\nالملفات", "transfer" to "⇅\nالنقل", "settings" to "⚙\nالإعدادات")
        tabs.forEach { (key, title) ->
            val t = TextView(this).apply {
                text = title; gravity = Gravity.CENTER; textSize = 11f
                setTextColor(if (key == screen) cyan else Color.rgb(145, 163, 190))
                setPadding(dp(6), dp(5), dp(6), dp(5))
                background = if (key == screen) rounded(panel2, 12) else null
                setOnClickListener { showScreen(key) }
            }
            navBar.addView(t, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        screenHost.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        when (screen) {
            "home" -> {
                content.addView(label("لوحة التحكم", 25f))
                content.addView(label("إدارة اتصال البلايستيشن ونقل ملفاتك", 13f, Color.rgb(157, 178, 208)))
                val hero = card().apply {
                    background = rounded(Color.rgb(17, 43, 80), 22)
                    addView(label("DELUXE CONNECT", 12f, cyan))
                    addView(label("PS4  /  GoldHEN FTP", 22f))
                    addView(label(if (connected()) "● متصل بالبلايستيشن" else "○ بانتظار الاتصال", 14f, if (connected()) Color.rgb(95, 235, 160) else Color.rgb(255, 195, 90)))
                    addView(label("${hostTextSaved().ifBlank { "IP غير محدد" }} : ${portTextSaved()}", 12f, Color.LTGRAY))
                    addView(styledButton(if (connected()) "قطع الاتصال" else "إعداد الاتصال", true) { if (connected()) disconnect() else showScreen("settings") }, LinearLayout.LayoutParams(-1, dp(48)))
                }
                content.addView(hero, marginParams(bottom = 14))
                val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val c1 = card().apply { addView(label("01", 12f, cyan)); addView(label("تصفح الملفات", 15f)); addView(label("محتويات PS4", 11f, Color.LTGRAY)) }
                val c2 = card().apply { addView(label("02", 12f, cyan)); addView(label("نقل مباشر", 15f)); addView(label("PKG والملفات", 11f, Color.LTGRAY)) }
                stats.addView(c1, LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(6) })
                stats.addView(c2, LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(6) })
                content.addView(stats, marginParams(bottom = 14))
                content.addView(label("إجراءات سريعة", 17f))
                content.addView(styledButton("▤   فتح مدير الملفات", false) { showScreen("files"); if (connected()) refreshRemote() else toast("اتصل بالبلاي أولاً") }, marginParams(top = 8, bottom = 8))
                content.addView(styledButton("↑   إرسال ملف إلى PS4", true) { pickUpload() }, marginParams(bottom = 8))
                content.addView(styledButton("⚙   إعداد الاتصال", false) { showScreen("settings") })
            }
            "files" -> {
                content.addView(label("مدير الملفات", 24f))
                content.addView(label("تصفح ملفات ومجلدات جهاز PS4", 13f, Color.LTGRAY))
                content.addView(card().apply {
                    addView(label("المسار الحالي", 11f, cyan))
                    pathLabel = label("المسار: $currentPath", 13f)
                    addView(pathLabel)
                    addView(styledButton("↻  تحديث القائمة", true) { if (connected()) refreshRemote() else toast("اتصل بالبلاي أولاً") }, marginParams(top = 10))
                }, marginParams(top = 12, bottom = 12))
                list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                content.addView(list)
                if (connected()) refreshRemote() else content.addView(label("اتصل من الرئيسية أو الإعدادات حتى تظهر الملفات.", 13f, Color.rgb(255, 195, 90)))
            }
            "transfer" -> {
                content.addView(label("مركز النقل", 24f))
                content.addView(label("متابعة رفع وتنزيل الملفات", 13f, Color.LTGRAY))
                content.addView(card().apply {
                    addView(label("حالة العملية", 12f, cyan))
                    progress = ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
                    addView(progress, LinearLayout.LayoutParams(-1, dp(10)).apply { topMargin = dp(14); bottomMargin = dp(12) })
                    progressText = label("جاهز للنقل", 14f)
                    addView(progressText)
                    addView(styledButton("↑  اختيار ملف للرفع", true) { pickUpload() }, marginParams(top = 16))
                    addView(styledButton("▤  استعراض ملفات PS4", false) { showScreen("files"); if (connected()) refreshRemote() else toast("اتصل بالبلاي أولاً") }, marginParams(top = 8))
                }, marginParams(top = 14))
            }
            else -> {
                content.addView(label("الإعدادات", 24f))
                content.addView(label("بيانات اتصال خادم GoldHEN", 13f, Color.LTGRAY))
                content.addView(card().apply {
                    addView(label("عنوان IP للبلايستيشن", 13f, cyan))
                    host = EditText(this@MainActivity).apply {
                        hint = "مثال: 192.168.68.102"; setSingleLine(); textSize = 15f
                        setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setPadding(dp(12), 0, dp(12), 0)
                        background = rounded(panel2, 12); setText(hostTextSaved())
                    }
                    addView(host, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8); bottomMargin = dp(14) })
                    addView(label("Port FTP", 13f, cyan))
                    port = EditText(this@MainActivity).apply {
                        hint = "2121"; setSingleLine(); inputType = 2; textSize = 15f
                        setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setPadding(dp(12), 0, dp(12), 0)
                        background = rounded(panel2, 12); setText(portTextSaved())
                    }
                    addView(port, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8); bottomMargin = dp(14) })
                    status = label(if (connected()) "● متصل" else "● غير متصل", 13f, if (connected()) Color.GREEN else Color.rgb(255, 195, 90))
                    addView(status)
                    connectButton = styledButton(if (connected()) "قطع الاتصال" else "حفظ والاتصال", true) { if (connected()) disconnect() else connect() }
                    addView(connectButton, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(14) })
                }, marginParams(top = 14))
                content.addView(label("ملاحظة: تأكد أن الجهازين على نفس شبكة Wi‑Fi وأن FTP مفعّل في GoldHEN.", 12f, Color.LTGRAY), marginParams(top = 12))
            }
        }
    }

    private fun hostTextSaved(): String = if (::host.isInitialized) host.text.toString() else ""
    private fun portTextSaved(): String = if (::port.isInitialized) port.text.toString() else "2121"

    private fun marginParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun pickUpload() {
        if (!connected()) { toast("اتصل بالبلاي أولاً"); showScreen("settings"); return }
        showScreen("transfer")
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(i, 101)
    }

    private fun connected() = ftp?.isConnected == true

    private fun connect() {
        val h = host.text.toString().trim()
        val p = port.text.toString().toIntOrNull()
        if (h.isBlank() || p == null || p !in 1..65535) { toast("أدخل IP وPort صحيحين"); return }
        connectButton.isEnabled = false
        status.text = "جاري اختبار الاتصال..."
        worker.execute {
            var client: FTPClient? = null
            try {
                client = FTPClient()
                client.connectTimeout = 7000
                client.defaultTimeout = 7000
                client.dataTimeout = 15000
                client.connect(h, p)
                if (!client.login("anonymous", "")) {
                    // GoldHEN FTP commonly permits anonymous login; if rejected, show the server reply.
                    throw Exception("رفض الخادم تسجيل الدخول: ${client.replyString ?: client.replyCode}")
                }
                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
                if (!client.isConnected) throw Exception("لم يكتمل الاتصال")
                ftp = client
                currentPath = client.printWorkingDirectory() ?: "/"
                runOnUiThread {
                    status.text = "متصل فعلياً • ${client.replyString?.trim() ?: "FTP OK"}"
                    status.setTextColor(Color.rgb(90, 230, 150))
                    connectButton.text = "قطع الاتصال"
                    connectButton.isEnabled = true
                    if (::list.isInitialized && currentScreen == "files") refreshRemote()
                }
            } catch (e: Exception) {
                try { if (client?.isConnected == true) client.disconnect() } catch (_: Exception) {}
                runOnUiThread {
                    status.text = "فشل الاتصال: ${e.message ?: "تحقق من IP والمنفذ وخادم GoldHEN"}"
                    status.setTextColor(Color.rgb(255, 100, 100))
                    connectButton.isEnabled = true
                }
            }
        }
    }

    private fun disconnect() {
        worker.execute {
            try { ftp?.logout() } catch (_: Exception) {}
            try { if (ftp?.isConnected == true) ftp?.disconnect() } catch (_: Exception) {}
            ftp = null
            runOnUiThread {
                if (::list.isInitialized) list.removeAllViews()
                if (::status.isInitialized) { status.text = "غير متصل"; status.setTextColor(Color.rgb(255, 190, 90)) }
                if (::connectButton.isInitialized) connectButton.text = "اتصال بالبلايستيشن"
                if (::pathLabel.isInitialized) pathLabel.text = "المسار: /"
                if (currentScreen == "home") showScreen("home")
            }
        }
    }

    private fun refreshRemote() {
        val c = ftp ?: return
        val path = currentPath
        if (::status.isInitialized) status.text = "جاري قراءة ملفات PS4..."
        worker.execute {
            try {
                val files = c.listFiles(path)
                runOnUiThread {
                    list.removeAllViews()
                    val parent = styledButton("↑  مجلد أعلى", false) {

                            val up = path.substringBeforeLast('/', "")
                            currentPath = if (up.isBlank()) "/" else up
                            if (::pathLabel.isInitialized) pathLabel.text = "المسار: $currentPath"; refreshRemote()
                        }
                    list.addView(parent, marginParams(bottom = 6))
                    files.sortedWith(compareBy<FTPFile> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }).forEach { f ->
                        val b = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(14), dp(12), dp(14), dp(12))
                            background = rounded(panel2, 14)
                            addView(label((if (f.isDirectory) "📁  " else "📄  ") + f.name, 14f))
                            if (f.isFile) addView(label(human(f.size), 11f, Color.LTGRAY))
                            setOnClickListener {
                                if (f.isDirectory) {
                                    currentPath = if (path == "/") "/${f.name}" else "$path/${f.name}"
                                    pathLabel.text = "المسار: $currentPath"; refreshRemote()
                                } else chooseDownload(f)
                            }
                            setOnLongClickListener {
                                transferName = f.name
                                if (!f.isDirectory) {
                                    val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_TITLE, f.name)
                                    }
                                    startActivityForResult(i, 102)
                                } else toast("افتح المجلد أولاً")
                                true
                            }
                        }
                        list.addView(b, marginParams(bottom = 7))
                    }
                    if (::status.isInitialized) { status.text = "نجح الاتصال • ${files.size} عنصر"; status.setTextColor(Color.rgb(90, 230, 150)) }
                }
            } catch (e: Exception) {
                runOnUiThread { if (::status.isInitialized) status.text = "تعذر عرض الملفات: ${e.message}" else toast("تعذر عرض الملفات: ${e.message}") }
            }
        }
    }

    private fun chooseDownload(f: FTPFile) {
        transferName = f.name
        showScreen("transfer")
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, f.name)
        }
        startActivityForResult(i, 102)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (requestCode == 101) upload(uri)
        if (requestCode == 102) download(uri, transferName)
    }

    private fun upload(uri: Uri) {
        val c = ftp ?: return
        worker.execute {
            try {
                val name = queryName(uri) ?: "upload.pkg"
                val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                runOnUiThread { progress.progress = 0; progressText.text = "رفع $name..." }
                val input = contentResolver.openInputStream(uri) ?: throw Exception("تعذر فتح الملف")
                input.use { ins ->
                    val out = c.storeFileStream(remotePath(name))
                        ?: throw Exception("رفض PS4 رفع الملف: ${c.replyString}")
                    out.use { os -> copyProgress(ins, os, size) }
                    if (!c.completePendingCommand()) throw Exception("فشل إكمال الرفع: ${c.replyString}")
                }
                runOnUiThread { progress.progress = 100; progressText.text = "اكتمل رفع $name"; if (::list.isInitialized && currentScreen == "files") refreshRemote() }
            } catch (e: Exception) {
                runOnUiThread { progressText.text = "فشل الرفع: ${e.message}" }
            }
        }
    }

    private fun download(uri: Uri, name: String) {
        val c = ftp ?: return
        worker.execute {
            try {
                val remote = remotePath(name)
                val size = c.listFiles(remote).firstOrNull()?.size ?: -1L
                runOnUiThread { progress.progress = 0; progressText.text = "تنزيل $name..." }
                val input = c.retrieveFileStream(remote) ?: throw Exception("تعذر تنزيل الملف: ${c.replyString}")
                val output = contentResolver.openOutputStream(uri) ?: throw Exception("تعذر إنشاء الملف المحلي")
                input.use { ins -> output.use { os -> copyProgress(ins, os, size) } }
                if (!c.completePendingCommand()) throw Exception("فشل إكمال التنزيل: ${c.replyString}")
                runOnUiThread { progress.progress = 100; progressText.text = "اكتمل تنزيل $name" }
            } catch (e: Exception) {
                runOnUiThread { progressText.text = "فشل التنزيل: ${e.message}" }
            }
        }
    }

    private fun copyProgress(input: InputStream, output: OutputStream, total: Long) {
        val buf = ByteArray(256 * 1024)
        var done = 0L
        var lastUi = System.currentTimeMillis()
        var lastBytes = 0L
        val start = System.currentTimeMillis()
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n); done += n
            val now = System.currentTimeMillis()
            if (now - lastUi >= 500) {
                val speed = (done - lastBytes) * 1000.0 / max(1L, now - lastUi)
                val pct = if (total > 0) ((done * 100 / total).toInt()).coerceIn(0, 100) else 0
                val remain = if (speed > 0 && total > done) ((total - done) / speed).toLong() else -1L
                runOnUiThread {
                    progress.progress = pct
                    progressText.text = "${human(done)} / ${if (total > 0) human(total) else "؟"} • ${humanRate(speed)}/s • المتبقي ${if (remain >= 0) "${remain}s" else "—"}"
                }
                lastUi = now; lastBytes = done
            }
        }
        output.flush()
    }

    private fun remotePath(name: String): String {
        val safeName = name.replace("/", "_").replace("\\", "_")
        return if (currentPath == "/") "/$safeName" else "$currentPath/$safeName"
    }

    private fun queryName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun human(n: Long): String {
        if (n < 0) return "؟"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = n.toDouble(); var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(Locale.US, "%.2f %s", v, units[i])
    }
    private fun humanRate(n: Double) = human(n.toLong())
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        try { if (ftp?.isConnected == true) ftp?.disconnect() } catch (_: Exception) {}
        worker.shutdownNow()
        super.onDestroy()
    }
}
