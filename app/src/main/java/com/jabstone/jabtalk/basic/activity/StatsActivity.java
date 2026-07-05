package com.jabstone.jabtalk.basic.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;

import java.util.List;

public class StatsActivity extends Activity {

    public static final String EXTRA_KIND = "kind";
    public static final String KIND_WORDS = "words";
    public static final String KIND_SENTENCES = "sentences";
    public static final String KIND_BIGRAMS = "bigrams";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stats_activity);
        setTitle(R.string.stats_activity_title);

        populateSection(R.id.stats_words_container, StatsHelper.topWords(10, null));
        populateSection(R.id.stats_sentences_container, StatsHelper.topSentences(10));
        populateSection(R.id.stats_bigrams_container, StatsHelper.topBigrams(10));

        wireViewAll(R.id.stats_words_view_all, KIND_WORDS);
        wireViewAll(R.id.stats_sentences_view_all, KIND_SENTENCES);
        wireViewAll(R.id.stats_bigrams_view_all, KIND_BIGRAMS);
    }

    private void populateSection(int containerId, List<StatsHelper.Row> rows) {
        LinearLayout container = findViewById(containerId);
        container.removeAllViews();
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.stats_empty);
            empty.setTextColor(getResources().getColor(R.color.jabtalkGray));
            int pad = (int) (10 * getResources().getDisplayMetrics().density);
            empty.setPadding(pad, pad, pad, pad);
            container.addView(empty);
            return;
        }
        int altColor = getResources().getColor(R.color.jabtalkStatsRowAlt);
        int normalColor = getResources().getColor(R.color.jabtalkWhite);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < rows.size(); i++) {
            StatsHelper.Row row = rows.get(i);
            View view = inflater.inflate(R.layout.stats_row, container, false);
            TextView label = view.findViewById(R.id.stats_row_label);
            TextView count = view.findViewById(R.id.stats_row_count);
            label.setText(row.label);
            count.setText(String.valueOf(row.count));
            view.setBackgroundColor(i % 2 == 1 ? altColor : normalColor);
            container.addView(view);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.stats_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_item_clear_stats) {
            confirmClearStats();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmClearStats() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.stats_clear_confirm_title)
                .setMessage(R.string.stats_clear_confirm_message)
                .setPositiveButton(R.string.button_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        JTApp.getDataStore().clearAllStats();
                        try {
                            JTApp.getDataStore().saveDataStore();
                        } catch (Exception ignored) {}
                        populateSection(R.id.stats_words_container, StatsHelper.topWords(10, null));
                        populateSection(R.id.stats_sentences_container, StatsHelper.topSentences(10));
                        populateSection(R.id.stats_bigrams_container, StatsHelper.topBigrams(10));
                        Toast.makeText(StatsActivity.this,
                                R.string.stats_cleared_toast, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.button_no, null)
                .show();
    }

    private void wireViewAll(int linkId, final String kind) {
        TextView link = findViewById(linkId);
        link.setPaintFlags(link.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(StatsActivity.this, StatsDetailActivity.class);
                intent.putExtra(EXTRA_KIND, kind);
                startActivity(intent);
            }
        });
    }
}
