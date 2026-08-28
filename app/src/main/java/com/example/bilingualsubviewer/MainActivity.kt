package com.example.bilingualsubviewer

import android.app.ComponentCaller
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

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
    private lateinit var playerControls: PlayerControlView
    private lateinit var mediaButton: Button
    private lateinit var videoToggleButton: Button
    private lateinit var speedButton: Button
    private lateinit var loopButton: Button
    private lateinit var statusText: TextView
    private lateinit var subtitleAdapter: SubtitleAdapter

    private var player: ExoPlayer? = null
    private var subtitles: List<Subtitle> = emptyList()
    private var currentPosition = -1
    private var searchQuery = ""
    private var videoHidden = false
    private var loopSubtitle = false
    private var repeatCount = 0
    private var repeatTarget = 5
    private var playbackSpeed = 1.0f
    private var lastNonDefaultSpeed = 1.0f
    private var subtitleOffsetMs = 0L
    private var loopGuard = false

    private val syncHandler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            syncSubtitleToPlayer()
            syncHandler.postDelayed(this, 50)
        }
    }
    private val hideStatusRunnable = Runnable { statusText.visibility = View.GONE }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { loadSubtitle(it) } }
    private val openMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { loadMedia(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()
        setupInsets()
        setupButtons()
        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) intent.data?.let { loadSubtitle(it) }
        updateUi()
        syncHandler.post(syncRunnable)
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let { loadSubtitle(it) }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        syncHandler.removeCallbacksAndMessages(null)
        statusText.removeCallbacks(hideStatusRunnable)
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || player == null) return super.dispatchKeyEvent(event)
        if (currentFocus is EditText) return super.dispatchKeyEvent(event)
        val p = player ?: return super.dispatchKeyEvent(event)
        val ctrl = event.isCtrlPressed
        val shift = event.isShiftPressed
        when (event.keyCode) {
            KeyEvent.KEYCODE_SPACE -> { p.playWhenReady = !p.playWhenReady; showStatus(if (p.playWhenReady) "▶ Playing" else "⏸ Paused"); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { p.seekTo((p.currentPosition - if (ctrl) 30000 else if (shift) 60000 else 5000).coerceAtLeast(0)); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { p.seekTo(p.currentPosition + if (ctrl) 30000 else if (shift) 60000 else 5000); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { p.volume = (p.volume + 0.1f).coerceAtMost(1f); showStatus("🔊 ${(p.volume * 100).toInt()}%"); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { p.volume = (p.volume - 0.1f).coerceAtLeast(0f); showStatus("🔉 ${(p.volume * 100).toInt()}%"); return true }
            KeyEvent.KEYCODE_M -> { p.volume = if (p.volume > 0f) 0f else 1f; showStatus(if (p.volume == 0f) "🔇 Muted" else "🔊 Unmuted"); return true }
            KeyEvent.KEYCODE_X -> { changeSpeed(-0.1f); return true }
            KeyEvent.KEYCODE_C -> { changeSpeed(0.1f); return true }
            KeyEvent.KEYCODE_Z -> { toggleNormalLastSpeed(); return true }
            KeyEvent.KEYCODE_HOME -> { if (ctrl) selectSubtitle(currentPosition.coerceAtLeast(0)) else previousSubtitle(); return true }
            KeyEvent.KEYCODE_MOVE_END -> { nextSubtitle(); return true }
            KeyEvent.KEYCODE_INSERT, KeyEvent.KEYCODE_BACKSLASH -> { toggleLoop(); return true }
            KeyEvent.KEYCODE_COMMA -> { changeSubtitleOffset(-500); return true }
            KeyEvent.KEYCODE_PERIOD -> { changeSubtitleOffset(500); return true }
            KeyEvent.KEYCODE_SLASH -> { subtitleOffsetMs = 0; syncSubtitleToPlayer(); showStatus("Subtitle sync reset"); return true }
            KeyEvent.KEYCODE_LEFT_BRACKET -> { showStatus("A = current subtitle"); return true }
            KeyEvent.KEYCODE_RIGHT_BRACKET -> { showStatus("B = current subtitle"); return true }
            KeyEvent.KEYCODE_ENTER -> { playerControls.performClick(); return true }
        }
        return super.dispatchKeyEvent(event)
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
        playerControls = findViewById(R.id.playerControls)
        mediaButton = findViewById(R.id.mediaButton)
        videoToggleButton = findViewById(R.id.videoToggleButton)
        speedButton = findViewById(R.id.speedButton)
        loopButton = findViewById(R.id.loopButton)
        statusText = findViewById(R.id.statusText)

        subtitleList.layoutManager = LinearLayoutManager(this)
        subtitleAdapter = SubtitleAdapter(emptyList(), emptyList()) { selectSubtitle(it) }
        subtitleList.adapter = subtitleAdapter

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { searchQuery = s?.toString()?.trim() ?: ""; applySearch() }
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

    private fun setupButtons() {
        findViewById<Button>(R.id.openButton).setOnClickListener { openDocument.launch(arrayOf("application/x-subrip", "text/srt", "text/plain", "application/octet-stream", "*/*")) }
        mediaButton.setOnClickListener { openMedia.launch(arrayOf("video/*", "audio/*", "application/octet-stream")) }
        videoToggleButton.setOnClickListener { toggleVideo() }
        speedButton.setOnClickListener { showSpeedMenu() }
        loopButton.setOnClickListener { toggleLoop() }
        previousButton.setOnClickListener { previousSubtitle() }
        nextButton.setOnClickListener { nextSubtitle() }
    }

    private fun loadMedia(uri: Uri) {
        try {
            if (player == null) {
                player = ExoPlayer.Builder(this).build().also { exo ->
                    playerView.player = exo
                    playerControls.player = exo
                    exo.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) { syncSubtitleToPlayer() }
                    })
                }
            }
            player?.setMediaItem(MediaItem.fromUri(uri))
            player?.setPlaybackSpeed(playbackSpeed)
            player?.prepare()
            player?.playWhenReady = true
            fileNameText.text = uri.lastPathSegment ?: getString(R.string.media_file)
            videoToggleButton.visibility = View.VISIBLE
            speedButton.visibility = View.VISIBLE
            loopButton.visibility = View.VISIBLE
            playerControls.visibility = View.VISIBLE
        } catch (exception: Exception) { Toast.makeText(this, "Media error: ${exception.message}", Toast.LENGTH_LONG).show() }
    }

    private fun toggleVideo() {
        videoHidden = !videoHidden
        playerView.visibility = if (videoHidden) View.GONE else View.VISIBLE
        playerControls.visibility = if (player != null) View.VISIBLE else View.GONE
        videoToggleButton.text = if (videoHidden) getString(R.string.show_video) else getString(R.string.hide_video)
        showStatus(if (videoHidden) "🎧 Audio Only — video hidden" else "🎬 Video shown")
    }

    private fun loadSubtitle(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { input -> BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText() }
            if (text.isNullOrBlank()) { Toast.makeText(this, getString(R.string.empty_file), Toast.LENGTH_SHORT).show(); return }
            val parsed = SubtitleParser.parse(text)
            if (parsed.isEmpty()) { Toast.makeText(this, getString(R.string.no_subtitles), Toast.LENGTH_LONG).show(); return }
            subtitles = parsed
            currentPosition = 0
            subtitleOffsetMs = 0
            searchQuery = ""
            searchInput.setText("")
            if (player == null) fileNameText.text = uri.lastPathSegment ?: getString(R.string.subtitle_file)
            subtitleAdapter.setAllItems(subtitles)
            applySearch()
            syncSubtitleToPlayer()
        } catch (exception: Exception) { Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_LONG).show() }
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
        return subtitles.filter { s -> s.index.toString().contains(searchQuery) || s.text.lowercase(Locale.getDefault()).contains(q) }
    }

    private fun selectFirstSearchResult(results: List<Subtitle>) {
        val first = results.firstOrNull() ?: return
        val original = subtitles.indexOfFirst { it.index == first.index && it.startTime == first.startTime }
        if (original >= 0) selectSubtitle(original)
    }

    private fun selectSubtitle(position: Int) {
        if (position !in subtitles.indices) return
        currentPosition = position
        repeatCount = 0
        val subtitle = subtitles[position]
        player?.seekTo((subtitle.startTime - subtitleOffsetMs).coerceAtLeast(0))
        player?.playWhenReady = true
        scrollToSubtitle(subtitle)
        updateUi()
    }

    private fun syncSubtitleToPlayer() {
        val p = player ?: return
        if (subtitles.isEmpty()) return
        val time = p.currentPosition + subtitleOffsetMs
        val index = subtitles.indexOfLast { it.startTime <= time }
        if (index >= 0 && time < subtitles[index].endTime) {
            if (index != currentPosition) {
                currentPosition = index
                updateUi()
                scrollToSubtitle(subtitles[index])
            }
            if (loopSubtitle && time >= subtitles[index].endTime - 250 && !loopGuard) {
                loopGuard = true
                if (repeatCount + 1 >= repeatTarget) {
                    loopSubtitle = false
                    repeatCount = 0
                    updateLoopButton()
                    showStatus("▶ Loop finished — 5 plays")
                } else {
                    repeatCount++
                    p.seekTo((subtitles[index].startTime - subtitleOffsetMs).coerceAtLeast(0))
                    p.playWhenReady = true
                }
                syncHandler.postDelayed({ loopGuard = false }, 350)
            }
        }
    }

    private fun scrollToSubtitle(subtitle: Subtitle) {
        val visible = subtitleAdapter.visiblePositionOf(subtitle)
        if (visible >= 0) subtitleList.smoothScrollToPosition(visible)
    }

    private fun nextSubtitle() { if (subtitles.isNotEmpty() && currentPosition < subtitles.lastIndex) selectSubtitle(currentPosition + 1) }
    private fun previousSubtitle() { if (subtitles.isNotEmpty() && currentPosition > 0) selectSubtitle(currentPosition - 1) }

    private fun toggleLoop() {
        if (subtitles.isEmpty() || currentPosition !in subtitles.indices) return
        loopSubtitle = !loopSubtitle
        repeatCount = 0
        repeatTarget = 5
        updateLoopButton()
        showStatus(if (loopSubtitle) "🔁 Loop ON — 5 plays" else "⏹ Loop OFF")
    }

    private fun updateLoopButton() { loopButton.text = if (loopSubtitle) getString(R.string.loop_on) else getString(R.string.loop_off) }

    private fun showSpeedMenu() {
        val popup = android.widget.PopupMenu(this, speedButton)
        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed -> popup.menu.add(String.format(Locale.US, "%.2fx", speed)).setOnMenuItemClickListener { setSpeed(speed); true } }
        popup.show()
    }

    private fun changeSpeed(delta: Float) { setSpeed((playbackSpeed + delta).coerceIn(0.25f, 3.0f)) }
    private fun setSpeed(speed: Float) {
        if (speed != 1.0f) lastNonDefaultSpeed = speed
        playbackSpeed = speed
        player?.setPlaybackSpeed(speed)
        speedButton.text = String.format(Locale.US, "%.1fx", speed)
        showStatus(String.format(Locale.US, "▶ Speed %.1fx", speed))
    }
    private fun toggleNormalLastSpeed() { if (playbackSpeed == 1.0f) setSpeed(lastNonDefaultSpeed) else setSpeed(1.0f) }

    private fun changeSubtitleOffset(delta: Long) {
        subtitleOffsetMs += delta
        syncSubtitleToPlayer()
        val sign = if (subtitleOffsetMs >= 0) "+" else ""
        showStatus("Subtitle sync $sign${subtitleOffsetMs / 1000.0}s")
    }

    private fun showStatus(message: String) {
        statusText.removeCallbacks(hideStatusRunnable)
        statusText.text = message
        statusText.visibility = View.VISIBLE
        statusText.postDelayed(hideStatusRunnable, 1400)
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
        subtitleAdapter.setCurrentIndex(subtitles[currentPosition].index)
    }

    private class SubtitleAdapter(
        private var items: List<Subtitle>,
        private var allItems: List<Subtitle>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {
        private var currentIndex: Int? = null
        fun setAllItems(items: List<Subtitle>) { allItems = items }
        fun setItems(newItems: List<Subtitle>) { items = newItems; notifyDataSetChanged() }
        fun setCurrentIndex(index: Int) { currentIndex = index; notifyDataSetChanged() }
        fun visiblePositionOf(subtitle: Subtitle): Int = items.indexOfFirst { it.index == subtitle.index && it.startTime == subtitle.startTime }
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder = ViewHolder(android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_subtitle, parent, false))
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val subtitle = items[position]
            holder.number.text = subtitle.index.toString()
            holder.text.text = BidiUtils.format(subtitle.text)
            holder.time.text = formatTime(subtitle.startTime)
            holder.itemView.alpha = if (subtitle.index == currentIndex) 1f else 0.75f
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