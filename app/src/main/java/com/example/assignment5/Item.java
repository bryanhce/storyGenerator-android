package com.example.assignment5;

import android.graphics.Bitmap;

public class Item {
    Bitmap imageBitmap;
    String name;
    boolean isChecked;
    Item(Bitmap imageBitmap, String name) {
        this.imageBitmap = imageBitmap;
        this.name = name;
        this.isChecked = false;
    }
}
