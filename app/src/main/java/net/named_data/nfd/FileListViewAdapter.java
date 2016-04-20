package net.named_data.nfd;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by yuan on 16-4-18.
 */
public class FileListViewAdapter extends BaseAdapter {
    public static final String KEY_NAME = "name";
    public static final String KEY_SIZE = "size";

    private ArrayList<HashMap<String, String>> mDatalist;
    private LayoutInflater mInflater;
    private Context mContext;

    public FileListViewAdapter(Context mContext, ArrayList<HashMap<String, String>> mDatalist) {
        this.mContext = mContext;
        this.mDatalist = mDatalist;
        mInflater = LayoutInflater.from(mContext);
    }

    @Override
    public int getCount() {
        return (mDatalist == null) ? 0 : mDatalist.size();
    }

    @Override
    public Object getItem(int i) {
        return mDatalist.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        FileItemHolder holder;

        for (int ti = 0; ti < mDatalist.size(); ++ ti) {
            Log.i("NFD", mDatalist.get(ti).toString());
        }

        if (view == null) {
            holder = new FileItemHolder();
            view = mInflater.inflate(R.layout.list_item_file_item, null);
            view.setTag(holder);

            holder.mfileName = (TextView) view.findViewById(R.id.list_item_file_name);
            holder.mfileSize = (TextView) view.findViewById(R.id.list_item_file_size);
        } else {
            holder = (FileItemHolder) view.getTag();
        }

        holder.mfileName.setText((String)mDatalist.get(i).get(KEY_NAME));
        holder.mfileSize.setText((String) mDatalist.get(i).get(KEY_SIZE));

        Log.i("NFD", "holder.mfileName: "+ holder.mfileName);


        return view;
    }

    class FileItemHolder {
        public TextView mfileName, mfileSize;
//        public FileItemHolder(TextView fileName, TextView fileSize) {
//            mfileName = fileName;
//            mfileSize = fileSize;
//        }
    }
}
