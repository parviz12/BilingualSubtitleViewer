package com.example.bilingualsubviewer

import android.app.ComponentCaller
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.graphics.Typeface
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
    private lateinit var navigationPlayPauseButton: Button
    private lateinit var emptyText: TextView
    private lateinit var searchInput: EditText
    private lateinit var playerView: PlayerView
    private lateinit var mediaButton: Button
    private lateinit var videoToggleButton: Button
    private lateinit var speedButton: Button
    private lateinit var loopButton: Button
    private lateinit var statusText: TextView
    private lateinit var subtitleAdapter: SubtitleAdapter
    private lateinit var playerControls: View
    private lateinit var playPauseButton: Button
    private lateinit var currentTimeText: TextView
    private lateinit var durationTimeText: TextView
    private lateinit var timelineSeekBar: SeekBar

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
    private var userSeeking = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideStatus = Runnable { statusText.visibility = View.GONE }
    private val syncRunnable = object : Runnable {
        override fun run() { syncSubtitleToPlayer(); updatePlaybackControls(); handler.postDelayed(this, 100) }
    }

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::loadSubtitle) }
    private val openMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::loadMedia) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews(); setupInsets(); setupButtons()
        if (savedInstanceState == null && intent.action == Intent.ACTION_VIEW) intent.data?.let(::loadSubtitle)
        updateUi(); handler.post(syncRunnable)
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller); setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let(::loadSubtitle)
    }

    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); statusText.removeCallbacks(hideStatus); player?.release(); player=null; super.onDestroy() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || player == null || currentFocus is EditText) return super.dispatchKeyEvent(event)
        val p=player!!; val ctrl=event.isCtrlPressed; val shift=event.isShiftPressed
        when(event.keyCode){
            KeyEvent.KEYCODE_SPACE->{togglePlayback();return true}
            KeyEvent.KEYCODE_DPAD_LEFT->{p.seekTo((p.currentPosition-if(ctrl)30000 else if(shift)60000 else 5000).coerceAtLeast(0));return true}
            KeyEvent.KEYCODE_DPAD_RIGHT->{p.seekTo(p.currentPosition+(if(ctrl)30000 else if(shift)60000 else 5000));return true}
            KeyEvent.KEYCODE_DPAD_UP->{p.volume=(p.volume+.1f).coerceAtMost(1f);return true}
            KeyEvent.KEYCODE_DPAD_DOWN->{p.volume=(p.volume-.1f).coerceAtLeast(0f);return true}
            KeyEvent.KEYCODE_M->{p.volume=if(p.volume>0)0f else 1f;return true}
            KeyEvent.KEYCODE_X->{changeSpeed(-.1f);return true}
            KeyEvent.KEYCODE_C->{changeSpeed(.1f);return true}
            KeyEvent.KEYCODE_Z->{toggleNormalLastSpeed();return true}
            KeyEvent.KEYCODE_HOME->{if(ctrl)selectSubtitle(currentPosition.coerceAtLeast(0))else previousSubtitle();return true}
            KeyEvent.KEYCODE_MOVE_END->{nextSubtitle();return true}
            KeyEvent.KEYCODE_INSERT,KeyEvent.KEYCODE_BACKSLASH->{toggleLoop();return true}
        }
        return super.dispatchKeyEvent(event)
    }

    private fun setupViews(){
        subtitleList=findViewById(R.id.subtitleList); currentSubtitleText=findViewById(R.id.currentSubtitleText); positionText=findViewById(R.id.positionText); fileNameText=findViewById(R.id.fileNameText)
        previousButton=findViewById(R.id.previousButton); nextButton=findViewById(R.id.nextButton); navigationPlayPauseButton=findViewById(R.id.navigationPlayPauseButton); emptyText=findViewById(R.id.emptyText); searchInput=findViewById(R.id.searchInput)
        playerView=findViewById(R.id.playerView); mediaButton=findViewById(R.id.mediaButton); videoToggleButton=findViewById(R.id.videoToggleButton); speedButton=findViewById(R.id.speedButton); loopButton=findViewById(R.id.loopButton); statusText=findViewById(R.id.statusText)
        playerControls=findViewById(R.id.playerControls); playPauseButton=findViewById(R.id.playPauseButton); currentTimeText=findViewById(R.id.currentTimeText); durationTimeText=findViewById(R.id.durationTimeText); timelineSeekBar=findViewById(R.id.timelineSeekBar)
        subtitleList.layoutManager=LinearLayoutManager(this); subtitleAdapter=SubtitleAdapter(emptyList(),emptyList()){selectSubtitle(it)}; subtitleList.adapter=subtitleAdapter
        searchInput.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int)=Unit;override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){searchQuery=s?.toString()?.trim() ?: "";applySearch()};override fun afterTextChanged(s:android.text.Editable?)=Unit})
        timelineSeekBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:SeekBar){userSeeking=true};override fun onStopTrackingTouch(s:SeekBar){player?.seekTo(s.progress.toLong());userSeeking=false};override fun onProgressChanged(s:SeekBar,p:Int,fromUser:Boolean){if(fromUser)currentTimeText.text=formatDuration(p.toLong())}})
    }

    private fun setupInsets(){val root=findViewById<View>(R.id.rootContainer);ViewCompat.setOnApplyWindowInsetsListener(root){v,i->val b=i.getInsets(WindowInsetsCompat.Type.systemBars());v.updatePadding(left=b.left,top=b.top,right=b.right,bottom=b.bottom);i}}
    private fun setupButtons(){
        findViewById<Button>(R.id.openButton).setOnClickListener{openDocument.launch(arrayOf("application/x-subrip","text/srt","text/plain","application/octet-stream","*/*"))}
        mediaButton.setOnClickListener{openMedia.launch(arrayOf("video/*","audio/*","application/octet-stream"))};videoToggleButton.setOnClickListener{toggleVideo()};speedButton.setOnClickListener{showSpeedMenu()};loopButton.setOnClickListener{toggleLoop()};previousButton.setOnClickListener{previousSubtitle()};nextButton.setOnClickListener{nextSubtitle()};playPauseButton.setOnClickListener{togglePlayback()};navigationPlayPauseButton.setOnClickListener{togglePlayback()}
    }

    private fun togglePlayback(){player?.let{it.playWhenReady=!it.playWhenReady;updatePlaybackControls()}}

    private fun loadMedia(uri:Uri){try{
        if(player==null){player=ExoPlayer.Builder(this).build().also{exo->playerView.player=exo;exo.addListener(object:Player.Listener{override fun onPlaybackStateChanged(s:Int){updatePlaybackControls()}})}}
        player?.setMediaItem(MediaItem.fromUri(uri));player?.setPlaybackSpeed(playbackSpeed);player?.prepare();player?.playWhenReady=true
        fileNameText.text=uri.lastPathSegment?:getString(R.string.media_file)
        videoHidden=false
        playerView.visibility=View.VISIBLE
        playerView.alpha=1f
        playerView.requestLayout()
        playerControls.visibility=View.VISIBLE
        videoToggleButton.visibility=View.VISIBLE;speedButton.visibility=View.VISIBLE;loopButton.visibility=View.VISIBLE
        videoToggleButton.text=getString(R.string.hide_video)
        updatePlaybackControls()
    }catch(e:Exception){Toast.makeText(this,"Media error: ${e.message}",Toast.LENGTH_LONG).show()}}

    private fun toggleVideo(){
        if(player==null)return
        videoHidden=!videoHidden
        playerView.visibility=if(videoHidden)View.GONE else View.VISIBLE
        playerControls.visibility=View.VISIBLE
        videoToggleButton.text=if(videoHidden)getString(R.string.show_video)else getString(R.string.hide_video)
        showStatus(if(videoHidden)"🎧 Audio Only — video hidden" else "🎬 Video shown")
    }

    private fun loadSubtitle(uri:Uri){try{val text=contentResolver.openInputStream(uri)?.use{BufferedReader(InputStreamReader(it,Charsets.UTF_8)).readText()};if(text.isNullOrBlank()){Toast.makeText(this,getString(R.string.empty_file),Toast.LENGTH_SHORT).show();return};val parsed=SubtitleParser.parse(text);if(parsed.isEmpty()){Toast.makeText(this,getString(R.string.no_subtitles),Toast.LENGTH_LONG).show();return};subtitles=parsed;currentPosition=0;repeatCount=0;subtitleOffsetMs=0;searchQuery="";searchInput.setText("");if(player==null)fileNameText.text=uri.lastPathSegment?:getString(R.string.subtitle_file);subtitleAdapter.setAllItems(subtitles);applySearch();syncSubtitleToPlayer()}catch(e:Exception){Toast.makeText(this,"Error: ${e.message}",Toast.LENGTH_LONG).show()}}
    private fun applySearch(){val f=filteredSubtitles();subtitleAdapter.setItems(f);if(searchQuery.isNotBlank()&&f.isEmpty()){emptyText.text=getString(R.string.no_search_results);emptyText.visibility=View.VISIBLE;return};emptyText.text=getString(R.string.open_subtitle_message);updateUi();if(searchQuery.isNotBlank()&&f.isNotEmpty())selectFirstSearchResult(f)}
    private fun filteredSubtitles():List<Subtitle>{if(searchQuery.isBlank())return subtitles;val q=searchQuery.lowercase(Locale.getDefault());return subtitles.filter{s->s.index.toString().contains(searchQuery)||s.text.lowercase(Locale.getDefault()).contains(q)}}
    private fun selectFirstSearchResult(r:List<Subtitle>){val f=r.firstOrNull()?:return;val i=subtitles.indexOfFirst{s->s.index==f.index&&s.startTime==f.startTime};if(i>=0)selectSubtitle(i)}
    private fun selectSubtitle(i:Int){if(i !in subtitles.indices)return;currentPosition=i;repeatCount=0;val s=subtitles[i];player?.seekTo((s.startTime-subtitleOffsetMs).coerceAtLeast(0));player?.playWhenReady=true;scrollToSubtitle(s);updateUi();updatePlaybackControls()}
    private fun syncSubtitleToPlayer(){val p=player?:return;if(subtitles.isEmpty())return;val time=p.currentPosition+subtitleOffsetMs;val i=subtitles.indexOfLast{it.startTime<=time};if(i>=0&&time<subtitles[i].endTime){if(i!=currentPosition){currentPosition=i;updateUi();scrollToSubtitle(subtitles[i])};if(loopSubtitle&&time>=subtitles[i].endTime-250&&!loopGuard){loopGuard=true;repeatCount++;if(repeatCount>=repeatTarget){repeatCount=0;showStatus("🔁 Loop: 5/5 — continuing")};p.seekTo((subtitles[i].startTime-subtitleOffsetMs).coerceAtLeast(0));p.playWhenReady=true;handler.postDelayed({loopGuard=false},350)}}}
    private fun scrollToSubtitle(s:Subtitle){val p=subtitleAdapter.visiblePositionOf(s);if(p>=0)subtitleList.smoothScrollToPosition(p)}
    private fun nextSubtitle(){if(subtitles.isNotEmpty()&&currentPosition<subtitles.lastIndex)selectSubtitle(currentPosition+1)};private fun previousSubtitle(){if(subtitles.isNotEmpty()&&currentPosition>0)selectSubtitle(currentPosition-1)}
    private fun toggleLoop(){if(subtitles.isEmpty()||currentPosition !in subtitles.indices)return;loopSubtitle=!loopSubtitle;repeatCount=0;repeatTarget=5;updateLoopButton();showStatus(if(loopSubtitle)"🔁 Loop ON — groups of 5"else"⏹ Loop OFF")};private fun updateLoopButton(){loopButton.text=if(loopSubtitle)getString(R.string.loop_on)else getString(R.string.loop_off)}
    private fun showSpeedMenu(){val p=android.widget.PopupMenu(this,speedButton);listOf(.5f,.75f,1f,1.25f,1.5f,1.75f,2f).forEach{s->p.menu.add(String.format(Locale.US,"%.2fx",s)).setOnMenuItemClickListener{setSpeed(s);true}};p.show()};private fun changeSpeed(d:Float){setSpeed((playbackSpeed+d).coerceIn(.25f,3f))};private fun setSpeed(s:Float){if(s!=1f)lastNonDefaultSpeed=s;playbackSpeed=s;player?.setPlaybackSpeed(s);speedButton.text=String.format(Locale.US,"%.1fx",s);showStatus("▶ Speed %.1fx".format(Locale.US,s))};private fun toggleNormalLastSpeed(){setSpeed(if(playbackSpeed==1f)lastNonDefaultSpeed else 1f)}
    private fun changeSubtitleOffset(d:Long){subtitleOffsetMs+=d;syncSubtitleToPlayer();showStatus("Subtitle sync ${if(subtitleOffsetMs>=0)"+"else""}${subtitleOffsetMs/1000.0}s")};private fun showStatus(m:String){statusText.removeCallbacks(hideStatus);statusText.text=m;statusText.visibility=View.VISIBLE;statusText.postDelayed(hideStatus,1400)}
    private fun updatePlaybackControls(){val p=player?:return;val d=p.duration.coerceAtLeast(0);val pos=p.currentPosition.coerceAtLeast(0);if(!userSeeking){timelineSeekBar.max=if(d>0)d.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()else 0;timelineSeekBar.progress=if(d>0)pos.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()else 0;currentTimeText.text=formatDuration(pos)};durationTimeText.text=formatDuration(d);val icon=if(p.isPlaying||p.playWhenReady)"⏸"else"▶";playPauseButton.text=icon;navigationPlayPauseButton.text=icon}
    private fun formatDuration(ms:Long):String{val t=ms.coerceAtLeast(0)/1000;return if(t>=3600)String.format(Locale.US,"%d:%02d:%02d",t/3600,(t%3600)/60,t%60)else String.format(Locale.US,"%02d:%02d",t/60,t%60)}
    private fun updateUi(){val h=subtitles.isNotEmpty()&&currentPosition in subtitles.indices;emptyText.visibility=if(h&&(searchQuery.isBlank()||subtitleAdapter.itemCount>0))View.GONE else View.VISIBLE;currentSubtitleText.visibility=View.GONE;if(!h){positionText.text="0 / 0";previousButton.isEnabled=false;nextButton.isEnabled=false;return};positionText.text=String.format(Locale.US,"%d / %d",currentPosition+1,subtitles.size);previousButton.isEnabled=currentPosition>0;nextButton.isEnabled=currentPosition<subtitles.lastIndex;subtitleAdapter.setCurrentIndex(subtitles[currentPosition].index)}

    private class SubtitleAdapter(private var items:List<Subtitle>,private var allItems:List<Subtitle>,private val onClick:(Int)->Unit):RecyclerView.Adapter<SubtitleAdapter.ViewHolder>(){private var currentIndex:Int?=null;fun setAllItems(x:List<Subtitle>){allItems=x};fun setItems(x:List<Subtitle>){items=x;notifyDataSetChanged()};fun setCurrentIndex(x:Int){currentIndex=x;notifyDataSetChanged()};fun visiblePositionOf(s:Subtitle)=items.indexOfFirst{it.index==s.index&&it.startTime==s.startTime};override fun onCreateViewHolder(p:android.view.ViewGroup,t:Int)=ViewHolder(android.view.LayoutInflater.from(p.context).inflate(R.layout.item_subtitle,p,false));override fun onBindViewHolder(h:ViewHolder,p:Int){val s=items[p];val active=s.index==currentIndex;h.number.text=s.index.toString();h.text.text=BidiUtils.format(s.text);h.time.text=formatTime(s.startTime);h.itemView.alpha=if(active)1f else .75f;h.itemView.setBackgroundResource(if(active)R.drawable.subtitle_current_background else android.R.color.transparent);h.text.setTypeface(null,if(active)Typeface.BOLD else Typeface.NORMAL);h.number.setTypeface(null,Typeface.BOLD);h.itemView.setOnClickListener{val i=allItems.indexOfFirst{it.index==s.index&&it.startTime==s.startTime};if(i>=0)onClick(i)}};override fun getItemCount()=items.size;private fun formatTime(ms:Long):String{val t=ms/1000;return String.format(Locale.US,"%02d:%02d",t/60,t%60)};class ViewHolder(v:View):RecyclerView.ViewHolder(v){val number:TextView=v.findViewById(R.id.itemNumber);val text:TextView=v.findViewById(R.id.itemText);val time:TextView=v.findViewById(R.id.itemTime)}}
}
