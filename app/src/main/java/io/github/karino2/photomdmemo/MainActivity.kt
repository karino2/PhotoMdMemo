package io.github.karino2.photomdmemo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        const val LAST_URI_KEY = "last_uri_path"

        fun sharedPreferences(ctx: Context) =
            ctx.getSharedPreferences("PHOTO_MD_MEMO", Context.MODE_PRIVATE)!!

        fun lastUri(ctx: Context) = sharedPreferences(ctx).getString(LAST_URI_KEY, null)?.toUri()

        fun writeLastUri(ctx: Context, uri: Uri) = sharedPreferences(ctx)
            .edit(commit = true) {
                putString(LAST_URI_KEY, uri.toString())
        }

        fun resetLastUriStr(ctx: Context) = sharedPreferences(ctx).edit(commit = true) {
            putString(LAST_URI_KEY, null)
        }

        fun showMessage(ctx: Context, msg : String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    private var _url : Uri? = null

    private val rootDir: DocumentFile
        get() = _url?.let { DocumentFile.fromTreeUri(this, it) } ?: throw Exception("No url set")

    private fun writeLastUri(uri: Uri) = writeLastUri(this, uri)
    private val lastUri: Uri?
        get() = lastUri(this)

    private val getRootDirUrl = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        // if cancel, null coming.
        uri?.let {
            contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            writeLastUri(it)
            openRootDir(it)
            handleIntent(intent)
        }
    }

    private fun openRootDir(url: Uri) {
        _url = url
        adapter.clear()
        adapter.addAll(Entry.from(rootDir))
        listView.adapter = adapter
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            if (it.action == Intent.ACTION_SEND || it.action == Intent.ACTION_SEND_MULTIPLE) {
                if (it.type?.startsWith("image/") == true) {
                    val newIntent = Intent(this, NewActivity::class.java).apply {
                        action = it.action
                        type = it.type
                        if (it.action == Intent.ACTION_SEND) {
                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                it.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                it.getParcelableExtra(Intent.EXTRA_STREAM)
                            }
                            putExtra(Intent.EXTRA_STREAM, uri)
                        } else {
                            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                it.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                it.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                            }
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        }
                    }
                    startActivity(newIntent)
                }
            }
        }
    }

    private val listView by lazy { findViewById<ListView>(R.id.listView) }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val adapter by lazy {
        object : ArrayAdapter<Entry>(this, R.layout.list_item){
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
                val entry = getItem(position)!!
                view.findViewById<TextView>(R.id.textViewDate).text = dateFormatter.format(entry.date)
                view.findViewById<TextView>(R.id.textViewBody).text = entry.name
                return view
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _url?.let {
            try {
                openRootDir(it)
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val entry = adapter.getItem(position)!!
            val viewIntent = Intent(this, ViewActivity::class.java).apply {
                data = entry.uri
            }
            startActivity(viewIntent)
        }

        try {
            lastUri?.let {
                openRootDir(it)
                handleIntent(intent)
                return
            }
        } catch(_: Exception) {
            showMessage(this, "Can't open saved dir. Please reopen.")
        }
        getRootDirUrl.launch(null)
    }
}
