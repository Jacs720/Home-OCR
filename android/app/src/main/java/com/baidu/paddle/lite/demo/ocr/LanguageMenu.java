package com.baidu.paddle.lite.demo.ocr;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.ListPopupWindow;
import androidx.core.content.ContextCompat;

/** Anchored app-language dropdown shared by the main and checklist screens. */
public final class LanguageMenu {
    private LanguageMenu() { }

    public static void attach(Activity activity, ImageButton button) {
        button.setImageResource(AppLanguage.current(activity).badgeResource);
        button.setOnClickListener(view -> show(activity, button));
    }

    private static void show(Activity activity, ImageButton anchor) {
        AppLanguage[] languages = AppLanguage.values();
        ListPopupWindow popup = new ListPopupWindow(activity);
        popup.setAnchorView(anchor);
        popup.setAdapter(new LanguageAdapter(activity, languages));
        popup.setModal(true);
        popup.setDropDownGravity(Gravity.END);
        popup.setWidth(dp(activity, 260));
        popup.setBackgroundDrawable(ContextCompat.getDrawable(activity, R.drawable.bg_card));
        popup.setOnItemClickListener((parent, view, position, id) -> {
            popup.dismiss();
            AppLanguage selected = languages[position];
            if (selected != AppLanguage.current(activity)) selected.apply(activity);
        });
        popup.show();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class LanguageAdapter extends BaseAdapter {
        private final Activity activity;
        private final AppLanguage[] languages;

        LanguageAdapter(Activity activity, AppLanguage[] languages) {
            this.activity = activity;
            this.languages = languages;
        }

        @Override public int getCount() {
            return languages.length;
        }

        @Override public AppLanguage getItem(int position) {
            return languages[position];
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            ViewHolder holder;
            if (recycled instanceof LinearLayout) {
                holder = (ViewHolder) recycled.getTag();
            } else {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(activity, 16), dp(activity, 10),
                        dp(activity, 16), dp(activity, 10));
                TypedValue selectable = new TypedValue();
                if (activity.getTheme().resolveAttribute(
                        android.R.attr.selectableItemBackground, selectable, true)) {
                    row.setBackgroundResource(selectable.resourceId);
                }

                ImageView badge = new ImageView(activity);
                badge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                row.addView(badge, new LinearLayout.LayoutParams(
                        dp(activity, 62), dp(activity, 34)));

                TextView label = new TextView(activity);
                label.setTextColor(ContextCompat.getColor(activity, R.color.text_primary));
                label.setTextSize(16);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                labelParams.setMarginStart(dp(activity, 12));
                row.addView(label, labelParams);
                holder = new ViewHolder(badge, label);
                row.setTag(holder);
                recycled = row;
            }

            AppLanguage language = getItem(position);
            holder.badge.setImageResource(language.badgeResource);
            holder.label.setText(language == AppLanguage.current(activity)
                    ? activity.getString(R.string.language_selected_format, language.displayName)
                    : language.displayName);
            return recycled;
        }
    }

    private static final class ViewHolder {
        final ImageView badge;
        final TextView label;

        ViewHolder(ImageView badge, TextView label) {
            this.badge = badge;
            this.label = label;
        }
    }
}
