package com.example.assignment5;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.widget.Toast;

public class StoryTeller extends Activity {

    SQLiteDatabase db;
    ArrayList<Item> itemList;
    ItemListAdapter adapter;
    EditText keywordsToFind;
    CheckBox includeSketchesCheckbox;
    TextView storyTextView;
    TextView selectedTags;
    ArrayList<String> selectedTagsArr;
    ListView lv;
    TextToSpeech tts;

    String url = "https://api.textcortex.com/v1/texts/social-media-posts";

    String API_KEY = "APIKEY";
    private MediaPlayer mediaPlayer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.story_page);

        db = this.openOrCreateDatabase("mydb", Context.MODE_PRIVATE, null);
        keywordsToFind = (EditText) findViewById(R.id.keywordsToFind);
        itemList = new ArrayList<>();
        adapter = new ItemListAdapter(this, R.layout.list_item_with_checkbox, itemList);
        includeSketchesCheckbox = findViewById(R.id.includeSketchesCheckbox);
        storyTextView = findViewById(R.id.storyTextView);
        selectedTags = findViewById(R.id.selectedTags);
        lv = findViewById(R.id.storyList);
        selectedTagsArr = new ArrayList<>();

        createDefaultList();

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.US);
                }
            }
        });
        tts.setPitch(0.3f);
        tts.setSpeechRate(1.5f);
        mediaPlayer = MediaPlayer.create(this, R.raw.filter_sound);
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

    protected void createDefaultList() {
        // grab data from the database and add it to array
        Cursor cursor = db.rawQuery("SELECT PICTURE, DATE, TAGS FROM SKETCHES " +
                        "UNION " +
                        "SELECT PICTURE, DATE, TAGS FROM IMAGES " +
                        "ORDER BY DATE DESC",
                null);
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
        lv.setAdapter(adapter);
    }

    public void findKeywords(View view) {
        playAudio(R.raw.filter_sound);

        String keywords = keywordsToFind.getText().toString();
        itemList = new ArrayList<>();
        // empty keyword is find everything
        StringBuilder query = new StringBuilder("SELECT PICTURE, DATE, TAGS FROM IMAGES ");
        if (!keywords.equals("")) {
            query.append("WHERE TAGS LIKE '%" + keywords + "%' ");
        }
        if (includeSketchesCheckbox.isChecked()) {
            query.append("UNION SELECT PICTURE, DATE, TAGS FROM SKETCHES ");
            if (!keywords.equals("")) {
                query.append("WHERE TAGS LIKE '%" + keywords + "%' ");
            }
        }
        query.append("ORDER BY DATE DESC");
//        Log.i("query", query.toString());
        Cursor cursor = db.rawQuery(query.toString(), null);
        for (int i = 0; i < cursor.getCount(); i++) {
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
        adapter = new ItemListAdapter(this, R.layout.list_item_with_checkbox, itemList);
        lv.setAdapter(adapter);
    }
    
    public void generateStory(View view) {
        String temp = selectedTags.getText().toString();
        if (temp.equals("")) {
            Toast.makeText(this, "No item selected", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedTagsArr.size() > 3) {
            Toast.makeText(this, "More than 3 items selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] keywordsArr = temp.split(" ,");
        try {
            makeRequest(keywordsArr);
        } catch (JSONException e) {
            Log.e("error", e.toString());
        }
    }

    private void makeRequest(String[] keywords) throws JSONException {
        // create JSON object
        JSONObject data = new JSONObject();
        data.put("context", "happy story");
        data.put("max_tokens", 100);
        data.put("mode", "twitter");
        data.put("model", "chat-sophos-1");
        data.put("keywords", new JSONArray(keywords));

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, data, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                Log.i("story", response.toString());
                try {
                    JSONArray outputsArray = response.getJSONObject("data").getJSONArray("outputs");
                    String text = outputsArray.getJSONObject(0).getString("text");
                    storyTextView.setText(text);
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                } catch (JSONException e) {
                    Log.e("error", e.toString());
                }

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("error", new String(error.networkResponse.data));

            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "Bearer " + API_KEY);
                return headers;
            }
        };

        RequestQueue requestQueue = Volley.newRequestQueue(this);
        requestQueue.add(request);
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
                        R.layout.list_item_with_checkbox, parent, false);
            }
            Item currentItem = getItem(position);
            CheckBox checkBox = convertView.findViewById(R.id.itemCheckbox);
            checkBox.setChecked(currentItem.isChecked);
            checkBox.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    CheckBox checkBox = (CheckBox) v;
                    boolean checked = checkBox.isChecked();
                    // so that other random boxes wont be ticked
                    currentItem.isChecked = checked;

                    String tag = currentItem.name.split("\n")[0];

                    if (checked) {
                        if (selectedTagsArr.size() == 3) {
                            Toast.makeText(getContext(), "More than 3 items selected", Toast.LENGTH_SHORT).show();
                            currentItem.isChecked = false;
                            checkBox.setChecked(false);
                            return;
                        }
                        selectedTagsArr.add(tag);
                        playAudio(R.raw.tag_sound);
                    } else {
                        selectedTagsArr.remove(tag);
                    }

                    String result = String.join(", ", selectedTagsArr);
                    selectedTags.setText(result);
                }
            });
            checkBox.setTag(currentItem);
            ImageView itemImage = convertView.findViewById(R.id.itemImage);
            TextView itemName = convertView.findViewById(R.id.itemName);
            itemImage.setImageBitmap(currentItem.imageBitmap);
            itemName.setText(currentItem.name);
            return convertView;
        }
    }

}
