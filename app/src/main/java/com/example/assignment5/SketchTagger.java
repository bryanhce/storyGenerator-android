package com.example.assignment5;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SketchTagger extends Activity {

    Bitmap imageBitmap;
    private MyDrawingArea myDrawingAreaId;
    SQLiteDatabase db;
    ArrayList<Item> itemList;
    ItemListAdapter adapter;
    EditText editText;

    private final String API_KEY = "APIKEY";
    private MediaPlayer mediaPlayer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sketch_page);

        databaseInit();

        myDrawingAreaId = findViewById(R.id.myDrawingArea);

        itemList = new ArrayList<>();
        adapter = new ItemListAdapter(this, R.layout.list_item, itemList);

        createImageList();

        editText = (EditText)findViewById(R.id.sketchCreateTags);
        mediaPlayer = MediaPlayer.create(this, R.raw.save_sound);

        // used to delete specific rows in the database
//        db.execSQL("delete from sketches where ID IN (1, 2, 3)");
    }

    private void playAudio(int resourceId) {
        if (mediaPlayer != null) {
            mediaPlayer.reset(); // Reset the MediaPlayer before playing a new song
            mediaPlayer = MediaPlayer.create(this, resourceId);
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

    protected void databaseInit() {
        db = this.openOrCreateDatabase("mydb", Context.MODE_PRIVATE, null);
        db.execSQL("CREATE TABLE IF NOT EXISTS SKETCHES" +
                " (ID INTEGER PRIMARY KEY AUTOINCREMENT, PICTURE BLOB, DATE TEXT, TAGS TEXT)");
    }

    public void clearDrawing(View view) {
        myDrawingAreaId.clearPath();
    }

    public void saveDrawing(View view) {
        playAudio(R.raw.save_sound);
        // get current date and time in String
        Date currentDate = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy - ha HHmmss", Locale.US);
        String formattedDate = dateFormat.format(currentDate);

        // get tags typed in
        EditText editText = findViewById(R.id.sketchCreateTags);
        String tags = editText.getText().toString();

        //convert bitmap to byte
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] ba = stream.toByteArray();

        // insert into database
        ContentValues cv = new ContentValues();
        cv.put("PICTURE", ba);
        cv.put("DATE", formattedDate);
        cv.put("TAGS", tags);
        db.insert("SKETCHES", null, cv);

        // render new list
        String date = formattedDate.substring(0, formattedDate.lastIndexOf(" ")); // remove specific time
        itemList.add(new Item(imageBitmap, tags + "\n" + date));
        adapter.notifyDataSetChanged();
    }

    public void filterDrawing(View view) {
        playAudio(R.raw.filter_sound);
        // get filter tags
        EditText filterTags = findViewById(R.id.sketchFilterTags);
        String tags = filterTags.getText().toString();
        itemList = new ArrayList<>();

        // constructing dynamic sql query
        String sqlQuery = "SELECT PICTURE, DATE, TAGS FROM SKETCHES";
        if (!tags.equals("")) {
            // depending on how strict the filter function works
            // might need to remove the %% for exact word matching
            sqlQuery += " WHERE TAGS LIKE '%" + tags + "%'";
        }
        sqlQuery += " ORDER BY DATE DESC LIMIT 3";
        Cursor cursor = db.rawQuery(sqlQuery, null);
        int count = Math.min(cursor.getCount(), 3);
        int i;
        for (i = 0; i < count; i++) {
            cursor.moveToPosition(i);
            byte[] ba = cursor.getBlob(0);
            Bitmap b = BitmapFactory.decodeByteArray(ba, 0, ba.length);
            String longDate = cursor.getString(1); // date contains specific timing too
            String date = longDate.substring(0, longDate.lastIndexOf(" ")); // remove specific time
            String displayTags = cursor.getString(2);
            String supportingText = displayTags + "\n" + date;
            itemList.add(new Item(b, supportingText));
        }
        cursor.close();

        adapter = new ItemListAdapter(this, R.layout.list_item, itemList);
        ListView lv = findViewById(R.id.mylist);
        lv.setAdapter(adapter);
    }

    protected void createImageList() {
        // grab data from the database and add it to array
        Cursor cursor = db.rawQuery("SELECT PICTURE, DATE, TAGS FROM SKETCHES ORDER BY DATE DESC", null);
        for (int i = 0; i < cursor.getCount(); i++) {
            cursor.moveToPosition(i);
            byte[] ba = cursor.getBlob(0);
            Bitmap b = BitmapFactory.decodeByteArray(ba, 0, ba.length);
            String longDate = cursor.getString(1); // date contains specific timing too
            String date = longDate.substring(0, longDate.lastIndexOf(" ")); // remove specific time
            String tags = cursor.getString(2);
            String supportingText = tags + "\n" + date;
            itemList.add(new Item(b, supportingText));
        }
        cursor.close();

        ListView lv = findViewById(R.id.mylist);
        lv.setAdapter(adapter);
    }

    public void getTags(View view) {
        playAudio(R.raw.tag_sound);
        imageBitmap = myDrawingAreaId.getBitmap();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String label = getVisionLabels(imageBitmap);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            editText.setText(label);
                        }
                    });
                } catch (IOException e) {
                    Log.e("vision", e.toString());
                }
            }
        }).start();
    }

    public void returnToHomePage(View view) {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
    }

    private class ItemListAdapter extends ArrayAdapter<Item> {

        ItemListAdapter(Context context, int resource, ArrayList<Item> objects) {
            super(context, resource, objects);
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                        R.layout.list_item, parent, false);
            }
            Item currentItem = getItem(position);
            ImageView foodImage = convertView.findViewById(R.id.itemImage);
            TextView foodName = convertView.findViewById(R.id.itemName);
            foodImage.setImageBitmap(currentItem.imageBitmap);
            foodName.setText(currentItem.name);
            return convertView;
        }
    }

    // only used for new labels, past labels dont need to call this function
    private String getVisionLabels(Bitmap bitmap) throws IOException {
        //1. ENCODE image.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bout);
        Image myimage = new Image(); // this is google api class
        myimage.encodeContent(bout.toByteArray());

        //2. PREPARE AnnotateImageRequest
        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();
        annotateImageRequest.setImage(myimage);
        Feature f = new Feature();
        f.setType("LABEL_DETECTION");
        f.setMaxResults(5);
        List<Feature> lf = new ArrayList<Feature>();
        lf.add(f);
        annotateImageRequest.setFeatures(lf);

        //3.BUILD the Vision
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(new VisionRequestInitializer(API_KEY));
        Vision vision = builder.build();

        //4. CALL Vision.Images.Annotate
        BatchAnnotateImagesRequest batchAnnotateImagesRequest = new BatchAnnotateImagesRequest();
        List<AnnotateImageRequest> list = new ArrayList<AnnotateImageRequest>();
        list.add(annotateImageRequest);
        batchAnnotateImagesRequest.setRequests(list);
        Vision.Images.Annotate task = vision.images().annotate(batchAnnotateImagesRequest);
        BatchAnnotateImagesResponse response = task.execute();

        StringBuilder sb = new StringBuilder();
        // iterate through responses
        for (AnnotateImageResponse annotateImageResponse : response.getResponses()) {
            // iterate through each label in the response
            // get the first label and then its description
            for (int i = 0; i < 5; i++) {
                float score = annotateImageResponse.getLabelAnnotations().get(i).getScore();
                if (i == 0 && score < 0.85) {
                    sb.append(annotateImageResponse.getLabelAnnotations().get(i).getDescription()).append(", ");
                    break;
                } else if (score >= 0.85) {
                    sb.append(annotateImageResponse.getLabelAnnotations().get(i).getDescription()).append(", ");
                }
            }
        }
        // remove trailing ,space
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}
