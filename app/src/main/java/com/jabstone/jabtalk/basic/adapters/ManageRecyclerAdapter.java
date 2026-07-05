package com.jabstone.jabtalk.basic.adapters;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.jabstone.jabtalk.basic.JTApp;
import com.jabstone.jabtalk.basic.R;
import com.jabstone.jabtalk.basic.listeners.IDataStoreListener;
import com.jabstone.jabtalk.basic.storage.Ideogram;
import com.jabstone.jabtalk.basic.storage.Ideogram.Type;
import com.jabstone.jabtalk.basic.widgets.AutoResizeTextView;

import java.util.Collections;
import java.util.LinkedList;

public class ManageRecyclerAdapter extends RecyclerView.Adapter<ManageRecyclerAdapter.Holder>
        implements IDataStoreListener {

    private static final String TAG = ManageRecyclerAdapter.class.getSimpleName();
    private static final int VIEW_CATEGORY_LANDSCAPE = 0;
    private static final int VIEW_WORD_LANDSCAPE = 1;
    private static final int VIEW_CATEGORY_PORTRAIT = 2;
    private static final int VIEW_WORD_PORTRAIT = 3;

    public interface OnItemClickListener {
        void onItemClick(Ideogram gram);
    }

    public interface OnItemMoveListener {
        void onItemMoved();
    }

    private final Context m_context;
    private Ideogram m_parent;
    private OnItemClickListener m_clickListener = null;
    private OnItemMoveListener m_moveListener = null;
    private ItemTouchHelper m_touchHelper = null;
    private boolean m_sortMode = false;

    public ManageRecyclerAdapter(Context context, Ideogram parent) {
        m_context = context;
        m_parent = parent;
        setHasStableIds(true);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        m_clickListener = listener;
    }

    public void setOnItemMoveListener(OnItemMoveListener listener) {
        m_moveListener = listener;
    }

    public void setTouchHelper(ItemTouchHelper helper) {
        m_touchHelper = helper;
    }

    public void setSortMode(boolean enabled) {
        m_sortMode = enabled;
        notifyDataSetChanged();
    }

    public boolean isSortMode() {
        return m_sortMode;
    }

    public Ideogram getItem(int position) {
        return m_parent.getChildren(true).get(position);
    }

    @Override
    public int getItemCount() {
        return m_parent.getChildren(true).size();
    }

    @Override
    public long getItemId(int position) {
        Ideogram gram = m_parent.getChildren(true).get(position);
        return gram.getId().hashCode();
    }

    @Override
    public int getItemViewType(int position) {
        Ideogram gram = m_parent.getChildren(true).get(position);
        Bitmap jpg = gram.getImage();
        boolean landscape = jpg.getWidth() > jpg.getHeight();
        boolean category = gram.getType() == Type.Category;
        if (landscape) {
            return category ? VIEW_CATEGORY_LANDSCAPE : VIEW_WORD_LANDSCAPE;
        }
        return category ? VIEW_CATEGORY_PORTRAIT : VIEW_WORD_PORTRAIT;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(m_context);
        int layoutId;
        switch (viewType) {
            case VIEW_CATEGORY_LANDSCAPE:
                layoutId = R.layout.category_listitem_landscape;
                break;
            case VIEW_WORD_LANDSCAPE:
                layoutId = R.layout.word_listitem_landscape;
                break;
            case VIEW_CATEGORY_PORTRAIT:
                layoutId = R.layout.category_listitem_portrait;
                break;
            default:
                layoutId = R.layout.word_listitem_portrait;
                break;
        }
        View view = inflater.inflate(layoutId, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Ideogram ideogram = m_parent.getChildren(true).get(position);
        Bitmap jpg = ideogram.getImage();
        try {
            ImageView thumb = holder.itemView.findViewById(R.id.ideogram_thumb);
            TextView label = holder.itemView.findViewById(R.id.ideogram_label);
            TextView phrase = holder.itemView.findViewById(R.id.ideogram_phrase);
            RelativeLayout itemLayout = holder.itemView.findViewById(R.id.ideogram_listitem_layout);
            thumb.setImageBitmap(jpg);
            label.setText(ideogram.getLabel());
            phrase.setText(ideogram.getPhrase() != null
                    ? Html.fromHtml("<i>" + ideogram.getPhrase() + "&nbsp;</i>")
                    : "");
            itemLayout.setTag(ideogram);

            TextView playCount = holder.itemView.findViewById(R.id.ideogram_play_count);
            if (playCount != null) {
                int count = ideogram.getCurrentMonthPlayCount();
                if (count > 0) {
                    playCount.setText(String.valueOf(count));
                    playCount.setVisibility(View.VISIBLE);
                } else {
                    playCount.setVisibility(View.GONE);
                }
            }

            AutoResizeTextView textLabel = holder.itemView.findViewById(R.id.ideogram_text_label);
            if (ideogram.isHidden()) {
                int red = m_context.getResources().getColor(R.color.jabtalkRed);
                label.setTextColor(red);
                phrase.setTextColor(red);
                phrase.setText(Html.fromHtml("<i>"
                        + m_context.getString(R.string.manage_activity_hidden_item)
                        + "&nbsp;</i>"));
            } else {
                int black = m_context.getResources().getColor(R.color.jabtalkBlack);
                label.setTextColor(black);
                phrase.setTextColor(black);
            }
            if (ideogram.isTextButton()) {
                int thumbWidth = m_context.getResources()
                        .getDimensionPixelSize(R.dimen.image_thumb_landscape_width);
                int thumbHeight = m_context.getResources()
                        .getDimensionPixelSize(R.dimen.image_thumb_landscape_height);
                textLabel.setTextSize(50);
                textLabel.setText(ideogram.getLabel());
                textLabel.resizeText(
                        (int) (thumbWidth * JTApp.TEXT_BUTTON_THUMB_PADDING),
                        (int) (thumbHeight * JTApp.TEXT_BUTTON_THUMB_PADDING));
                textLabel.setVisibility(View.VISIBLE);
            } else {
                textLabel.setVisibility(View.GONE);
            }

            final Holder holderRef = holder;
            if (m_sortMode) {
                View.OnClickListener nullClick = null;
                itemLayout.setOnClickListener(nullClick);
                holder.itemView.setOnClickListener(nullClick);
                ((Activity) m_context).unregisterForContextMenu(itemLayout);
                holder.itemView.setAlpha(0.75f);
                View.OnTouchListener dragTouch = new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_DOWN && m_touchHelper != null) {
                            m_touchHelper.startDrag(holderRef);
                        }
                        return false;
                    }
                };
                itemLayout.setOnTouchListener(dragTouch);
                holder.itemView.setOnTouchListener(dragTouch);
            } else {
                holder.itemView.setAlpha(1.0f);
                itemLayout.setOnTouchListener(null);
                holder.itemView.setOnTouchListener(null);
                View.OnClickListener onClick = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Ideogram tagged = (Ideogram) itemLayout.getTag();
                        if (m_clickListener != null && tagged != null) {
                            m_clickListener.onItemClick(tagged);
                        }
                    }
                };
                itemLayout.setOnClickListener(onClick);
                holder.itemView.setOnClickListener(onClick);
                ((Activity) m_context).registerForContextMenu(itemLayout);
            }
        } catch (Exception e) {
            JTApp.logMessage(TAG, JTApp.LOG_SEVERITY_ERROR, e.getMessage());
        }
    }

    public boolean onItemMove(int from, int to) {
        LinkedList<Ideogram> children = m_parent.getChildren(true);
        if (from < 0 || to < 0 || from >= children.size() || to >= children.size()) {
            return false;
        }
        Collections.swap(children, from, to);
        notifyItemMoved(from, to);
        return true;
    }

    public void onItemMoveFinished() {
        if (m_moveListener != null) {
            m_moveListener.onItemMoved();
        }
    }

    @Override
    public void DataStoreUpdated() {
        Ideogram g = JTApp.getDataStore().getIdeogram(m_parent.getId());
        if (g != null && !g.equals(m_parent)) {
            m_parent = JTApp.getDataStore().getRootCategory();
        }
        notifyDataSetChanged();
    }

    static class Holder extends RecyclerView.ViewHolder {
        Holder(View itemView) {
            super(itemView);
        }
    }
}
