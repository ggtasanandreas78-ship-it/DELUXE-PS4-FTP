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
        val bg = Color.rgb(3, 10, 25)
        val cardColor = Color.rgb(8, 25, 48)
        val blue = Color.rgb(0, 105, 255)
        val cyan = Color.rgb(40, 190, 255)
        val muted = Color.rgb(165, 190, 220)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        fun panel(): android.graphics.drawable.GradientDrawable {
            return android.graphics.drawable.GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = 22f
                setStroke(1, Color.rgb(20, 65, 105))
            }
        }

        fun label(text: String, size: Float = 14f, color: Int = Color.WHITE) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                setPadding(4, 5, 4, 5)
            }

        fun makeButton(text: String, color: Int = blue) =
            Button(this).apply {
                this.text = text
                textSize = 14f
                setTextColor(Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = 18f
                }
                isAllCaps = false
                minHeight = 50
            }

        fun section(title: String): LinearLayout {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(15, 13, 15, 13)
                background = panel()
            }
            box.addView(label(title, 17f))
            return box
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 10, 15, 10)
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(com.deluxe.ps4ftp.R.drawable.deluxe_logo)
            contentDescription = "DELUXE PS4 FTP"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        content.addView(logo, LinearLayout.LayoutParams(76, 76).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        content.addView(label("DELUXE PS4 FTP", 25f).apply {
            gravity = Gravity.CENTER
            setTextColor(cyan)
        })
        content.addView(label("FAST  •  SIMPLE  •  RELIABLE", 11f, muted).apply {
            gravity = Gravity.CENTER
        })

        val connection = section("●  اتصال البلايستيشن")
        connection.addView(label("عنوان IP للبلايستيشن", 12f, muted))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        host = EditText(this).apply {
            hint = "مثال: 192.168.68.102"
            setSingleLine()
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(12, 0, 12, 0)
            background = panel()
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        port = EditText(this).apply {
            hint = "Port"
            setSingleLine()
            setText("2121")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            setPadding(8, 0, 8, 0)
            background = panel()
        }

        row.addView(host, LinearLayout.LayoutParams(0, 52, 1f))
        row.addView(Space(this), LinearLayout.LayoutParams(8, 1))
        row.addView(port, LinearLayout.LayoutParams(88, 52))
        connection.addView(row)

        connectButton = makeButton("اتصال بالبلايستيشن")
        connection.addView(connectButton, LinearLayout.LayoutParams(-1, 52).apply {
            topMargin = 10
        })

        status = label("● غير متصل", 13f, Color.rgb(255, 190, 90))
        connection.addView(status)

        content.addView(connection, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 18
        })

        val files = section("📁  ملفات PS4")
        pathLabel = label("المسار الحالي: /", 13f, cyan)
        files.addView(pathLabel)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        actions.addView(makeButton("⬆ إرسال ملف"), LinearLayout.LayoutParams(0, 52, 1f).apply {
            rightMargin = 5
        }.also { params ->
            // يبقى زر الرفع مرتبطاً بوظيفة اختيار الملف الأصلية
        })

        actions.addView(makeButton("⟳ تحديث", Color.rgb(14, 53, 91)),
            LinearLayout.LayoutParams(0, 52, 1f).apply { leftMargin = 5 })

        val uploadButton = actions.getChildAt(0) as Button
        uploadButton.setOnClickListener {
            if (!connected()) {
                toast("اتصل بالبلاي أولاً")
            } else {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(i, 101)
            }
        }

        actions.getChildAt(1).setOnClickListener {
            if (connected()) refreshRemote() else toast("اتصل بالبلاي أولاً")
        }

        files.addView(actions)
        files.addView(label("اضغط على مجلد لاستعراض محتوياته", 12f, muted))

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        files.addView(list)
        content.addView(files, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 14
        })

        val transfer = section("⇅  حالة النقل")
        progress = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(blue)
        }
        transfer.addView(progress, LinearLayout.LayoutParams(-1, 12).apply {
            topMargin = 8
        })

        progressText = label("جاهز للنقل", 13f, cyan)
        transfer.addView(progressText)
        transfer.addView(label("تأكد أن الجهازين على نفس شبكة Wi-Fi", 11f, muted))

        content.addView(transfer, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = 14
            bottomMargin = 15
        })

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        connectButton.setOnClickListener {
            if (connected()) disconnect() else connect()
        }
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
                client.dataTimeout = java.time.Duration.ofSeconds(15)
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
