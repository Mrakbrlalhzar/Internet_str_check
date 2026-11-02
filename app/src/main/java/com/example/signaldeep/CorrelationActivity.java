package com.example.signaldeep;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class CorrelationActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_correlation);
        ListView lv = findViewById(R.id.list);
        DatabaseHelper db = new DatabaseHelper(this);
        List<String> rows = db.getRecent(200);
        lv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }
}
