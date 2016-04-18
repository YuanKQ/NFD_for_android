package net.named_data.nfd;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by yuan on 16-4-15.
 */
public class ChatLIstViewAdapter extends BaseAdapter {
    public static final int ROLE_OWN = 0;
    public static final int ROLE_TARGET = 1;
    public static final int ROLE_OTHER = 2;
    public static final String KEY_ROLE = "role";
    public static final String KEY_TEXT = "text";
    public static final String KEY_DATE = "date";
    public static final String KEY_NAME = "name";
    public static final String KEY_SHOW_MSG = "show_msg";

    private Context mContext;

    private ArrayList<HashMap<String, Object>> mDatalist;

    private LayoutInflater mInflater;

    private DisplayMetrics dm;

    public ChatLIstViewAdapter(Context mContext, ArrayList<HashMap<String, Object>> mDatalist) {
        this.mContext = mContext;
        this.mDatalist = mDatalist;
        mInflater = LayoutInflater.from(mContext);
        dm = new DisplayMetrics();
        ((Activity) mContext).getWindowManager().getDefaultDisplay().getMetrics(dm);
    }

    @Override
    public int getCount() {
        return mDatalist.size();
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        TextView text;
        TextView date;
        if(view == null) {
            View layout = mInflater.inflate(R.layout.chat_list_item_layout, null);
            if (layout == null)
                return null;
            //Content for msg: TextView
            text = (TextView) layout.findViewById(R.id.tvText);
            ClickListener listener = new ClickListener(text);
            text.setOnClickListener(listener);
            text.setTag(listener);

            //Date and time: TextView
            date = (TextView) layout.findViewById(R.id.tvDate);
            ViewHolder holder = new ViewHolder(null, text, date);
            holder.setPosition(i);
            layout.setTag(holder);
            view = layout;
        }else{
            ViewHolder holder = (ViewHolder) view.getTag();
            text = holder.mText;
            date = holder.mDate;
            holder.setPosition(i);
        }
        if(text == null || date == null)
            return null;

        int role = (Integer) mDatalist.get(i).get(KEY_ROLE);
        RelativeLayout rLayout = (RelativeLayout) view;
        RelativeLayout.LayoutParams param;
        switch (role) {
            case ROLE_OWN:  //msg text will dispaly on the right
                rLayout.removeAllViews();
                param = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                param.addRule(RelativeLayout.ALIGN_TOP);
                date.setText((String) mDatalist.get(i).get(KEY_DATE));
                rLayout.addView(date, param);

                text.setTextColor(Color.WHITE);
                text.setBackgroundResource(R.drawable.chart_list_item_right_selector);
                text.setText((String)mDatalist.get(i).get(KEY_TEXT));
                ClickListener listener = (ClickListener) text.getTag();
                if(listener != null){
                    if((Boolean)mDatalist.get(i).get(KEY_SHOW_MSG)){
                        listener.hideMessage();
                    }else{
                        listener.showMessage();
                    }
                }

                param = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                param.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                param.addRule(RelativeLayout.BELOW, date.getId());  //text will be display under the date
                rLayout.addView(text, param);
                break;

            case ROLE_TARGET:  //msg text will display on the left
                rLayout.removeAllViews();
                param = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                param.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                date.setText((String) mDatalist.get(i).get(KEY_DATE));
                rLayout.addView(date, param);

                text.setTextColor(Color.BLACK);
                text.setBackgroundResource(R.drawable.chart_list_item_left_selector);
                text.setText((String) mDatalist.get(i).get(KEY_TEXT));
                ClickListener listener2 = (ClickListener) text.getTag();
                if(listener2 != null){
                    if((Boolean)mDatalist.get(i).get(KEY_SHOW_MSG)){
                        listener2.hideMessage();
                    }else{
                        listener2.showMessage();
                    }
                }
                param = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                param.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                param.addRule(RelativeLayout.BELOW, date.getId());
                rLayout.addView(text, param);
                break;

            default:
                return null;
        }
        return rLayout;
    }

    private class ClickListener implements View.OnClickListener {

        private TextView mView;

        public ClickListener(TextView view) {
            mView = view;
        }

        public void showMessage(){
            mView.setTransformationMethod(PasswordTransformationMethod.getInstance());
        }

        public void hideMessage(){
            mView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        }

        @Override
        public void onClick(View view) {
            RelativeLayout rLayout = (RelativeLayout) mView.getParent();
            ViewHolder holder = (ViewHolder) rLayout.getTag();
            int pos = holder.getPosition();
            boolean isShow = (Boolean) mDatalist.get(pos).get(KEY_SHOW_MSG);
            if(isShow){
                mView.setTransformationMethod(PasswordTransformationMethod.getInstance());
                mDatalist.get(pos).put(KEY_SHOW_MSG, false);
            }else {
                mView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                mDatalist.get(pos).put(KEY_SHOW_MSG, true);
            }
        }
    }

    class ViewHolder {
        public TextView mName, mText, mDate;
        public int position;
        public ViewHolder(TextView mName, TextView mText, TextView mDate) {
            this.mName = mName;
            this.mText = mText;
            this.mDate = mDate;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }
    }
}
