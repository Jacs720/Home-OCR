package com.baidu.paddle.lite.demo.ocr;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChecklistAdapter extends BaseAdapter {
    public interface OnOwnedChangedListener {
        void onOwnedChanged(ChecklistEntry entry, boolean owned);
    }

    private final LayoutInflater inflater;
    private final OnOwnedChangedListener listener;
    private final List<ChecklistEntry> entries = new ArrayList<>();

    public ChecklistAdapter(Context context, OnOwnedChangedListener listener) {
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setEntries(List<ChecklistEntry> updated) {
        entries.clear();
        entries.addAll(updated);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return entries.size();
    }

    @Override
    public ChecklistEntry getItem(int position) {
        return entries.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_checklist_entry, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ChecklistEntry entry = getItem(position);
        holder.number.setText(String.format(Locale.ROOT, "#%04d", entry.nationalNumber));
        holder.pokemon.setText(entry.pokemon);
        holder.target.setText(entry.originMark);
        holder.owned.setOnCheckedChangeListener(null);
        holder.owned.setChecked(entry.owned);
        holder.owned.setText(entry.owned ? R.string.checklist_owned : R.string.checklist_pending);
        int color = ContextCompat.getColor(parent.getContext(), entry.owned
                ? R.color.checklist_owned : R.color.checklist_pending);
        holder.owned.setTextColor(color);
        holder.owned.setOnCheckedChangeListener((button, checked) -> {
            entry.owned = checked;
            listener.onOwnedChanged(entry, checked);
            notifyDataSetChanged();
        });
        convertView.setOnClickListener(view -> holder.owned.setChecked(!holder.owned.isChecked()));
        return convertView;
    }

    private static final class ViewHolder {
        final TextView number;
        final TextView pokemon;
        final TextView target;
        final CheckBox owned;

        ViewHolder(View view) {
            number = view.findViewById(R.id.checklist_number);
            pokemon = view.findViewById(R.id.checklist_pokemon);
            target = view.findViewById(R.id.checklist_target);
            owned = view.findViewById(R.id.checklist_owned);
        }
    }
}
