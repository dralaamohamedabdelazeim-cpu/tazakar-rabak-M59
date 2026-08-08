package com.alaaeltaweel.thikrallah.presentation.screen.radio

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RadioViewModel : ViewModel() {

    private val _state = MutableStateFlow(RadioUiState())
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    private val CHANNELS = listOf(
        RadioUiState.RadioChannelUiState(1,  "إذاعة أبو بكر الشاطري",           "https://backup.qurango.net/radio/shaik_abu_bakr_al_shatri"),
        RadioUiState.RadioChannelUiState(2,  "إذاعة أحمد خضر الطرابلسي",        "https://backup.qurango.net/radio/ahmad_khader_altarabulsi"),
        RadioUiState.RadioChannelUiState(3,  "إذاعة إبراهيم الأخضر",            "https://backup.qurango.net/radio/ibrahim_alakdar"),
        RadioUiState.RadioChannelUiState(4,  "إذاعة خالد الجليل",               "https://backup.qurango.net/radio/khalid_aljileel"),
        RadioUiState.RadioChannelUiState(5,  "إذاعة صلاح الهاشم",               "https://backup.qurango.net/radio/salah_alhashim"),
        RadioUiState.RadioChannelUiState(6,  "إذاعة صلاح بو خاطر",              "https://backup.qurango.net/radio/slaah_bukhatir"),
        RadioUiState.RadioChannelUiState(7,  "إذاعة عبد الباسط عبد الصمد",       "https://backup.qurango.net/radio/abdulbasit_abdulsamad_mojawwad"),
        RadioUiState.RadioChannelUiState(8,  "إذاعة عبد العزيز سحيم",           "https://backup.qurango.net/radio/a_sheim"),
        RadioUiState.RadioChannelUiState(9,  "إذاعة فارس عباد",                 "https://backup.qurango.net/radio/fares_abbad"),
        RadioUiState.RadioChannelUiState(10, "إذاعة ماهر المعيقلي",             "https://backup.qurango.net/radio/maher"),
        RadioUiState.RadioChannelUiState(11, "إذاعة محمد صديق المنشاوي",         "https://backup.qurango.net/radio/mohammed_siddiq_alminshawi_mojawwad"),
        RadioUiState.RadioChannelUiState(12, "إذاعة محمود خليل الحصري",          "https://backup.qurango.net/radio/mahmoud_khalil_alhussary_mojawwad"),
        RadioUiState.RadioChannelUiState(13, "إذاعة محمود علي البنا",            "https://backup.qurango.net/radio/mahmoud_ali__albanna_mojawwad"),
        RadioUiState.RadioChannelUiState(14, "إذاعة علي الحذيفي",               "https://qurango.net/radio/ali_alhuthaifi"),
        RadioUiState.RadioChannelUiState(15, "إذاعة ناصر القطامي",              "https://backup.qurango.net/radio/nasser_alqatami"),
        RadioUiState.RadioChannelUiState(16, "إذاعة نبيل الرفاعي",              "https://backup.qurango.net/radio/nabil_al_rifay"),
        RadioUiState.RadioChannelUiState(17, "إذاعة هيثم الجدعاني",             "https://backup.qurango.net/radio/hitham_aljadani"),
        RadioUiState.RadioChannelUiState(18, "إذاعة ياسر الدوسري",              "https://backup.qurango.net/radio/yasser_aldosari"),
        RadioUiState.RadioChannelUiState(19, "إذاعة القرآن الكريم من القاهرة",     "https://stream.radiojar.com/0tpy1h0kxtzuv"),
        RadioUiState.RadioChannelUiState(20, "إذاعة السنة النبوية",               "https://stream.radiojar.com/8s5u5tpdtwzuv"),
        RadioUiState.RadioChannelUiState(21, "إذاعة تلاوات خاشعة",              "https://backup.qurango.net/radio/salma"),
        RadioUiState.RadioChannelUiState(22, "إذاعة الرقية الشرعية",            "https://backup.qurango.net/radio/roqiah"),
        RadioUiState.RadioChannelUiState(23, "إذاعة سعد الغامدي",               "https://backup.qurango.net/radio/saad_alghamdi"),
        RadioUiState.RadioChannelUiState(24, "المختصر في تفسير القرآن الكريم",   "https://backup.qurango.net/radio/mukhtasartafsir"),
    )

    init {
        _state.value = RadioUiState(channels = CHANNELS)
    }

    fun onPlayClick(id: Int) {
        val channel = _state.value.channels.firstOrNull { it.id == id } ?: return
        _state.value = _state.value.copy(
            channels = _state.value.channels.map {
                when {
                    it.id == id -> it.copy(selected = true, isLoading = true, isPlaying = false)
                    else -> it.copy(selected = false, isLoading = false, isPlaying = false)
                }
            }
        )
        // يُرسَل لـ RadioScreen عبر getPlayUrl()
        _playUrl = channel.streamUrl
        _playName = channel.nameAr
    }

    fun onChannelReady(id: Int) {
        _state.value = _state.value.copy(
            channels = _state.value.channels.map {
                if (it.id == id) it.copy(isLoading = false, isPlaying = true) else it
            }
        )
    }

    fun onPauseClick(id: Int) {
        _state.value = _state.value.copy(
            channels = _state.value.channels.map {
                if (it.id == id) it.copy(isPlaying = false, isLoading = false, selected = false) else it
            }
        )
        _playUrl = null
    }

    fun onPlayerError() {
        _state.value = _state.value.copy(
            channels = _state.value.channels.map {
                it.copy(isPlaying = false, isLoading = false, selected = false)
            }
        )
        _playUrl = null
    }

    // URL للتشغيل — يقرأها الـ Screen
    private var _playUrl: String? = null
    private var _playName: String? = null
    fun consumePlayUrl(): Pair<String, String>? {
        val url = _playUrl ?: return null
        val name = _playName ?: return null
        _playUrl = null
        _playName = null
        return Pair(url, name)
    }
}

