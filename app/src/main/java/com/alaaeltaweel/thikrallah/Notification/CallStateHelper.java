package com.alaaeltaweel.thikrallah.Notification;

import android.content.Context;
import android.media.AudioManager;
import android.telephony.TelephonyManager;

/**
 * فحص موحّد: هل في مكالمة دلوقتي؟
 * - وضع الصوت في النظام (من غير أي إذن): مكالمة عادية أو رنين مكالمة، ومكالمات الإنترنت (واتساب وغيره) لو includeVoip
 * - حالة الاتصال من TelephonyManager (محتاجة إذن READ_PHONE_STATE في أندرويد 12+، ولو مفيش إذن بنتجاهلها)
 */
public class CallStateHelper {
    public static boolean isInCall(Context ctx, boolean includeVoip) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getMode();
                if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE) return true;
                if (includeVoip && mode == AudioManager.MODE_IN_COMMUNICATION) return true;
            }
        } catch (Exception ignored) { }
        try {
            TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) return true;
        } catch (Exception ignored) { }   // SecurityException لو الإذن مش ممنوح
        return false;
    }
}
