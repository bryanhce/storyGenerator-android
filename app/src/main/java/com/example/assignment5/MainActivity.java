package com.example.assignment5;

import androidx.appcompat.app.AppCompatActivity;
import android.media.MediaPlayer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mediaPlayer = MediaPlayer.create(this, R.raw.home_music);
        playAudio();
    }

    private void playAudio() {
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }
    }

    @Override
    protected void onStop() {
        // Stop and release the MediaPlayer when the activity is stopped
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onStop();
    }

    public void launchPhotoTagger(View view) {
        Intent intent = new Intent(this, PhotoTagger.class);
        startActivity(intent);
    }

    public void launchSketchTagger(View view) {
        Intent intent = new Intent(this, SketchTagger.class);
        startActivity(intent);
    }

    public void launchStoryTeller(View view) {
        Intent intent = new Intent(this, StoryTeller.class);
        startActivity(intent);
    }
}