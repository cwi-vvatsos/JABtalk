package com.jabstone.jabtalk.basic.activity;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jabstone.jabtalk.basic.R;

import java.util.ArrayList;
import java.util.List;

public class StatsDetailActivity extends Activity {

    private RecyclerView list;
    private TextView empty;
    private Adapter adapter;
    private String kind;
    private List<String> monthKeys;   // parallel to spinner options minus first ("All time")
    private String selectedMonth = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.stats_detail_activity);

        kind = getIntent().getStringExtra(StatsActivity.EXTRA_KIND);
        if (kind == null) kind = StatsActivity.KIND_WORDS;

        int headerLabelRes;
        switch (kind) {
            case StatsActivity.KIND_SENTENCES:
                setTitle(R.string.stats_detail_sentences_title);
                headerLabelRes = R.string.stats_detail_sentences_title;
                break;
            case StatsActivity.KIND_BIGRAMS:
                setTitle(R.string.stats_detail_bigrams_title);
                headerLabelRes = R.string.stats_detail_bigrams_title;
                break;
            default:
                setTitle(R.string.stats_detail_words_title);
                headerLabelRes = R.string.stats_detail_words_title;
        }
        ((TextView) findViewById(R.id.stats_detail_header_label)).setText(headerLabelRes);

        list = findViewById(R.id.stats_detail_list);
        empty = findViewById(R.id.stats_detail_empty);
        adapter = new Adapter();
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        LinearLayout filterBar = findViewById(R.id.stats_detail_filter_bar);
        if (StatsActivity.KIND_WORDS.equals(kind)) {
            filterBar.setVisibility(View.VISIBLE);
            setupMonthSpinner();
        }

        refresh();
    }

    private void setupMonthSpinner() {
        monthKeys = StatsHelper.knownMonths();
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.stats_month_all));
        options.addAll(monthKeys);
        ArrayAdapter<String> a = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, options) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.BLACK);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(android.graphics.Color.WHITE);
                }
                return v;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = findViewById(R.id.stats_detail_month_spinner);
        spinner.setAdapter(a);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMonth = position == 0 ? null : monthKeys.get(position - 1);
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void refresh() {
        List<StatsHelper.Row> rows;
        switch (kind) {
            case StatsActivity.KIND_SENTENCES:
                rows = StatsHelper.topSentences(0);
                break;
            case StatsActivity.KIND_BIGRAMS:
                rows = StatsHelper.topBigrams(0);
                break;
            default:
                rows = StatsHelper.topWords(0, selectedMonth);
        }
        adapter.setRows(rows);
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        private final List<StatsHelper.Row> rows = new ArrayList<>();

        void setRows(List<StatsHelper.Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.stats_row, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            StatsHelper.Row row = rows.get(position);
            holder.label.setText(row.label);
            holder.count.setText(String.valueOf(row.count));
            int altColor = holder.itemView.getResources().getColor(R.color.jabtalkStatsRowAlt);
            int normalColor = holder.itemView.getResources().getColor(R.color.jabtalkWhite);
            holder.itemView.setBackgroundColor(position % 2 == 1 ? altColor : normalColor);
        }

        @Override
        public int getItemCount() { return rows.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView label;
            final TextView count;
            Holder(View itemView) {
                super(itemView);
                label = itemView.findViewById(R.id.stats_row_label);
                count = itemView.findViewById(R.id.stats_row_count);
            }
        }
    }
}
