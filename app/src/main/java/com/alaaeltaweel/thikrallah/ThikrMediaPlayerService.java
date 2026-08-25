package com.alaaeltaweel.thikrallah;





import android.annotation.SuppressLint;

import android.app.Notification;

import android.app.NotificationChannel;

import android.app.NotificationManager;

import android.app.PendingIntent;

import android.app.Service;

import android.content.ComponentName;

import android.content.Context;

import android.content.Intent;

import android.content.SharedPreferences;

import android.content.res.AssetFileDescriptor;

import android.content.res.Configuration;

import android.graphics.Color;

import android.media.AudioAttributes;

import android.media.AudioFocusRequest;

import android.media.AudioManager;

import android.media.MediaPlayer;

import android.media.MediaPlayer.OnCompletionListener;

import android.net.Uri;

import android.os.Build;

import android.os.Bundle;

import android.os.Handler;

import android.os.IBinder;

import android.os.Looper;

import android.os.Message;

import android.os.Messenger;

import android.os.PowerManager;

import android.os.RemoteException;

import android.os.Vibrator;

import android.preference.PreferenceManager;

import android.support.v4.media.MediaMetadataCompat;

import android.support.v4.media.session.MediaControllerCompat;

import android.support.v4.media.session.MediaSessionCompat;

import android.util.Log;

import android.widget.Toast;





import androidx.core.app.NotificationCompat;

import androidx.core.app.NotificationCompat.Action;

import androidx.media.app.NotificationCompat.MediaStyle;



import com.alaaeltaweel.thikrallah.Notification.MyAlarmsManager;

import com.alaaeltaweel.thikrallah.Notification.ThikrMediaBroadcastReciever;



import java.io.FileDescriptor;

import java.io.FileInputStream;

import java.io.IOException;

import java.lang.ref.WeakReference;

import java.util.ArrayList;

import java.util.Locale;

import java.util.Timer;

import java.util.TimerTask;

import java.util.regex.Matcher;

import java.util.regex.Pattern;



import static android.support.v4.media.MediaMetadataCompat.Builder;



import timber.log.Timber;



import android.telephony.PhoneStateListener;

import android.telephony.TelephonyManager;



public class ThikrMediaPlayerService extends Service implements OnCompletionListener,

        AudioManager.OnAudioFocusChangeListener, android.hardware.SensorEventListener {

    static String TAG = "ThikrMediaPlayerService";

    // ✅ حساس القلب - منقول من AthanScreenActivity عشان يشتغل حتى لو الشاشة مقفولة
    private android.hardware.SensorManager flipSensorManager;
    private android.hardware.Sensor flipAccelerometer;
    private boolean isMutedByFlipService = false;
    // ✅ كتم صوت الأذان بزرار الصوت حتى لو شاشة الأذان مقفولة/التطبيق في الخلفية (زي حساس القلب بالظبط)
    private VolumeButtonReceiver volumeButtonReceiver;

    public static final int MEDIA_PLAYER_PAUSE = 1;

    public static final int MEDIA_PLAYER_RESET = 2;

    public static final int MEDIA_PLAYER_PLAY = 3;

    public static final int MEDIA_PLAYER_PLAYALL = 4;

    public static final int MEDIA_PLAYER_ISPLAYING = 5;

    public static final int MEDIA_PLAYER_INNCREMENT = 6;

    public static final int MEDIA_PLAYER_CHANGE_VOLUME = 7;

    public static final int MEDIA_PLAYER_RESUME = 8;

    // ✅ تمت الإضافة: ثابت إيقاف الأذان

    public static final int MEDIA_PLAYER_STOP = 9;

    // ✅ كتم/إرجاع صوت الأذان بسبب قلب الهاتف (الشاشة تفضل زي ما هي، الصوت بس اللي بيتغير)
    public static final int MEDIA_PLAYER_MUTE_BY_FLIP = 10;
    public static final int MEDIA_PLAYER_UNMUTE_BY_FLIP = 11;

    // ✅ حماية الخدمة من القفل طول ما الدعاء شغال (خصوصًا في الأجهزة اللي بتقفل التطبيقات بسرعة)
    public static final int MEDIA_PLAYER_DUA_STARTED = 12;
    public static final int MEDIA_PLAYER_DUA_ENDED = 13;



    AudioManager am;

    int play_count = 0;

    private MediaPlayer player;

    private static long lastGeneralThikrPlayStartTime = 0; // ✅ لمنع تشغيل الذكر العام فوق نفسه
    private static long lastAthanPlayStartTime = 0; // ✅ لمنع تشغيل الأذان فوق نفسه

    public int currentThikrCounter = 0;

    private boolean isPaused;

    private final int NOTIFICATION_ID = 74;

    private int currentPlaying;

    private String ThikrType;

    private MediaSessionCompat mediaSession;

    private MediaControllerCompat mController;

    private boolean overRideRespectMute = false;

    // ✅ لمنع مؤقت تدرّج الصوت أو استرجاع الـ audio focus من إرجاع الصوت لوحده وقت الكتم بالقلب/زرار الصوت
    private boolean isMutedByFlip = false;
    // ✅ نسخة static عشان DuaPlayerHelper يقدر يعرف هل الأذان كان مكتوم، ويورّث نفس الحالة للدعاء
    public static volatile boolean lastAthanWasMuted = false;

    private boolean isUserAction = true;

    private NotificationCompat.Builder notificationBuilder;

    ArrayList<Messenger> mClients = new ArrayList<>();

    static final int MSG_CURRENT_PLAYING = 100;

    static final int MSG_UNBIND = 99;

    private String filepath;

    private Context mcontext;

    private Uri uri;



    static class IncomingHandler extends Handler {

        private final WeakReference<ThikrMediaPlayerService> mService;



        IncomingHandler(ThikrMediaPlayerService service) {

            mService = new WeakReference<>(service);

        }



        @Override

        public void handleMessage(Message msg) {

            ThikrMediaPlayerService service = mService.get();

            if (service != null) {

                Message msg2;

                switch (msg.what) {

                    case MSG_CURRENT_PLAYING:

                        service.mClients.clear();

                        service.mClients.add(msg.replyTo);

                        service.sendMessageToUI(MSG_CURRENT_PLAYING, service.getCurrentPlaying());

                        break;

                    default:

                        msg2 = Message.obtain(null, ThikrMediaPlayerService.MSG_CURRENT_PLAYING, 0, 0);

                        try {

                            msg.replyTo.send(msg2);

                        } catch (RemoteException e) {

                            e.printStackTrace();

                            Timber.e("%s", e.getMessage());

                        }

                        super.handleMessage(msg);

                }

            }

        }

    }



    final Messenger mMessenger = new Messenger(new IncomingHandler(this));



    private void sendMessageToUI(int what, int intvaluetosend) {

        for (int i = mClients.size() - 1; i >= 0; i--) {

            try {

                Message msg = Message.obtain(null, what, intvaluetosend, 0);

                Bundle data = new Bundle();

                data.putString("com.alaaeltaweel.thikrallah.datatype", this.getThikrType());

                msg.setData(data);

                mClients.get(i).send(msg);

            } catch (RemoteException e) {

                // client is dead

            }

        }

    }



    private void updateAllAlarms() {

        new Handler(Looper.getMainLooper()).postDelayed(new UpdateAlarmsRunnable(mcontext.getApplicationContext()), 5000);

    }



    private static class UpdateAlarmsRunnable implements Runnable {

        private final WeakReference<Context> mApplicationContext;



        UpdateAlarmsRunnable(Context context) {

            mApplicationContext = new WeakReference<>(context);

        }



        @Override

        public void run() {

            Context mContext = mApplicationContext.get();

            if (mContext != null) {

                Log.d(TAG, "calling UpdateAllApplicableAlarms from ThikrMediaPlayerService");

                new MyAlarmsManager(mApplicationContext.get()).UpdateAllApplicableAlarms();

            }

        }

    }



    @Override

    public IBinder onBind(Intent intent) {

        return mMessenger.getBinder();

    }



    @Override

    public void onCreate() {

        super.onCreate();

        Timber.d("ThikrMediaPlayerService onCreate");



        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        String lang = mPrefs.getString("language", null);



        if (lang != null) {

            Locale locale = new Locale(lang);

            Locale.setDefault(locale);

            Configuration config = new Configuration();

            config.locale = locale;

            getBaseContext().getResources().updateConfiguration(config,

                    getBaseContext().getResources().getDisplayMetrics());

        }

        Timber.d("oncreate called");

        initMediaPlayer();

        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            MyCallStateCallback callbackListener = new MyCallStateCallback();

            tm.registerTelephonyCallback(getMainExecutor(), callbackListener);

        } else {

            // Android 11 وأقل

            tm.listen(new PhoneStateListener() {

                @Override

                public void onCallStateChanged(int state, String phoneNumber) {

                    if (state == TelephonyManager.CALL_STATE_RINGING ||

                            state == TelephonyManager.CALL_STATE_OFFHOOK) {

                        if (player != null && player.isPlaying()) {

                            player.stop();

                            stopService(new Intent(ThikrMediaPlayerService.this,

                                    com.alaaeltaweel.thikrallah.Notification.ChatHeadService.class));

                            // ✅ من غير السطر ده، شاشة الأذان كانت مالهاش خبر إن الأذان اتوقف بسبب مكالمة فبتفضل مفتوحة
                            sendBroadcast(new Intent("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"));

                            stopSelf();

                        }

                    }

                }

            }, PhoneStateListener.LISTEN_CALL_STATE);

        }

    }



    private void initNotification() {

        // ✅ لو اللي شغال دلوقتي أذان، الإشعار وقت الضغط عليه يرجعنا لشاشة الأذان نفسها
        // مباشرة - بكده الإشعار ده بقى هو نفسه وسيلة "الرجوع للأذان"، مش محتاجين إشعار منفصل
        boolean isCurrentlyAthan = this.getThikrType() != null && this.getThikrType().contains(MainActivity.DATA_TYPE_ATHAN);

        Intent resultIntent;
        if (isCurrentlyAthan) {
            resultIntent = new Intent(this, com.alaaeltaweel.thikrallah.Notification.AthanScreenActivity.class);
            resultIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", this.getThikrType());
            resultIntent.putExtra("isResume", true); // ✅ الأذان شغال بالفعل - متشغلوش تاني من الأول
        } else {
            resultIntent = new Intent(this, MainActivity.class);
            resultIntent.putExtra("FromNotification", true);
            resultIntent.putExtra("DataType", this.getThikrType());
        }

        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent launchAppPendingIntent = PendingIntent.getActivity(this,

                0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String NOTIFICATION_CHANNEL_ID = "ThikrMediaPlayerService";

            String channelName = this.getResources().getString(R.string.remember_notification);

            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);

            chan.setSound(null, new AudioAttributes.Builder()

                    .setUsage(this.getStreamAudioAttributes())

                    .build());

            chan.setLightColor(Color.BLUE);

            chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            assert manager != null;

            manager.createNotificationChannel(chan);

            notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);

        } else {

            notificationBuilder = new NotificationCompat.Builder(this);

        }



        notificationBuilder

                .setSmallIcon(R.drawable.ic_launcher)

                .setAutoCancel(true)

                .setContentTitle(getString(R.string.my_app_name))

                .setPriority(Notification.PRIORITY_MAX)

                .setContentText(getThikrTypeString(this.getThikrType()))

                .setContentIntent(launchAppPendingIntent);

        notificationBuilder = setVisibilityPublic(notificationBuilder);

        notificationBuilder = addAction(notificationBuilder, "pause", R.drawable.ic_media_pause);

        notificationBuilder = addAction(notificationBuilder, "stop", R.drawable.ic_media_stop);

        this.SetMediaMetadata();

        notificationBuilder = this.setMediaStyle(notificationBuilder, new MediaStyle()

                .setShowActionsInCompactView(0, 1)

                .setMediaSession(mediaSession.getSessionToken()));



        mediaSession.setActive(true);

        Timber.d("starting thikrmediaplayerservice notification on foreground from initNotification");

        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        Timber.d("Finished starting thikrmediaplayerservice notification on foreground from initNotification");

        updateActions();

    }



    private NotificationCompat.Builder setVisibilityPublic(NotificationCompat.Builder inotificationBuilder) {

        inotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        return inotificationBuilder;

    }



    private NotificationCompat.Builder setMediaStyle(NotificationCompat.Builder builder, MediaStyle mediaStyle) {

        if (android.os.Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1

                && (Build.MANUFACTURER.toLowerCase(Locale.ENGLISH).contains("huawei")

                || Build.MANUFACTURER.toLowerCase(Locale.ENGLISH).contains("samsung"))) {

            return builder;

        } else {

            return builder.setStyle(mediaStyle);

        }

    }



    private void SetMediaMetadata() {

        MediaMetadataCompat.Builder builder;

        builder = new Builder();

        mediaSession.setMetadata(builder.build());

    }



    @SuppressLint("RestrictedApi")

    private void updateActions() {

        if (notificationBuilder != null) {

            notificationBuilder.mActions.clear();

            this.SetMediaMetadata();



            if (this.isPlaying()) {

                Timber.d("show pause & stop");

                notificationBuilder = addAction(notificationBuilder, "pause", R.drawable.ic_media_pause);

                notificationBuilder = addAction(notificationBuilder, "stop", R.drawable.ic_media_stop);

                notificationBuilder = this.setMediaStyle(notificationBuilder, new MediaStyle()

                        .setShowActionsInCompactView(0, 1)

                        .setMediaSession(mediaSession.getSessionToken()));

            } else {

                Timber.d("show play");

                notificationBuilder = addAction(notificationBuilder, "play", R.drawable.ic_media_play);

                notificationBuilder = addAction(notificationBuilder, "stop", R.drawable.ic_media_stop);

                notificationBuilder = this.setMediaStyle(notificationBuilder, new MediaStyle()

                        .setShowActionsInCompactView(0)

                        .setMediaSession(mediaSession.getSessionToken()));

            }

            mediaSession.setActive(true);

            startForeground(NOTIFICATION_ID, notificationBuilder.build());

        }

    }



    private NotificationCompat.Builder addAction(NotificationCompat.Builder builder, String label, int icon) {

        Intent intent = new Intent(label).setClass(this.getApplicationContext(), ThikrMediaBroadcastReciever.class);

        intent.putExtras(callingintent.getExtras());

        PendingIntent RecieverPendingIntent = PendingIntent.getBroadcast(this, 1,

                intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        return builder.addAction(new Action(icon, label, RecieverPendingIntent));

    }



    Intent callingintent;



    @Override

    public int onStartCommand(Intent intent, int flags, int startId) {

        String incomingDataType = intent.getExtras().getString("com.alaaeltaweel.thikrallah.datatype", null);

        Timber.d("ThikrMediaPlayerService onStartCommand");



        callingintent = intent;

        Bundle data = intent.getExtras();

        mcontext = this.getApplicationContext();

        this.isUserAction = data.getBoolean("isUserAction", false);

        int action = data.getInt("ACTION", -1);



        Timber.d("action %s", action);



        // ✅ معالجة MEDIA_PLAYER_STOP قبل أي حاجة تانية

        if (action == MEDIA_PLAYER_STOP) {

            Timber.d("MEDIA_PLAYER_STOP called - stopping athan");

            boolean wasActuallyPlaying = player != null && player.isPlaying(); // ✅ نلتقط الحالة قبل أي إيقاف

            if (player != null) {

                // ✅ نفصل مستمع الاكتمال الأول قبل الإيقاف - عشان أي حدث "اكتمال" متأخر
                // يكون لسه في الطابور ميشغلش الدعاء تاني من مساره الطبيعي بعد ما شغلناه إحنا يدويًا تحت
                try { player.setOnCompletionListener(null); } catch (Exception ignored) {}

                if (player.isPlaying()) {

                    player.stop();

                }

                player.release();

                player = null;

            }

            // ✅ إرسال broadcast لـ AthanScreenActivity/النافذة العائمة - بس لو ده أذان حقيقي فعلاً وكان لسه شغال فعليًا
            // (عشان الشاشة تقفل نفسها لو الإيقاف جه من مصدر تاني غيرها، زي زرار الإشعار)
            boolean duaWillPlay1 = false;
            if (wasActuallyPlaying && incomingDataType != null && incomingDataType.contains(MainActivity.DATA_TYPE_ATHAN)) {
                sendBroadcast(new Intent("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"));
                duaWillPlay1 = com.alaaeltaweel.thikrallah.Notification.DuaPlayerHelper.playDuaAfterAthan(getApplicationContext()); // على طول كلمه - تشغيل الدعاء لما المستخدم يوقف الأذان يدويا
            }

            if (!duaWillPlay1) {
                this.stopForeground(true);
                if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق
                this.stopSelf();
            }

            return Service.START_NOT_STICKY;

        }



        if (intent.getExtras().getString("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_DAY_THIKR).equalsIgnoreCase(MainActivity.DATA_TYPE_GENERAL_THIKR) && this.isPlaying()) {

            this.updateAllAlarms();

            if (action == MEDIA_PLAYER_RESET) {

                Timber.d("reset called");

                this.resetPlayer();

                this.stopForeground(true);
                if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق

                this.stopSelf();

            }

            return Service.START_NOT_STICKY;

        }

        // ✅ حماية إضافية أقوى من التكرار - بتشيك على التوقيت مش بس isPlaying()
        if (incomingDataType != null && incomingDataType.equalsIgnoreCase(MainActivity.DATA_TYPE_GENERAL_THIKR)) {
            long nowMsGeneral = System.currentTimeMillis();
            if (nowMsGeneral - lastGeneralThikrPlayStartTime < 5000) {
                Timber.d("General thikr play request too close to last one, skipping duplicate");
                return Service.START_NOT_STICKY;
            }
            lastGeneralThikrPlayStartTime = nowMsGeneral;
        }

        // ✅ نفس الحماية للأذان - منع تشغيله فوق نفسه
        if (incomingDataType != null && incomingDataType.contains(MainActivity.DATA_TYPE_ATHAN) && !this.isUserAction) { // متطبقش على تجربة الصوت اليدوية من الإعدادات
            long nowMsAthan = System.currentTimeMillis();
            if (nowMsAthan - lastAthanPlayStartTime < 2000) {
                Timber.d("Athan play request too close to last one, skipping duplicate");
                return Service.START_NOT_STICKY;
            }
            lastAthanPlayStartTime = nowMsAthan;
        }
        this.setThikrType(incomingDataType);
        Timber.d("initNotification called");

        initNotification();

        Timber.d("initNotification finished");

        if (getThikrType() == null) {

            Timber.d("thikrtype is null... why?");

            this.updateAllAlarms();

            this.stopForeground(true);
            if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق

            this.stopSelf();

            return Service.START_NOT_STICKY;

        }

        Bundle bundle = new Bundle();

        bundle.putString("thikrtype", this.getThikrType());

        if (this.getThikrType().equalsIgnoreCase(MainActivity.DATA_TYPE_GENERAL_THIKR)) {

            this.updateAllAlarms();

            if ((am.getRingerMode() == AudioManager.RINGER_MODE_SILENT || am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE)) {

                // ✅ تصليح: الشرط القديم كان بيتحقق من DATA_TYPE_ATHAN جوه فرع بيشتغل بس لو DATA_TYPE_GENERAL_THIKR،
                // يعني الشرط الداخلي مستحيل يتحقق - فالخدمة كانت بترجع من غير ما توقف نفسها ولا تقفل الإشعار
                if (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {

                    vibrate();

                    Timber.d("ringer mode vibrate. now vibrating");

                }

                Timber.d("stopping self");

                this.stopForeground(true);
                if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق

                this.stopSelf();

                return Service.START_NOT_STICKY;

            }

        }

        if (this.getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

            this.updateAllAlarms();

        }

        Timber.d("onStartCommand called%s", intent.getExtras().toString());



        switch (action) {

            case MEDIA_PLAYER_PAUSE:

                Timber.d("pause called");

                this.pausePlayer();

                updateActions();

                break;

            case MEDIA_PLAYER_INNCREMENT:

                Timber.d("increment called");

                int increment = intent.getExtras().getInt("INCREMENT", 1);

                this.setCurrentPlaying(this.getCurrentPlaying() + increment);

                currentThikrCounter = 0;

                this.playAll();

                updateActions();

                break;

            case MEDIA_PLAYER_CHANGE_VOLUME:

                Timber.d("MEDIA_PLAYER_CHANGE_VOLUME called");

                this.setVolume();

                break;

            case MEDIA_PLAYER_MUTE_BY_FLIP:

                Timber.d("MEDIA_PLAYER_MUTE_BY_FLIP called - muting athan sound only");

                isMutedByFlip = true;
                lastAthanWasMuted = true;
                if (player != null) {
                    try { player.setVolume(0f, 0f); } catch (Exception ignored) {}
                }

                break;

            case MEDIA_PLAYER_UNMUTE_BY_FLIP:

                Timber.d("MEDIA_PLAYER_UNMUTE_BY_FLIP called - restoring athan sound");

                isMutedByFlip = false;
                lastAthanWasMuted = false;
                if (player != null) {
                    try { player.setVolume(1f, 1f); } catch (Exception ignored) {}
                }

                break;

            case MEDIA_PLAYER_DUA_STARTED:

                Timber.d("MEDIA_PLAYER_DUA_STARTED - keeping service alive in foreground while dua plays");

                try {
                    android.app.Notification duaNotif = com.alaaeltaweel.thikrallah.Notification.DuaPlayerHelper.buildDuaNotification(this);
                    if (duaNotif != null) {
                        startForeground(9911, duaNotif);
                    }
                } catch (Exception e) {
                    Timber.tag(TAG).e(e, "Failed to start foreground for dua");
                }

                return Service.START_NOT_STICKY;

            case MEDIA_PLAYER_DUA_ENDED:

                Timber.d("MEDIA_PLAYER_DUA_ENDED - dua finished, service can stop now");

                this.stopForeground(true);
                if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق
                this.stopSelf();

                return Service.START_NOT_STICKY;

            case MEDIA_PLAYER_RESET:

                Timber.d("reset called stopping self");

                // ✅ نلتقط الحالة قبل الإيقاف - عشان نعرف نشغل الدعاء لو كان أذان شغال فعلاً
                // (زرار الإيقاف في الإشعار كان بيوقف من غير ما يشغل الدعاء خالص - بقى زي زرار الإيقاف في الشاشة)
                boolean wasAthanPlayingBeforeReset = player != null && player.isPlaying()
                        && getThikrType() != null && getThikrType().contains(MainActivity.DATA_TYPE_ATHAN);

                this.resetPlayer();

                boolean duaWillPlayFromReset = false;
                if (wasAthanPlayingBeforeReset) {
                    // ✅ نبعت إشارة "الأذان خلص" عشان الشاشة/النافذة العائمة تقفل نفسها -
                    // ده كان ناقص خالص، فالشاشة كانت فاضلة فاتحة لو وقفت من زرار الإشعار
                    sendBroadcast(new Intent("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"));
                    duaWillPlayFromReset = com.alaaeltaweel.thikrallah.Notification.DuaPlayerHelper.playDuaAfterAthan(getApplicationContext());
                }

                if (!duaWillPlayFromReset) {
                    this.stopForeground(true);
                    if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق
                    this.stopSelf();
                }

                break;

            case MEDIA_PLAYER_PLAYALL:

                Timber.d("playall called");

                currentThikrCounter = 0;

                this.playAll();

                updateActions();

                break;

            case MEDIA_PLAYER_ISPLAYING:

                this.isPlaying();

                break;

            case MEDIA_PLAYER_PLAY:

                int file = -1;

                filepath = "null";

                file = data.getInt("FILE");

                filepath = data.getString("FILE_PATH");

                String URI_string = data.getString("URI");

                if (URI_string != null && !URI_string.equals("null")) {

                    uri = Uri.parse(data.getString("URI"));

                    Timber.d("URI passed is " + uri + " file path is " + filepath);

                } else {

                    if (filepath != null && this.exists(this.getApplicationContext(), Uri.parse(filepath))) {

                        uri = Uri.parse(filepath);

                        Timber.d("URI passed is " + uri + " file path is " + filepath);

                    } else {

                        uri = null;

                        Timber.d("URI passed is null file path is %s", filepath);

                    }

                }

                Timber.d("play " + file + " called");

                currentThikrCounter = 0;

                this.play(file);

                updateActions();

                break;

            case MEDIA_PLAYER_RESUME:

                this.play();

                updateActions();

                break;

        }

        return Service.START_NOT_STICKY;

    }



    public int getCurrentPlaying() {

        return currentPlaying;

    }



    public void setCurrentPlaying(int icurrentPlaying) {

        currentPlaying = icurrentPlaying;

        sendMessageToUI(MSG_CURRENT_PLAYING, currentPlaying);

    }



    public int getAudioFocusRequestType() {

        if (this.getThikrType().equalsIgnoreCase(MainActivity.DATA_TYPE_GENERAL_THIKR)) {

            return AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
        }

        return AudioManager.AUDIOFOCUS_GAIN;

    }



    private int getStreamType() {

        if (this.getThikrType().equalsIgnoreCase(MainActivity.DATA_TYPE_GENERAL_THIKR)) {

            return AudioManager.STREAM_NOTIFICATION;

        } else if (this.getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

            return AudioManager.STREAM_NOTIFICATION;

        } else {

            return AudioManager.STREAM_MUSIC;

        }

    }



    private int getStreamAudioAttributes() {

        if (this.getThikrType() == null) {

            return AudioAttributes.USAGE_NOTIFICATION;

        }

        if (this.getThikrType().equalsIgnoreCase(MainActivity.DATA_TYPE_GENERAL_THIKR)) {

            return AudioAttributes.USAGE_NOTIFICATION;

        } else if (this.getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

            return AudioAttributes.USAGE_NOTIFICATION;

        } else {

            return AudioAttributes.USAGE_MEDIA;

        }

    }



    public void play() {

        player.setOnCompletionListener(this);

        int ret = requestAudioFocus();

        if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

            Timber.d("audiofocus request granted");

            startPlayerIfAllowed();

            setVolume();

        } else {

            Timber.d("audiofocus request denied");

        }

        updateActions();

    }



    public void play(int fileNumber) {

        final boolean isUserActionForThisPlay = this.isUserAction; // ✅ نثبت قيمة isUserAction وقت بداية التشغيل عشان نستخدمها صح لما الصوت يخلص


        int fadeDuration = 0;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());

        boolean isGradual = sharedPrefs.getBoolean("gradual_volume", true);

        if (getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

            // ✅ أذان جديد بيبدأ - نصفّر حالة الكتم القديمة (لو فاضلة من أذان سابق)
            lastAthanWasMuted = false;

            // ✅ أذان جديد فعلي بيبدأ - نسمح للدعاء بعده يشتغل تاني (نصفّر القفل القديم لو فاضل من أذان قبل كده)
            com.alaaeltaweel.thikrallah.Notification.DuaPlayerHelper.resetGuardForNewAthan();

            // ✅ منتدخلش في مستوى الصوت خالص - يشتغل بالظبط على المستوى اللي المستخدم حاطه،
            // حتى لو صفر. اختياره هو الأساس.

            if (isGradual) {

                fadeDuration = 10000;

            }

        }



        this.initMediaPlayer();

        // ✅ تسجيل حساس القلب/زرار الصوت لازم يحصل بعد initMediaPlayer() مش قبلها -
        // initMediaPlayer() بينادي resetPlayer() لو لقى player قديم لسه موجود، وresetPlayer()
        // بيلغي تسجيل الحساس/الـ receiver على طول. لو سجلناهم قبل initMediaPlayer()، كانوا
        // بيتلغوا فورًا قبل ما المستخدم يقدر يقلب الهاتف أو يدوس زرار الصوت أصلاً - وده كان
        // سبب إن حساس القلب مش بيشتغل خالص
        if (getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

            // ✅ تفعيل حساس القلب هنا (مش في الشاشة) عشان يشتغل حتى لو الشاشة مقفولة أو التليفون في الجيب
            boolean lockOnFlip = sharedPrefs.getBoolean("lock_athan_on_flip", false);
            if (lockOnFlip) {
                isMutedByFlipService = false;
                isMutedByFlip = false;
                flipSensorManager = (android.hardware.SensorManager) getSystemService(Context.SENSOR_SERVICE);
                if (flipSensorManager != null) {
                    flipAccelerometer = flipSensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
                    if (flipAccelerometer != null) {
                        boolean registered = flipSensorManager.registerListener(this, flipAccelerometer, android.hardware.SensorManager.SENSOR_DELAY_NORMAL);
                        Timber.d("flipSensorManager registerListener result: %s", registered);
                    } else {
                        Timber.e("flipAccelerometer sensor is NULL on this device");
                    }
                } else {
                    Timber.e("flipSensorManager service is NULL");
                }
            }

            // ✅ تفعيل مراقبة زرار الصوت هنا (مش في الشاشة) عشان يشتغل حتى لو شاشة الأذان مقفولة
            boolean lockOnVolume = sharedPrefs.getBoolean("lock_athan_on_volume_buttons", false);
            if (lockOnVolume) {
                try {
                    volumeButtonReceiver = new VolumeButtonReceiver();
                    android.content.IntentFilter volumeFilter =
                            new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION");
                    // ✅ من أندرويد 13 (API 33) لازم نحدد صراحة إنه مش exported، وإلا التسجيل ممكن يفشل بصمت
                    if (Build.VERSION.SDK_INT >= 33) {
                        registerReceiver(volumeButtonReceiver, volumeFilter, Context.RECEIVER_NOT_EXPORTED);
                    } else {
                        registerReceiver(volumeButtonReceiver, volumeFilter);
                    }
                    Timber.d("volumeButtonReceiver registered successfully");
                } catch (Exception e) {
                    Timber.e(e, "FAILED to register volumeButtonReceiver");
                }
            }

        }

        setCurrentPlaying(fileNumber);



        // ✅ عند انتهاء الأذان تلقائياً — إرسال broadcast لإغلاق AthanScreenActivity

        player.setOnCompletionListener(mp -> {

            mp.reset();

            Timber.d("athan completed - sending broadcast to close AthanScreenActivity");

            Intent broadcastIntent = new Intent("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE");

            sendBroadcast(broadcastIntent);
            boolean duaWillPlay2 = false;
            if (getThikrType() != null && getThikrType().contains(MainActivity.DATA_TYPE_ATHAN) && !isUserActionForThisPlay) { duaWillPlay2 = com.alaaeltaweel.thikrallah.Notification.DuaPlayerHelper.playDuaAfterAthan(getApplicationContext()); } // متشغلش الدعاء لو ده كان مجرد تجربة صوت في الإعدادات مش أذان حقيقي // تشغيل الدعاء لو ده أذان بس


            resetPlayer();

            if (!duaWillPlay2) {
                stopForeground(true);
                if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق
                stopSelf();
            }

        });



        try {

            if (fileNumber != -1) {

                Timber.d("file number is %s", fileNumber);

                AssetFileDescriptor afd = this.getApplicationContext().getAssets().openFd(this.getMediaFolderName() + "/" + fileNumber + ".mp3");

                Timber.d("file path  is " + this.getMediaFolderName() + "/" + fileNumber + ".mp3");

                player.reset();

                player.setAudioStreamType(getStreamType());

                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

                player.prepare();

                try { afd.close(); } catch (IOException ignored) {} // ✅ تسريب file descriptor - الـ player بياخد نسخته الخاصة بعد setDataSource



                int ret = requestAudioFocus();

                if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

                    Timber.d("audio focus request granted.");

                    startPlayerIfAllowed();

                    updateActions();

                    if (fadeDuration > 0 && getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

                        final Timer timer = new Timer(true);

                        TimerTask timerTask = new TimerTask() {

                            @Override

                            public void run() {

                                if (player == null) {

                                    timer.cancel();

                                    timer.purge();

                                } else {

                                    incrementVolume();

                                }

                                if (iVolume == INT_VOLUME_MAX) {

                                    timer.cancel();

                                    timer.purge();

                                }

                            }

                        };

                        int delay = fadeDuration / INT_VOLUME_MAX;

                        if (delay == 0) delay = 1;

                        timer.schedule(timerTask, delay, delay);

                    } else {

                        this.setVolume();

                    }

                } else {

                    Timber.d("audio focus request denied.");

                }

            } else {

                FileDescriptor afd;

                FileInputStream fis;

                if (uri != null) {

                    try {

                        fis = new FileInputStream(this.getApplicationContext().getContentResolver().openFileDescriptor(uri, "r").getFileDescriptor());

                        Log.d(TAG, "fis defined by uri" + uri.toString());

                    } catch (java.lang.SecurityException e) {

                        sharedPrefs.edit().putBoolean("isMediaPermissionNeeded", true).commit();

                        Toast.makeText(this, R.string.need_audio_media_permission_message, Toast.LENGTH_LONG).show();

                        this.stopSelf();

                        return;

                    }

                } else {

                    fis = new FileInputStream(this.filepath);

                    Log.d(TAG, "fis defined by filepath" + this.filepath);

                }



                afd = fis.getFD();

                player.reset();

                player.setAudioStreamType(getStreamType());

                player.setDataSource(afd);

                player.prepare();

                try { fis.close(); } catch (IOException ignored) {} // ✅ تسريب file descriptor - الـ player بياخد نسخته الخاصة بعد setDataSource

                Log.d(TAG, "player prepared");

                int ret = requestAudioFocus();

                Log.d(TAG, "requestAudioFocus returned " + ret);

                if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

                    Log.d(TAG, "calling  startPlayerIfAllowed ");

                    startPlayerIfAllowed();

                    setVolume();

                }

            }

        } catch (IOException e) {

            Timber.e("%s", e.getMessage());

            e.printStackTrace();

        }

        updateActions();

    }



    public boolean exists(Context context, Uri uri) {

        return context.getContentResolver().getType(uri) != null;

    }



    private String[] getThikrArray() {

        String[] numbers_text = null;

        if (this.getThikrType().equals(MainActivity.DATA_TYPE_DAY_THIKR)) {

            numbers_text = getResources().getStringArray(R.array.MorningThikr);

        }

        if (this.getThikrType().equals(MainActivity.DATA_TYPE_NIGHT_THIKR)) {

            numbers_text = getResources().getStringArray(R.array.NightThikr);

        }

        if (this.getThikrType().equals(MainActivity.DATA_TYPE_GENERAL_THIKR)) {

            numbers_text = getResources().getStringArray(R.array.GeneralThikr);

        }

        if (this.getThikrType().contains(MainActivity.DATA_TYPE_QURAN)) {

            int surat = Integer.parseInt(this.getThikrType().split("/", 3)[1]);

            int count = this.getResources().getIntArray(R.array.verses_count)[surat];

            numbers_text = new String[count];

            for (int i = 0; i < count; i++) {

                numbers_text[i] = String.valueOf(i + 1);

            }

        }

        return numbers_text;

    }



    public void playAll() {

        if (!isPaused) {

            if (this.getCurrentPlaying() < 1) {

                setCurrentPlaying(1);

            }

            AssetFileDescriptor afd;

            try {

                Timber.d("current playing is %s", getCurrentPlaying());

                Timber.d("thikrtype is %s", getThikrType());

                afd = this.getApplicationContext().getAssets().openFd(getThikrType() + "/" + this.getCurrentPlaying() + ".mp3");

                Timber.d("now will call initmediaplayer");

                this.initMediaPlayer();

                Timber.d("finished initmediaplayer");

                player.setAudioStreamType(getStreamType());

                Timber.d("audio stream type set");

                player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

                Timber.d("datasource set");

                player.prepare();

                try { afd.close(); } catch (IOException ignored) {} // ✅ تسريب file descriptor - الـ player بياخد نسخته الخاصة بعد setDataSource

                Timber.d("current playing was prepared successfully %s", getCurrentPlaying());

            } catch (IOException e) {

                if (this.getCurrentPlaying() < 1) {

                    setCurrentPlaying(1);

                }

                if (this.getCurrentPlaying() > this.getThikrArray().length) {

                    setCurrentPlaying(this.getThikrArray().length);

                }

            }

        }

        isPaused = false;

        player.setOnCompletionListener(this);



        int ret = requestAudioFocus();

        Timber.d("audiofocus request return code is %s", ret);

        if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

            Timber.d("audiofocus request granted =%s", AudioManager.AUDIOFOCUS_REQUEST_GRANTED);

            startPlayerIfAllowed();

            setVolume();

        }

    }



    private int requestAudioFocus() {

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {

            AudioAttributes mPlaybackAttributes = new AudioAttributes.Builder()

                    .setUsage(this.getStreamAudioAttributes())

                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)

                    .build();

            AudioFocusRequest mFocusRequest = new AudioFocusRequest.Builder(this.getAudioFocusRequestType())

                    .setAcceptsDelayedFocusGain(true)

                    .setOnAudioFocusChangeListener(this)

                    .setAudioAttributes(mPlaybackAttributes)

                    .build();

            return am.requestAudioFocus(mFocusRequest);

        } else {

            return am.requestAudioFocus(this,

                    this.getStreamType(),

                    getAudioFocusRequestType());

        }

    }



    @Override

    public void onDestroy() {

        Timber.d("ondestroy called");

        // ✅ تنظيف حساس القلب لو الخدمة اتقفلت خالص
        if (flipSensorManager != null) {
            flipSensorManager.unregisterListener(this);
        }
        // ✅ تنظيف مراقبة زرار الصوت لو الخدمة اتقفلت خالص
        if (volumeButtonReceiver != null) {
            try { unregisterReceiver(volumeButtonReceiver); } catch (Exception ignored) {}
            volumeButtonReceiver = null;
        }

        if (mediaSession != null) {

            mediaSession.release();

        }

        this.stopForeground(true);
        if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق

        if (player != null) {

            player.release();

            player = null;

        }

        am.abandonAudioFocus(this);

        this.sendMessageToUI(MSG_CURRENT_PLAYING, -99);

        this.sendMessageToUI(MSG_UNBIND, MSG_UNBIND);

        this.stopSelf();

        super.onDestroy();

    }



    public int getCurrentThikrRepeat() {

        int repeat = 1;

        if (this.getThikrType().contains(MainActivity.DATA_TYPE_QURAN)) {

            return repeat;

        }

        String currentThikr = "";

        try {

            currentThikr = this.getThikrArray()[this.getCurrentPlaying() - 1];

        } catch (IndexOutOfBoundsException e) {

            Timber.d("'index out of bound");

        }

        Pattern pattern = Pattern.compile("[\\d]+");

        Matcher matcher = pattern.matcher(currentThikr);

        Timber.d("current thikr is: %s", currentThikr);

        if (matcher.find()) {

            repeat = Integer.parseInt(matcher.group(0));

            Timber.d("repeat number found%s", repeat);

        } else {

            repeat = 1;

            Timber.d("no repeat number found%s", repeat);

        }

        return repeat;

    }



    // ✅ كتم صوت الأذان مرة واحدة بس عند قلب الهاتف (مفيش إرجاع تلقائي)
    @Override
    public void onSensorChanged(android.hardware.SensorEvent event) {
        if (isMutedByFlipService) return; // اتكتم قبل كده، متعملش حاجة تاني
        if (event.sensor.getType() == android.hardware.Sensor.TYPE_ACCELEROMETER) {
            float z = event.values[2];
            boolean isFaceDown = z < -9.0f;
            if (isFaceDown) {
                Timber.d("Service flip detected (z=%s), player=%s", z, (player == null ? "null" : "not-null"));
            }
            if (isFaceDown && player != null) {
                isMutedByFlipService = true;
                isMutedByFlip = true;
                lastAthanWasMuted = true;
                try { player.setVolume(0f, 0f); } catch (Exception ignored) {}
                if (flipSensorManager != null) {
                    flipSensorManager.unregisterListener(this); // اتكتم، خلاص متحتاجش تراقب تاني
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {
        // مش محتاجين نعمل حاجة هنا
    }

    // ✅ كتم صوت الأذان مرة واحدة بس عند الضغط على زرار الصوت (مفيش إرجاع تلقائي) - شغالة حتى لو شاشة الأذان مقفولة
    private class VolumeButtonReceiver extends android.content.BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
            int newVal = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
            int prevVal = intent.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1);
            Timber.d("VolumeButtonReceiver fired - streamType=%s, prev=%s, new=%s, isMutedByFlip=%s, player=%s", streamType, prevVal, newVal, isMutedByFlip, (player == null ? "null" : "not-null"));
            if (isMutedByFlip) return; // اتكتم قبل كده، متعملش حاجة تاني
            if (streamType != AudioManager.STREAM_MUSIC) return;
            if (player == null) return;
            // ✅ ضغطة زرار الصوت الحقيقية بتغيّر المستوى بخطوة واحدة (+1/-1) بالظبط.
            // أي تغيير تاني (تطبيق تاني ضبط الصوت برمجيًا، أو النظام غيّره لسبب آخر) بيتجاهل
            if (prevVal == -1 || newVal == -1 || Math.abs(newVal - prevVal) != 1) {
                Timber.d("Ignoring volume change - not a genuine single button press");
                return;
            }
            isMutedByFlipService = true;
            isMutedByFlip = true;
            lastAthanWasMuted = true;
            try { player.setVolume(0f, 0f); } catch (Exception ignored) {}
            if (volumeButtonReceiver != null) {
                try { unregisterReceiver(volumeButtonReceiver); } catch (Exception ignored) {} // اتكتم، خلاص متحتاجش تراقب تاني
                volumeButtonReceiver = null;
            }
        }
    }

    public void onCompletion(MediaPlayer mp) {

        mp.reset();

        Timber.d("oncomplete called");

        Timber.d("thikrtype is " + this.getThikrType() + " vs " + MainActivity.DATA_TYPE_GENERAL_THIKR);

        currentThikrCounter++;

        if (this.getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {
            // ✅ شغل الدعاء بعد الأذان - بس لو ده أذان حقيقي فعلاً
            Intent duaIntent = new Intent("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE");
            sendBroadcast(duaIntent);
            boolean duaWillPlay3 = com.alaaeltaweel.thikrallah.Notification.DuaPlayerHelper.playDuaAfterAthan(getApplicationContext()); // تشغيل الدعاء مستقل عن فتح الشاشة

            this.resetPlayer();
            if (!duaWillPlay3) {
                this.stopForeground(true);
                if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق
                this.stopSelf();
            }
            return;
        }

        if (this.getThikrType().equalsIgnoreCase(MainActivity.DATA_TYPE_GENERAL_THIKR) || this.getThikrType().contains(MainActivity.DATA_TYPE_QURAN)) {
            // ✅ تنظيف عادي فقط - من غير ما نطلق حدث "الأذان خلص"
            this.resetPlayer();
            this.stopForeground(true);
            if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق
            this.stopSelf();
            return;
        }

        if (this.getCurrentPlaying() >= getThikrArray().length && currentThikrCounter >= getCurrentThikrRepeat()) {

            setCurrentPlaying(1);

            currentThikrCounter = 0;

            this.resetPlayer();

            this.stopForeground(true);
            if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق

            this.stopSelf();

        } else {

            if (currentThikrCounter >= getCurrentThikrRepeat()) {

                currentThikrCounter = 0;

                setCurrentPlaying(this.getCurrentPlaying() + 1);

            } else {

                setCurrentPlaying(this.getCurrentPlaying());

            }

            playAll();

        }

    }



    private String getThikrType() {

        return ThikrType;

    }



    private String getMediaFolderName() {

        if (getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

            return ThikrType = MainActivity.DATA_TYPE_ATHAN;

        }

        return getThikrType();

    }



    private void setThikrType(String iThikrType) {

        if (iThikrType != null) {

            ThikrType = iThikrType;

        }

    }



    private String getThikrTypeString(String thikTypeConstant) {

        if (thikTypeConstant != null) {

            switch (thikTypeConstant) {

                case MainActivity.DATA_TYPE_ATHAN1:

                    return this.getString(R.string.prayer1);

                case MainActivity.DATA_TYPE_ATHAN2:

                    return this.getString(R.string.prayer2);

                case MainActivity.DATA_TYPE_ATHAN3:

                    return this.getString(R.string.prayer3);

                case MainActivity.DATA_TYPE_ATHAN4:

                    return this.getString(R.string.prayer4);

                case MainActivity.DATA_TYPE_ATHAN5:

                    return this.getString(R.string.prayer5);

                case MainActivity.DATA_TYPE_DAY_THIKR:

                    return this.getString(R.string.morningThikr);

                case MainActivity.DATA_TYPE_NIGHT_THIKR:

                    return this.getString(R.string.nightThikr);

                case MainActivity.DATA_TYPE_QURAN_KAHF:

                    return this.getString(R.string.surat_alkahf);

                case MainActivity.DATA_TYPE_QURAN_MULK:

                    return this.getString(R.string.surat_almulk);

                default:

                    return this.getString(R.string.remember_notification);

            }

        } else {

            return this.getString(R.string.remember_notification);

        }

    }



    public void resetPlayer() {
    // ✅ إيقاف مراقبة حساس القلب لما الصوت يقف/يخلص
    if (flipSensorManager != null) {
        flipSensorManager.unregisterListener(this);
    }
    // ✅ إيقاف مراقبة زرار الصوت لما الصوت يقف/يخلص
    if (volumeButtonReceiver != null) {
        try { unregisterReceiver(volumeButtonReceiver); } catch (Exception ignored) {}
        volumeButtonReceiver = null;
    }
    if (this.player != null) {
        // ✅ نفصل مستمع الاكتمال قبل الإيقاف - عشان أي حدث "اكتمال" متأخر لسه في الطابور
        // ميشغلش الدعاء تاني من مساره الطبيعي (نفس إصلاح MEDIA_PLAYER_STOP بالظبط)
        try { this.player.setOnCompletionListener(null); } catch (Exception ignored) {}
        try {
            this.player.stop();
        } catch (Exception e) {
            Timber.tag(TAG).e(e, "resetPlayer: stop() failed, discarding player");
        }
        try {
            this.player.reset();
        } catch (Exception e) {
            Timber.tag(TAG).e(e, "resetPlayer: reset() failed, discarding player");
            try {
                this.player.release();
            } catch (Exception ignored) {}
            this.player = null;
        }
    }
}



    public boolean isPlaying() {

        boolean isPlaying = false;

        if (player != null) {

            try {

                isPlaying = this.player.isPlaying();

            } catch (Exception e) {

                Timber.e(e.getMessage());

            }

        }

        Timber.d("isPlaying returning %s", isPlaying);

        return isPlaying;

    }



    public void pausePlayer() {

        isPaused = true;

        if (this.isPlaying()) {

            this.player.pause();

            this.updateActions();

        } else {

            if (this.play_count == 0) {

                this.stopSelf();

            }

        }

    }



    @Override

    public void onAudioFocusChange(int focusChange) {

        switch (focusChange) {

            case AudioManager.AUDIOFOCUS_GAIN:

                Timber.d("gained focus");

                mediaSession.setActive(true);

                if (player == null) {

                    initMediaPlayer();

                } else if (!isPlaying()) {

                    startPlayerIfAllowed();

                }

                this.setVolume();

                break;

            case AudioManager.AUDIOFOCUS_LOSS:

                Timber.d("lost focus");

                mediaSession.setActive(false);

                if (isPlaying()) {

                    player.stop();

                }

                Timber.d("reseting player and releasing service");

                this.resetPlayer();

                this.stopForeground(true);
                if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} } // ✅ نقفل كارت التحكم من الشاشة المقفولة/المكالمة عشان ميفضلش عالق

                this.stopSelf();

                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:

                Timber.d("transient loss of  focus");

                // ✅ لو الأذان هو اللي كان شغال وقاطعته مكالمة (هاتف عادي أو مكالمة إنترنت)،
                // نكتفي بوقف-وقت-محدد، إكماله بعد دقايق من نهاية المكالمة مش منطقي،
                // وده كان سبب إن إشعار الأذان يفضل عالق لحد ما أذان جديد يجي يشيله
                if (getThikrType() != null && getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {
                    Timber.d("Athan interrupted by call - stopping fully instead of pause/resume");
                    if (isPlaying()) {
                        try { player.stop(); } catch (Exception ignored) {}
                    }
                    this.resetPlayer();
                    this.stopForeground(true);
                    if (mediaSession != null) { try { mediaSession.setActive(false); } catch (Exception ignored) {} }
                    // ✅ من غير السطر ده، شاشة الأذان كانت مالهاش خبر إن الأذان خلص فبتفضل مفتوحة
                    sendBroadcast(new Intent("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"));
                    this.stopSelf();
                } else {
                    if (isPlaying()) player.pause();
                }

                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:

                Timber.d("AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");

                if (isPlaying()) {

                    player.setVolume(0.1f, 0.1f);

                }

                break;

        }

        this.updateActions();

    }



    private void setVolume() {

        if (isMutedByFlip) return; // ✅ ما ترجعش الصوت لوحده (زي وقت استرجاع الـ audio focus) وإحنا مكتومين

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());

        boolean isGradual = sharedPrefs.getBoolean("gradual_volume", true);

        if (this.getThikrType().contains(MainActivity.DATA_TYPE_ATHAN)) {

            if (isGradual) {

                incrementVolume();

            } else {

                player.setVolume(1f, 1f);

            }

            return;

        }

        Timber.d("setVolume - thikr only");

        int volumeLevel = sharedPrefs.getInt("volume", 100);

        int maxVolume = 101;

        float volume = (float) (1 - Math.log(maxVolume - volumeLevel) / Math.log(maxVolume));

        player.setVolume(volume, volume);

    }



    private void startPlayerIfAllowed() {

        Timber.d("startPlayerIfAllowed called");

        int ret = requestAudioFocus();

        Timber.d("request audio focus return code is %s", ret);

        if (ret == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

            Timber.d("request audio focus granted");

            this.play_count++;

            sendMessageToUI(MSG_CURRENT_PLAYING, currentPlaying);

            // ✅ لو الشاشة نايمة (الجهاز كان في نوم عميق ومحدش صحصحه غيرنا)،
            // ناخد نص ثانية زيادة عشان نديله وقت "يفوق" كويس قبل ما نبدأ الصوت - ده بيمنع تقطيع أول كلمة
            PowerManager screenCheckPm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isScreenOff = screenCheckPm != null && !screenCheckPm.isInteractive();
            if (isScreenOff) {
                Timber.d("Screen is off - delaying playback start slightly to let device wake up fully");
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (player != null) {
                        try {
                            player.start();
                            Timber.d("player started after wake-up delay");
                        } catch (Exception e) {
                            Timber.e(e, "delayed player.start failed");
                        }
                    }
                }, 400);
            } else {

            player.start();

            Timber.d("player started");
            }

            this.updateActions();

        } else {

            Log.d(TAG, "audio focused request denied");

        }

    }



    private void vibrate() {

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        int dash = 500;

        int medium_gap = 500;

        long[] pattern = {0, dash, medium_gap, dash, medium_gap};

        v.vibrate(pattern, -1);

    }



    private void initMediaPlayer() {

        if (player != null) {

            Timber.d("initiMediaPlayer is called and player is not null");

            this.resetPlayer();

        }

        if (player == null) {

            Timber.d("initiMediaPlayer is called and player is null");

            player = new MediaPlayer();

            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

            am = (AudioManager) this.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);



            ComponentName receiver = new ComponentName("com.alaaeltaweel.thikrallah.Notification", ThikrMediaBroadcastReciever.class.getName());



            if (mediaSession != null) {

                mediaSession.release();

            }

            mediaSession = new MediaSessionCompat(this, "MEDIA_SESSION_THIKRALLAH", receiver, null);

            mController = new MediaControllerCompat(this, mediaSession);



            mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |

                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);



            mediaSession.setCallback(new MediaSessionCompat.Callback() {

                @Override

                public boolean onMediaButtonEvent(Intent mediaButtonEvent) {

                    Timber.d("onMediaButtonEvent");

                    return super.onMediaButtonEvent(mediaButtonEvent);

                }



                @Override

                public void onPlay() {

                    Timber.d("onPlay");

                    super.onPlay();

                }



                @Override

                public void onPause() {

                    Timber.d("onPause");

                    super.onPause();

                }



                @Override

                public void onSkipToNext() {

                    Timber.d("onSkipToNext");

                    super.onSkipToNext();

                }



                @Override

                public void onSkipToPrevious() {

                    Timber.d("onSkipToPrevious");

                    super.onSkipToPrevious();

                }



                @Override

                public void onSeekTo(long pos) {

                    super.onSeekTo(pos);

                }



                @Override

                public void onStop() {

                    Timber.d("onStop");

                    super.onStop();

                }

            });

            try {

                mediaSession.setActive(true);

            } catch (Exception e) {

                mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

                mediaSession.setActive(true);

            }

        }

    }



    private int iVolume;

    private final static int INT_VOLUME_MAX = 100;

    private final static int INT_VOLUME_MIN = 0;

    private final static float FLOAT_VOLUME_MAX = 1;

    private final static float FLOAT_VOLUME_MIN = 0;



    private void incrementVolume() {

        if (isMutedByFlip) return; // ✅ ما ترفعش الصوت وإحنا مكتومين بسبب قلب الهاتف/زرار الصوت

        Timber.d("incrementVolume called");

        iVolume = iVolume + 1;

        if (iVolume < INT_VOLUME_MIN)

            iVolume = INT_VOLUME_MIN;

        else if (iVolume > INT_VOLUME_MAX)

            iVolume = INT_VOLUME_MAX;



        float fVolume = 1 - ((float) Math.log(INT_VOLUME_MAX - iVolume) / (float) Math.log(INT_VOLUME_MAX));

        if (fVolume < FLOAT_VOLUME_MIN)

            fVolume = FLOAT_VOLUME_MIN;

        else if (fVolume > FLOAT_VOLUME_MAX)

            fVolume = FLOAT_VOLUME_MAX;



        if (player != null) {

            try {

                if (player.isPlaying()) {

                    player.setVolume(fVolume, fVolume);

                }

            } catch (IllegalStateException e) {

                e.printStackTrace();

                Timber.e("%s", e.getMessage());

            }

        }

    }



    @androidx.annotation.RequiresApi(api = Build.VERSION_CODES.S)

    private class MyCallStateCallback extends android.telephony.TelephonyCallback

            implements android.telephony.TelephonyCallback.CallStateListener {

        @Override

        public void onCallStateChanged(int state) {

            if (state == TelephonyManager.CALL_STATE_RINGING ||

                    state == TelephonyManager.CALL_STATE_OFFHOOK) {

                if (player != null && player.isPlaying()) {

                    player.stop();

                    stopService(new Intent(ThikrMediaPlayerService.this,

                            com.alaaeltaweel.thikrallah.Notification.ChatHeadService.class));

                    // ✅ من غير السطر ده، شاشة الأذان كانت مالهاش خبر إن الأذان اتوقف بسبب مكالمة فبتفضل مفتوحة
                    sendBroadcast(new Intent("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"));

                    ThikrMediaPlayerService.this.stopSelf();

                }

            }

        }

    }

}




