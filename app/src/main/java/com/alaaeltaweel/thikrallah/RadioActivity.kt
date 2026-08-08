package com.alaaeltaweel.thikrallah

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    @Composable
    private fun RadioScreen() {
        var playingId by remember { mutableIntStateOf(-1) }
        var loadingId by remember { mutableIntStateOf(-1) }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2E7D32))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "إذاعات القرآن الكريم",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(channels, key = { it.id }) { channel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        when {
                            loadingId == channel.id -> CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF1B5E20),
                                strokeWidth = 2.dp
                            )
                            playingId == channel.id -> Text("⏸", fontSize = 20.sp)
                            else -> Text("▶", fontSize = 20.sp)
                        }
                        Text(
                            text = channel.name,
                            fontSize = 15.sp,
                            color = Color.White,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE0E0E0))
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
