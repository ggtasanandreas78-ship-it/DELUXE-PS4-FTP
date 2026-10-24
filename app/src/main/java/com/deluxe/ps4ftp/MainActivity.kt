package com.deluxe.ps4ftp

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 18, 28)
        window.navigationBarColor = Color.rgb(15, 18, 28)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 12)
            setBackgroundColor(Color.rgb(18, 22, 34))
        }
        fun label(text: String, size: Float = 14f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.WHITE)
        }
        val logo = ImageView(this).apply {
            setImageResource(com.deluxe.ps4ftp.R.drawable.deluxe_logo)
            contentDescription = "DELUXE PS4 FTP logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(logo, LinearLayout.LayoutParams(88, 88).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(label("DELUXE PS4 FTP", 24f))
        root.addView(label("اتصال حقيقي بخادم FTP في GoldHEN"))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        host = EditText(this).apply {
            hint = "IP البلاي مثل 192.168.68.102"; setSingleLine(); textSize = 14f
            setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(34, 40, 56))
            setPadding(10, 0, 10, 0)
        }
        port = EditText(this).apply {
            hint = "Port"; setSingleLine(); setText("2121"); inputType = 2
            textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY)
            setBackgroundColor(Color.rgb(34, 40, 56)); setPadding(10, 0, 10, 0)
        }
        row.addView(host, LinearLayout.LayoutParams(0, 54, 1f))
        row.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        row.addView(port, LinearLayout.LayoutParams(90, 54))
        root.addView(row)
        connectButton = Button(this).apply { text = "اتصال بالبلايستيشن" }
        root.addView(connectButton)
        status = label("غير متصل")
        status.setTextColor(Color.rgb(255, 190, 90))
        root.addView(status)
        pathLabel = label("المسار: /")
        root.addView(pathLabel)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = "رفع ملف"
            setOnClickListener {
                if (!connected()) toast("اتصل بالبلاي أولاً") else {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    startActivityForResult(i, 101)
                }
            }
        }, LinearLayout.LayoutParams(0, 52, 1f))
        actions.addView(Button(this).apply {
            text = "تحديث"
            setOnClickListener { if (connected()) refreshRemote() else toast("اتصل بالبلاي أولاً") }
        }, LinearLayout.LayoutParams(0, 52, 1f))
        root.addView(actions)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        root.addView(progress)
        progressText = label("جاهز للنقل")
        root.addView(progressText)
        val scroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        connectButton.setOnClickListener { if (connected()) disconnect() else connect() }
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
                    refreshRemote()
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
                list.removeAllViews(); status.text = "غير متصل"
                status.setTextColor(Color.rgb(255, 190, 90))
                connectButton.text = "اتصال بالبلايستيشن"
                pathLabel.text = "المسار: /"
            }
        }
    }

    private fun refreshRemote() {
        val c = ftp ?: return
        val path = currentPath
        status.text = "جاري قراءة ملفات PS4..."
        worker.execute {
            try {
                val files = c.listFiles(path)
                runOnUiThread {
                    list.removeAllViews()
                    val parent = Button(this).apply {
                        text = "⬆ مجلد أعلى"
                        setOnClickListener {
                            val up = path.substringBeforeLast('/', "")
                            currentPath = if (up.isBlank()) "/" else up
                            pathLabel.text = "المسار: $currentPath"; refreshRemote()
                        }
                    }
                    list.addView(parent)
                    files.sortedWith(compareBy<FTPFile> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }).forEach { f ->
                        val b = Button(this).apply {
                            text = (if (f.isDirectory) "📁 " else "📄 ") + f.name +
                                if (f.isFile) "  (${human(f.size)})" else ""
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
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
                        list.addView(b)
                    }
                    status.text = "نجح الاتصال • ${files.size} عنصر"
                    status.setTextColor(Color.rgb(90, 230, 150))
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "تعذر عرض الملفات: ${e.message}" }
            }
        }
    }

    private fun chooseDownload(f: FTPFile) {
        transferName = f.name
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
                runOnUiThread { progress.progress = 100; progressText.text = "اكتمل رفع $name"; refreshRemote() }
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
