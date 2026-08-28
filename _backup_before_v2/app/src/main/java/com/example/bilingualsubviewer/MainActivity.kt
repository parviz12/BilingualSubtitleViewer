package com.example.bilingualsubviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var textSubtitle: TextView
    private lateinit var textPosition: TextView
    private lateinit var textTime: TextView
    private lateinit var textFileName: TextView

    private var subtitles: List<Subtitle> = emptyList()

    private var currentPosition = 0

    companion object {
        private const val REQUEST_OPEN_SRT = 1001
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        textSubtitle =
            findViewById(R.id.textSubtitle)

        textPosition =
            findViewById(R.id.textPosition)

        textTime =
            findViewById(R.id.textTime)

        textFileName =
            findViewById(R.id.textFileName)

        findViewById<Button>(
            R.id.buttonOpen
        ).setOnClickListener {
            openFilePicker()
        }

        findViewById<Button>(
            R.id.buttonPrevious
        ).setOnClickListener {
            showPrevious()
        }

        findViewById<Button>(
            R.id.buttonNext
        ).setOnClickListener {
            showNext()
        }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(intent)

        setIntent(intent)

        handleIncomingIntent(intent)
    }

    private fun openFilePicker() {

        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT)

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.type = "text/*"

        startActivityForResult(
            intent,
            REQUEST_OPEN_SRT
        )
    }

    @Deprecated(
        "Legacy API retained for broad Android compatibility."
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == REQUEST_OPEN_SRT &&
            resultCode == RESULT_OK &&
            data?.data != null
        ) {

            loadSubtitle(data.data!!)
        }
    }

    private fun handleIncomingIntent(
        intent: Intent?
    ) {

        if (
            intent?.action ==
            Intent.ACTION_VIEW
        ) {

            intent.data?.let {
                loadSubtitle(it)
            }
        }
    }

    private fun loadSubtitle(
        uri: Uri
    ) {

        try {

            val content =
                contentResolver
                    .openInputStream(uri)
                    ?.bufferedReader(
                        Charsets.UTF_8
                    )
                    ?.use {
                        it.readText()
                    }

            if (content == null) {

                showError(
                    "Unable to read subtitle file."
                )

                return
            }

            subtitles =
                SubtitleParser.parse(content)

            if (subtitles.isEmpty()) {

                showError(
                    "No valid subtitles found."
                )

                return
            }

            currentPosition = 0

            textFileName.text =
                getFileName(uri)

            showCurrentSubtitle()

        } catch (e: Exception) {

            showError(
                "Could not open subtitle:\n" +
                        (e.message ?: "Unknown error")
            )
        }
    }

    private fun showCurrentSubtitle() {

        if (subtitles.isEmpty()) {
            return
        }

        val subtitle =
            subtitles[currentPosition]

        textSubtitle.text =
            subtitle.text

        BidiUtils.applyDirection(
            textSubtitle,
            subtitle.text
        )

        textPosition.text =
            "${currentPosition + 1} / ${subtitles.size}"

        textTime.text =
            "${formatTime(subtitle.startTime)} --> " +
            formatTime(subtitle.endTime)
    }

    private fun showPrevious() {

        if (currentPosition > 0) {

            currentPosition--

            showCurrentSubtitle()
        }
    }

    private fun showNext() {

        if (
            currentPosition <
            subtitles.lastIndex
        ) {

            currentPosition++

            showCurrentSubtitle()
        }
    }

    private fun formatTime(
        milliseconds: Long
    ): String {

        val hours =
            milliseconds / 3_600_000

        val minutes =
            (milliseconds % 3_600_000) / 60_000

        val seconds =
            (milliseconds % 60_000) / 1_000

        val millis =
            milliseconds % 1_000

        return String.format(
            "%02d:%02d:%02d,%03d",
            hours,
            minutes,
            seconds,
            millis
        )
    }

    private fun getFileName(
        uri: Uri
    ): String {

        var result = "Subtitle"

        contentResolver.query(
            uri,
            arrayOf(
                OpenableColumns.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->

            if (cursor.moveToFirst()) {

                val index =
                    cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                if (index >= 0) {

                    result =
                        cursor.getString(index)
                }
            }
        }

        return result
    }

    private fun showError(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
