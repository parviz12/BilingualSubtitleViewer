package com.example.bilingualsubviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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

    private var subtitles: List<Subtitle> = emptyList()
    private var currentPosition = -1

    private lateinit var gestureDetector: GestureDetector

    private val openDocument =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {
                loadSubtitle(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        setupViews()
        setupInsets()
        setupGestures()
        setupButtons()

        updateUi()
    }

    private fun setupViews() {

        subtitleList = findViewById(R.id.subtitleList)
        currentSubtitleText = findViewById(R.id.currentSubtitleText)
        positionText = findViewById(R.id.positionText)
        fileNameText = findViewById(R.id.fileNameText)

        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)

        emptyText = findViewById(R.id.emptyText)

        subtitleList.layoutManager =
            LinearLayoutManager(this)

        subtitleList.adapter =
            SubtitleAdapter(
                emptyList()
            ) { position ->
                selectSubtitle(position)
            }
    }

    private fun setupInsets() {

        val root = findViewById<View>(R.id.rootContainer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->

            val systemBars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )

            insets
        }
    }

    private fun setupGestures() {

        gestureDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {

                    override fun onDown(event: MotionEvent): Boolean {
                        return true
                    }

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {

                        if (e1 == null) return false

                        val dx = e2.x - e1.x
                        val dy = e2.y - e1.y

                        if (abs(dx) < 100) return false
                        if (abs(dx) < abs(dy)) return false
                        if (abs(velocityX) < 100) return false

                        if (dx < 0) {
                            nextSubtitle()
                        } else {
                            previousSubtitle()
                        }

                        return true
                    }
                }
            )

        currentSubtitleText.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupButtons() {

        findViewById<Button>(R.id.openButton).setOnClickListener {
            openDocument.launch(
                arrayOf(
                    "text/*",
                    "application/octet-stream",
                    "*/*"
                )
            )
        }

        previousButton.setOnClickListener {
            previousSubtitle()
        }

        nextButton.setOnClickListener {
            nextSubtitle()
        }
    }

    private fun loadSubtitle(uri: Uri) {

        try {

            val text =
                contentResolver.openInputStream(uri)?.use { input ->

                    BufferedReader(
                        InputStreamReader(
                            input,
                            Charsets.UTF_8
                        )
                    ).readText()
                }

            if (text.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    getString(R.string.empty_file),
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

            subtitles =
                SubtitleParser.parse(text)

            if (subtitles.isEmpty()) {

                Toast.makeText(
                    this,
                    getString(R.string.no_subtitles),
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            currentPosition = 0

            fileNameText.text =
                uri.lastPathSegment
                    ?: getString(R.string.subtitle_file)

            subtitleList.adapter =
                SubtitleAdapter(
                    subtitles
                ) { position ->
                    selectSubtitle(position)
                }

            updateUi()

        } catch (exception: Exception) {

            Toast.makeText(
                this,
                "Error: ${exception.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun selectSubtitle(position: Int) {

        if (position !in subtitles.indices) return

        currentPosition = position

        subtitleList.scrollToPosition(position)

        updateUi()
    }

    private fun nextSubtitle() {

        if (subtitles.isEmpty()) return

        if (currentPosition < subtitles.lastIndex) {
            currentPosition++
            subtitleList.smoothScrollToPosition(currentPosition)
            updateUi()
        }
    }

    private fun previousSubtitle() {

        if (subtitles.isEmpty()) return

        if (currentPosition > 0) {
            currentPosition--
            subtitleList.smoothScrollToPosition(currentPosition)
            updateUi()
        }
    }

    private fun updateUi() {

        val hasSubtitles =
            subtitles.isNotEmpty() &&
            currentPosition in subtitles.indices

        emptyText.visibility =
            if (hasSubtitles) View.GONE else View.VISIBLE

        currentSubtitleText.visibility =
            if (hasSubtitles) View.VISIBLE else View.GONE

        if (!hasSubtitles) {

            positionText.text = "0 / 0"

            previousButton.isEnabled = false
            nextButton.isEnabled = false

            return
        }

        val subtitle =
            subtitles[currentPosition]

        currentSubtitleText.text =
            BidiUtils.format(subtitle.text)

        positionText.text =
            String.format(
                Locale.US,
                "%d / %d",
                currentPosition + 1,
                subtitles.size
            )

        previousButton.isEnabled =
            currentPosition > 0

        nextButton.isEnabled =
            currentPosition < subtitles.lastIndex

        subtitleList.post {
            subtitleList.scrollToPosition(currentPosition)
        }
    }

    private class SubtitleAdapter(
        private val items: List<Subtitle>,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

        override fun onCreateViewHolder(
            parent: android.view.ViewGroup,
            viewType: Int
        ): ViewHolder {

            val view =
                layoutInflater(parent.context)
                    .inflate(
                        R.layout.item_subtitle,
                        parent,
                        false
                    )

            return ViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int
        ) {

            val subtitle = items[position]

            holder.number.text =
                subtitle.index.toString()

            holder.text.text =
                BidiUtils.format(subtitle.text)

            holder.time.text =
                formatTime(subtitle.startTime)

            holder.itemView.setOnClickListener {
                onClick(position)
            }
        }

        override fun getItemCount(): Int =
            items.size

        private fun formatTime(milliseconds: Long): String {

            val totalSeconds =
                milliseconds / 1000

            val minutes =
                totalSeconds / 60

            val seconds =
                totalSeconds % 60

            return String.format(
                Locale.US,
                "%02d:%02d",
                minutes,
                seconds
            )
        }

        class ViewHolder(
            view: View
        ) : RecyclerView.ViewHolder(view) {

            val number: TextView =
                view.findViewById(R.id.itemNumber)

            val text: TextView =
                view.findViewById(R.id.itemText)

            val time: TextView =
                view.findViewById(R.id.itemTime)
        }

        private fun layoutInflater(
            context: android.content.Context
        ): android.view.LayoutInflater =
            android.view.LayoutInflater.from(context)
    }
}
