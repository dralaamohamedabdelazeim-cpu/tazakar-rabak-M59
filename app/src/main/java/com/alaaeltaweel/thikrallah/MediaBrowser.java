package com.alaaeltaweel.thikrallah;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MediaBrowser extends AppCompatActivity {
    private static final String TAG = "MediaBrowser";
    private static final int PERMISSION_REQUEST_CODE = 101;
    private List<String> myList;
    private ListView listView;
    private TextView pathTextView;
    private List<AudioModel> AllAudioFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.media_browser);

        listView = (ListView) findViewById(R.id.pathlist);
        pathTextView = (TextView) findViewById(R.id.path);
        myList = new ArrayList<>();

        // ✅ طلب الإذن أولاً قبل ما نقرأ الملفات
        checkPermissionAndLoadAudio();
    }

    private void checkPermissionAndLoadAudio() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // اندرويد 13+
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            // اندرويد 12 وأقل
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            loadAudioFiles();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, PERMISSION_REQUEST_CODE);
        }
    }

    private void loadAudioFiles() {
        pathTextView.setText("جاري تحميل الملفات...");
        // ✅ قراءة كل ملفات الصوت من الجهاز ممكن تاخد وقت لو المكتبة كبيرة - نعملها في خيط تاني
        // عشان منعملش تجميد (ANR) للشاشة
        new Thread(() -> {
            List<AudioModel> files = getAllAudioFromDevice(this.getApplicationContext());
            runOnUiThread(() -> {
                AllAudioFiles = files;
                myList.clear();
                for (int i = 0; i < AllAudioFiles.size(); i++) {
                    myList.add(AllAudioFiles.get(i).getaName());
                }
                pathTextView.setText("Select Audio File from below:");
                listView.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1, myList));

                listView.setOnItemClickListener((arg0, arg1, position, arg3) -> {
                    Intent data = new Intent();
                    data.putExtra("FILE", AllAudioFiles.get(position).getUri().toString());
                    setResult(RESULT_OK, data);
                    finish();
                });
            });
        }).start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadAudioFiles(); // ✅ تم منح الإذن، حمّل الملفات
            } else {
                finish(); // ❌ رفض الإذن، أغلق
            }
        }
    }

    public List<AudioModel> getAllAudioFromDevice(final Context context) {
        final List<AudioModel> tempAudioList = new ArrayList<>();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.AudioColumns.DATA,
                MediaStore.Audio.AudioColumns.ALBUM,
                MediaStore.Audio.ArtistColumns.ARTIST,
                MediaStore.Audio.Media._ID
        };
        Cursor c = context.getContentResolver().query(uri, projection, null, null, null);
        if (c != null) {
            while (c.moveToNext()) {
                AudioModel audioModel = new AudioModel();
                String path = c.getString(0);
                // ✅ بعض الملفات (خصوصًا من تخزين سحابي) ممكن ترجع مسار فاضي (null) - نتخطاها بهدوء
                if (path == null) continue;
                String album = c.getString(1);
                String artist = c.getString(2);
                long id = c.getLong(3);
                String name = path.substring(path.lastIndexOf("/") + 1);
                audioModel.setaName(name);
                audioModel.setaAlbum(album);
                audioModel.setaArtist(artist);
                audioModel.setaPath(path);
                audioModel.setId(id);
                Uri file_uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                audioModel.setUri(file_uri);
                tempAudioList.add(audioModel);
            }
            c.close();
        }
        return tempAudioList;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    private static class AudioModel {
        String aPath, aName, aAlbum, aArtist;
        Uri uri;
        long id;
        public Uri getUri() { return uri; }
        public long getId() { return id; }
        public void setUri(Uri uri) { this.uri = uri; }
        public void setId(long id) { this.id = id; }
        public String getaPath() { return aPath; }
        public void setaPath(String aPath) { this.aPath = aPath; }
        public String getaName() { return aName; }
        public void setaName(String aName) { this.aName = aName; }
        public String getaAlbum() { return aAlbum; }
        public void setaAlbum(String aAlbum) { this.aAlbum = aAlbum; }
        public String getaArtist() { return aArtist; }
        public void setaArtist(String aArtist) { this.aArtist = aArtist; }
    }
}
