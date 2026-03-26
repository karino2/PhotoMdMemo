package io.github.karino2.photomdmemo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewActivity : AppCompatActivity() {
    class PhotoAdapter(context: Context, uris: List<Uri>) : ArrayAdapter<Uri>(context, R.layout.photo_item, uris) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.photo_item, parent, false)
            val imageView = view.findViewById<ImageView>(R.id.imageViewPhoto)
            val uri = getItem(position)
            imageView.setImageURI(uri)
            return view
        }
    }

    private fun getUrisFromIntent(intent: Intent): List<Uri> {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let { listOf(it) } ?: emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                } ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private val imageUris by lazy { getUrisFromIntent(intent) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_new)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val listView = findViewById<ListView>(R.id.listViewPhotos)
        listView.adapter = PhotoAdapter(this, imageUris)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_new, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_done -> {
                saveAndFinish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveAndFinish() {
        val rawKeyword = findViewById<EditText>(R.id.editTextKeyword).text.toString()
        val keyword = Normalizer.normalize(rawKeyword, Normalizer.Form.NFC)
        val memo = findViewById<EditText>(R.id.editTextMemo).text.toString()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        val lastUri = MainActivity.lastUri(this) ?: return
        val rootDir = DocumentFile.fromTreeUri(this, lastUri) ?: return

        // 1. Save Images
        val savedPhotoNames = imageUris.mapIndexed { index, uri ->
            val fileName = "${dateStr}-${keyword}-${index + 1}.jpg"
            val file = rootDir.createFile("image/jpeg", fileName)
            file?.let {
                contentResolver.openOutputStream(it.uri)?.use { out ->
                    contentResolver.openInputStream(uri)?.use { input ->
                        input.copyTo(out)
                    }
                }
            }
            fileName
        }

        // 2. Save Markdown
        val mdFileName = "${dateStr}-${keyword}.md"
        val mdFile = rootDir.createFile("text/markdown", mdFileName)
        mdFile?.let {
            contentResolver.openOutputStream(it.uri)?.use { out ->
                val sb = StringBuilder()
                sb.append("# ${keyword}\n\n")
                sb.append("${memo}\n\n")
                savedPhotoNames.forEach { photoName ->
                    sb.append("![${photoName}](${photoName})\n")
                }
                out.write(sb.toString().toByteArray())
            }
        }

        finish()
    }
}
