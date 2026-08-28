package com.example.bilingualsubviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var subtitleList: RecyclerView
    private lateinit var currentSubtitleText: TextView
    private lateinit var positionText: TextView
    private lateinit var fileNameText: TextView
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var emptyText: TextView
    private lateinit var searchInput: EditText
    private lateinit var playerView: PlayerView
    private lateinit var mediaButton: Button
    private lateinit var videoToggleButton: Button
    private lateinit var gestureDetector: GestureDetector
    private lateinit var subtitleAdapter: SubtitleAdapter

    private var player: ExoPlayer? = null
    private var subtitles: List<Subtitle> = emptyList()
    private var currentPosition = -1
    private var searchQuery = ""
    private var videoHidden = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { loadSubtitle(it) }
    }

    private val openMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { loadMedia(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        setupInsets()
        setupGestures()
        setupButtons()
        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) intent.data?.let { loadSubtitle(it) }
        updateUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let { loadSubtitle(it) }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (::gestureDetector.isInitialized) gestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    private fun setupViews() {
        subtitleList = findViewById(R.id.subtitleList)
        currentSubtitleText = findViewById(R.id.currentSubtitleText)
        positionText = findViewById(R.id.positionText)
        fileNameText = findViewById(R.id.fileNameText)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        emptyText = findViewById(R.id.emptyText)
        searchInput = findViewById(R.id.searchInput)
        playerView = findViewById(R.id.playerView)
        mediaButton = findViewById(R.id.mediaButton)
        videoToggleButton = findViewById(R.id.videoToggleButton)

        subtitleList.layoutManager = LinearLayoutManager(this)
        subtitleAdapter = SubtitleAdapter(emptyList(), emptyList()) { selectSubtitle(it) }
        subtitleList.adapter = subtitleAdapter

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                applySearch()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun setupInsets() {
        val root = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dx) < 100 || abs(dx) < abs(dy) || abs(velocityX) < 100) return false
                if (dx < 0) nextSubtitle() else previousSubtitle()
                return true
            }
        })
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.openButton).setOnClickListener {
            openDocument.launch(arrayOf("application/x-subrip", "text/srt", "text/plain", "application/octet-stream", "*/*"))
        }
        mediaButton.setOnClickListener {
            openMedia.launch(arrayOf("video/*", "audio/*", "application/octet-stream"))
        }
        videoToggleButton.setOnClickListener { toggleVideo() }
        previousButton.setOnClickListener { previousSubtitle() }
        nextButton.setOnClickListener { nextSubtitle() }
    }

    private fun loadMedia(uri: Uri) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build().also { playerView.player = it }
            }
            player?.setMediaItem(MediaItem.fromUri(uri))
            player?.prepare()
            player?.playWhenReady = true
            fileNameText.text = uri.lastPathSegment ?: getString(R.string.media_file)
            videoToggleButton.visibility = View.VISIBLE
        } catch (exception: Exception) {
            Toast.makeText(this, "Media error: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleVideo() {
        videoHidden = !videoHidden
        playerView.visibility = if (videoHidden) View.GONE else View.VISIBLE
        videoToggleButton.text = if (videoHidden) getString(R.string.show_video) else getString(R.string.hide_video)
    }

    private fun loadSubtitle(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.empty_file), Toast.LENGTH_SHORT).show()
                return
            }
            val parsed = SubtitleParser.parse(text)
            if (parsed.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_subtitles), Toast.LENGTH_LONG).show()
                return
            }
            subtitles = parsed
            currentPosition = 0
            searchQuery = ""
            searchInput.setText("")
            if (player == null) fileNameText.text = uri.lastPathSegment ?: getString(R.string.subtitle_file)
            subtitleAdapter.setAllItems(subtitles)
            applySearch()
        } catch (exception: Exception) {
            Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun applySearch() {
        val filtered = filteredSubtitles()
        subtitleAdapter.setItems(filtered)
        if (searchQuery.isNotBlank() && filtered.isEmpty()) {
            emptyText.text = getString(R.string.no_search_results)
            emptyText.visibility = View.VISIBLE
            currentSubtitleText.visibility = View.GONE
            return
        }
        emptyText.text = getString(R.string.open_subtitle_message)
        updateUi()
        if (searchQuery.isNotBlank() && filtered.isNotEmpty()) selectFirstSearchResult(filtered)
    }

    private fun filteredSubtitles(): List<Subtitle> {
        if (searchQuery.isBlank()) return subtitles
        val q = searchQuery.lowercase(Locale.getDefault())
        return subtitles.filter { s ->
            s.index.toString().contains(searchQuery) || s.text.lowercase(Locale.getDefault()).contains(q)
        }
    }

    private fun selectFirstSearchResult(results: List<Subtitle>) {
        val first = results.firstOrNull() ?: return
        val original = subtitles.indexOfFirst { it.index == first.index && it.startTime == first.startTime }
        if (original >= 0) selectSubtitle(original)
    }

    private fun selectSubtitle(position: Int) {
        if (position !in subtitles.indices) return
        currentPosition = position
        val subtitle = subtitles[position]
        player?.seekTo(subtitle.startTime)
        player?.playWhenReady = true
        val visiblePosition = subtitleAdapter.visiblePositionOf(subtitle)
        if (visiblePosition >= 0) subtitleList.smoothScrollToPosition(visiblePosition)
        updateUi()
    }

    private fun nextSubtitle() {
        if (subtitles.isNotEmpty() && currentPosition < subtitles.lastIndex) selectSubtitle(currentPosition + 1)
    }

    private fun previousSubtitle() {
        if (subtitles.isNotEmpty() && currentPosition > 0) selectSubtitle(currentPosition - 1)
    }

    private fun updateUi() {
        val hasSubtitles = subtitles.isNotEmpty() && currentPosition in subtitles.indices
        val hasSearchResults = searchQuery.isBlank() || subtitleAdapter.itemCount > 0
        emptyText.visibility = if (hasSubtitles && hasSearchResults) View.GONE else View.VISIBLE
        currentSubtitleText.visibility = if (hasSubtitles) View.VISIBLE else View.GONE
        if (!hasSubtitles) {
            positionText.text = "0 / 0"
            previousButton.isEnabled = false
            nextButton.isEnabled = false
            return
        }
        currentSubtitleText.text = BidiUtils.format(subtitles[currentPosition].text)
        positionText.text = String.format(Locale.US, "%d / %d", currentPosition + 1, subtitles.size)
        previousButton.isEnabled = currentPosition > 0
        nextButton.isEnabled = currentPosition < subtitles.lastIndex
    }

    private class SubtitleAdapter(
        private var items: List<Subtitle>,
        private var allItems: List<Subtitle>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {
        fun setAllItems(items: List<Subtitle>) { allItems = items }
        fun setItems(newItems: List<Subtitle>) { items = newItems; notifyDataSetChanged() }
        fun visiblePositionOf(subtitle: Subtitle): Int = items.indexOfFirst { it.index == subtitle.index && it.startTime == subtitle.startTime }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder = ViewHolder(
            android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_subtitle, parent, false)
        )
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val subtitle = items[position]
            holder.number.text = subtitle.index.toString()
            holder.text.text = BidiUtils.format(subtitle.text)
            holder.time.text = formatTime(subtitle.startTime)
            holder.itemView.setOnClickListener {
                val original = allItems.indexOfFirst { it.index == subtitle.index && it.startTime == subtitle.startTime }
                if (original >= 0) onClick(original)
            }
        }
        override fun getItemCount(): Int = items.size
        private fun formatTime(milliseconds: Long): String {
            val totalSeconds = milliseconds / 1000
            return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        }
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val number: TextView = view.findViewById(R.id.itemNumber)
            val text: TextView = view.findViewById(R.id.itemText)
            val time: TextView = view.findViewById(R.id.itemTime)
        }
    }
}
