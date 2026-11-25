package com.alaaeltaweel.thikrallah

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RadioChannelItem(val id: Int, val name: String, val url: String)

class RadioActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    private val channels = listOf(
        RadioChannelItem(1,  "إذاعة أبو بكر الشاطري",           "https://backup.qurango.net/radio/shaik_abu_bakr_al_shatri"),
        RadioChannelItem(2,  "إذاعة أحمد خضر الطرابلسي",        "https://backup.qurango.net/radio/ahmad_khader_altarabulsi"),
        RadioChannelItem(3,  "إذاعة إبراهيم الأخضر",            "https://backup.qurango.net/radio/ibrahim_alakdar"),
        RadioChannelItem(4,  "إذاعة خالد الجليل",               "https://backup.qurango.net/radio/khalid_aljileel"),
        RadioChannelItem(5,  "إذاعة صلاح الهاشم",               "https://backup.qurango.net/radio/salah_alhashim"),
        RadioChannelItem(6,  "إذاعة صلاح بو خاطر",              "https://backup.qurango.net/radio/slaah_bukhatir"),
        RadioChannelItem(7,  "إذاعة عبد الباسط عبد الصمد",      "https://backup.qurango.net/radio/abdulbasit_abdulsamad_mojawwad"),
        RadioChannelItem(8,  "إذاعة عبد العزيز سحيم",           "https://backup.qurango.net/radio/a_sheim"),
        RadioChannelItem(9,  "إذاعة فارس عباد",                 "https://backup.qurango.net/radio/fares_abbad"),
        RadioChannelItem(10, "إذاعة ماهر المعيقلي",             "https://backup.qurango.net/radio/maher"),
        RadioChannelItem(11, "إذاعة محمد صديق المنشاوي",         "https://backup.qurango.net/radio/mohammed_siddiq_alminshawi_mojawwad"),
        RadioChannelItem(12, "إذاعة محمود خليل الحصري",          "https://backup.qurango.net/radio/mahmoud_khalil_alhussary_mojawwad"),
        RadioChannelItem(13, "إذاعة محمود علي البنا",            "https://backup.qurango.net/radio/mahmoud_ali__albanna_mojawwad"),
        RadioChannelItem(14, "إذاعة علي الحذيفي",               "https://qurango.net/radio/ali_alhuthaifi"),
        RadioChannelItem(15, "إذاعة ناصر القطامي",              "https://backup.qurango.net/radio/nasser_alqatami"),
        RadioChannelItem(16, "إذاعة نبيل الرفاعي",              "https://backup.qurango.net/radio/nabil_al_rifay"),
        RadioChannelItem(17, "إذاعة هيثم الجدعاني",             "https://backup.qurango.net/radio/hitham_aljadani"),
        RadioChannelItem(18, "إذاعة ياسر الدوسري",              "https://backup.qurango.net/radio/yasser_aldosari"),
       RadioChannelItem(19, "ترتيل ونغمات إسلامية",            "https://backup.qurango.net/radio/tarateel"),
        RadioChannelItem(20, "إذاعة السنة النبوية",             "https://radiosunna.radioca.st/stream"),
        RadioChannelItem(21, "إذاعة تلاوات خاشعة",              "https://backup.qurango.net/radio/salma"),
        RadioChannelItem(22, "إذاعة الرقية الشرعية",            "https://backup.qurango.net/radio/roqiah"),
        RadioChannelItem(23, "إذاعة سعد الغامدي",               "https://backup.qurango.net/radio/saad_alghamdi"),
        RadioChannelItem(24, "المختصر في تفسير القرآن الكريم",   "https://backup.qurango.net/radio/mukhtasartafsir"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RadioScreen()
        }
    }

    /** أيقونة التشغيل: مثلث غامق على الدايرة الذهبية. */
    @Composable
    private fun PlayIcon(color: Color) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width * 0.15f, size.height)
                close()
            }
            drawPath(path, color)
        }
    }

    /** أيقونة الإيقاف المؤقت: عمودين. */
    @Composable
    private fun PauseIcon(color: Color) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val barWidth = size.width * 0.32f
            val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.05f, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.63f, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = radius
            )
        }
    }

    @Composable
    private fun RadioScreen() {
        var playingId by remember { mutableIntStateOf(-1) }
        var loadingId by remember { mutableIntStateOf(-1) }

        val dark = Color(0xFF0B1120)
        val gold = Color(0xFFD4AF37)
        val rowShape = RoundedCornerShape(18.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F2A43), dark, Color(0xFF0E2F2A))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF1B5E20))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "إذاعات القرآن الكريم",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "اختر الإذاعة واضغط للتشغيل",
                    color = Color(0xFFFFE082),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(gold)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(channels, key = { it.id }) { channel ->
                    val isPlaying = playingId == channel.id
                    val isLoading = loadingId == channel.id
                    val rowBrush = if (isPlaying) {
                        Brush.horizontalGradient(listOf(Color(0xFF1B5E20), Color(0xFF16233F)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF182847), Color(0xFF14213D)))
                    }
                    val borderColor = if (isPlaying) Color(0xFF66BB6A) else Color(0x80D4AF37)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .clip(rowShape)
                            .background(rowBrush)
                            .border(1.dp, borderColor, rowShape)
                            .clickable {
                                if (playingId == channel.id) {
                                    stopPlayer()
                                    playingId = -1
                                    loadingId = -1
                                } else {
                                    stopPlayer()
                                    loadingId = channel.id
                                    playingId = -1
                                    val mp = MediaPlayer()
                                    @Suppress("DEPRECATION")
                                    mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
                                    try {
                                        mp.setDataSource(channel.url)
                                        mp.setOnPreparedListener {
                                            it.start()
                                            loadingId = -1
                                            playingId = channel.id
                                        }
                                        mp.setOnErrorListener { _, _, _ ->
                                            loadingId = -1
                                            playingId = -1
                                            Toast.makeText(this@RadioActivity, "تعذّر الاتصال", Toast.LENGTH_SHORT).show()
                                            true
                                        }
                                        mp.prepareAsync()
                                        mediaPlayer = mp
                                    } catch (e: Exception) {
                                        loadingId = -1
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPlaying) Color(0xFFFFE082) else Color.White
                            )
                            if (isPlaying) {
                                Text(
                                    text = "يعمل الآن",
                                    fontSize = 12.sp,
                                    color = Color(0xFF81C784),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFF7DC6F), gold)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isLoading -> CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = dark,
                                    strokeWidth = 2.5.dp
                                )
                                isPlaying -> PauseIcon(dark)
                                else -> PlayIcon(dark)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun stopPlayer() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onDestroy() {
        stopPlayer()
        super.onDestroy()
    }
}
