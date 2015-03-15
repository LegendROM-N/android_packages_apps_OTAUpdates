package com.ota.updates.activities;

import in.uncod.android.bypass.Bypass;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toolbar;

import com.ota.updates.Addon;
import com.ota.updates.R;
import com.ota.updates.RomUpdate;
import com.ota.updates.download.DownloadAddon;
import com.ota.updates.tasks.AddonXmlParser;
import com.ota.updates.utils.Constants;
import com.ota.updates.utils.Preferences;
import com.ota.updates.utils.Utils;

public class AddonActivity extends Activity implements Constants {

	public final static String TAG = "AddonActivity";

	public static Context mContext;
	private static ListView mListview;
	private static DownloadAddon mDownloadAddon;
	private static Builder mNetworkDialog;

	@SuppressLint("NewApi") @Override
	public void onCreate(Bundle savedInstanceState) {
		mContext = this;
		setTheme(Preferences.getTheme(mContext));
		boolean isLollipop = Utils.isLollipop();

		super.onCreate(savedInstanceState);
		setContentView(R.layout.ota_addons);

		if (isLollipop) {
			Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar_addons);
			setActionBar(toolbar);
			toolbar.setTitle(getResources().getString(R.string.app_name));
		}

		mListview = (ListView) findViewById(R.id.listview);
		mDownloadAddon = new DownloadAddon();

		new LoadAddonManifest().execute(RomUpdate.getAddonsUrl(mContext));
	}

	public static void setupListView(ArrayList<Addon> addonsList) {		
		final AddonsArrayAdapter adapter = new AddonsArrayAdapter(mContext, addonsList);
		mListview.setAdapter(adapter);
	}

	private static class LoadAddonManifest extends AsyncTask<Object, Void, ArrayList<Addon>> {

		public final String TAG = this.getClass().getSimpleName();

		private static final String MANIFEST = "addon_manifest.xml";

		ProgressDialog mLoadingDialog;

		@Override
		protected void onPreExecute(){

			// Show a loading/progress dialog while the search is being performed
			mLoadingDialog = new ProgressDialog(mContext);
			mLoadingDialog.setIndeterminate(true);
			mLoadingDialog.setCancelable(false);
			mLoadingDialog.setMessage(mContext.getResources().getString(R.string.loading));
			mLoadingDialog.show();

			// Delete any existing manifest file before we attempt to download a new one
			File manifest = new File(mContext.getFilesDir().getPath(), MANIFEST);
			if(manifest.exists()) {
				manifest.delete();
			}
		}

		@Override
		protected ArrayList<Addon> doInBackground(Object... param) {

			try {
				InputStream input = null;

				URL url = new URL((String) param[0]);
				URLConnection connection = url.openConnection();
				connection.connect();
				// download the file
				input = new BufferedInputStream(url.openStream());

				OutputStream output = mContext.openFileOutput(
						MANIFEST, Context.MODE_PRIVATE);

				byte data[] = new byte[1024];
				int count;
				while ((count = input.read(data)) != -1) {
					output.write(data, 0, count);
				}

				output.flush();
				output.close();
				input.close();

				// file finished downloading, parse it!
				AddonXmlParser parser = new AddonXmlParser();
				return parser.parse(new File(mContext.getFilesDir(), MANIFEST), mContext);
			} catch (Exception e) {
				Log.d(TAG, "Exception: " + e.getMessage());
			}
			return null;
		}

		@Override
		protected void onPostExecute(ArrayList<Addon> result) {
			mLoadingDialog.cancel();
			setupListView(result);
			super.onPostExecute(result);
		}
	}

	public static class AddonsArrayAdapter extends ArrayAdapter<Addon> {

		public AddonsArrayAdapter(Context context, ArrayList<Addon> users) {
			super(context, 0, users);
		}

		public static void updateProgress(int index, int progress, boolean finished) {
			View v = mListview.getChildAt((index - 1) - 
					mListview.getFirstVisiblePosition());

			if(v == null) {
				return;
			}

			ProgressBar progressBar = (ProgressBar) v.findViewById(R.id.progress_bar);

			if (finished) {
				progressBar.setProgress(0);
			} else {
				progressBar.setProgress(progress);
			}
		}

		public static void updateButtons(int index, boolean finished) {
			View v = mListview.getChildAt((index - 1) - 
					mListview.getFirstVisiblePosition());

			if(v == null) {
				return;
			}

			final Button download = (Button) v.findViewById(R.id.download_button);
			final Button cancel = (Button) v.findViewById(R.id.cancel_button);
			final Button delete = (Button) v.findViewById(R.id.delete_button);

			if (finished) {
				download.setVisibility(View.VISIBLE);
				download.setText(mContext.getResources().getString(R.string.finished));
				download.setClickable(false);
				delete.setVisibility(View.VISIBLE);
				cancel.setVisibility(View.GONE);
			} else {
				download.setVisibility(View.VISIBLE);
				download.setText(mContext.getResources().getString(R.string.download));
				download.setClickable(true);
				cancel.setVisibility(View.GONE);
				delete.setVisibility(View.GONE);
			}
		}
		
		private void showNetworkDialog() {
			mNetworkDialog = new Builder(mContext);
			mNetworkDialog.setTitle(R.string.available_wrong_network_title)
			.setMessage(R.string.available_wrong_network_message)
			.setPositiveButton(R.string.ok, null)
			.setNeutralButton(R.string.settings, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent(mContext, SettingsActivity.class);
					mContext.startActivity(intent);
				}
			});
			
			mNetworkDialog.show();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			final Addon item = getItem(position);    
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(R.layout.card_addons_list_item, parent, false);
			}

			TextView title = (TextView) convertView.findViewById(R.id.title);
			TextView desc = (TextView) convertView.findViewById(R.id.description);
			TextView updatedOn = (TextView) convertView.findViewById(R.id.updatedOn);
			TextView filesize = (TextView) convertView.findViewById(R.id.size);
			final Button download = (Button) convertView.findViewById(R.id.download_button);
			final Button cancel = (Button) convertView.findViewById(R.id.cancel_button);
			final Button delete = (Button) convertView.findViewById(R.id.delete_button);

			title.setText(item.getTitle());

			Bypass byPass = new Bypass(mContext);
			String descriptionStr = item.getDesc();
			CharSequence string = byPass.markdownToSpannable(descriptionStr);
			desc.setText(string);
			desc.setMovementMethod(LinkMovementMethod.getInstance());

			String UpdatedOnStr = convertView.getResources().getString(R.string.addons_updated_on);
			String date = item.getUpdatedOn();

			Locale locale = Locale.getDefault();
			SimpleDateFormat fromDate = new SimpleDateFormat("yyyy-mm-dd", locale);
			SimpleDateFormat toDate = new SimpleDateFormat("dd, MMMM yyyy", locale);

			try {
				date = toDate.format(fromDate.parse(date));
			} catch (ParseException e) {
				e.printStackTrace();
			}

			updatedOn.setText(UpdatedOnStr + " " + date);

			filesize.setText(Utils.formatDataFromBytes(item.getFilesize()));
			final File file = new File(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DOWNLOADS, item.getTitle() + ".zip");

			if (DEBUGGING) {
				Log.d(TAG, "file path " + file.getAbsolutePath());
				Log.d(TAG, "file length " + file.length() + " remoteLength " +  item.getFilesize());
			}
			if (file.length() == item.getFilesize()) {
				download.setVisibility(View.VISIBLE);
				download.setText(mContext.getResources().getString(R.string.finished));
				download.setClickable(false);
				delete.setVisibility(View.VISIBLE);
				cancel.setVisibility(View.GONE);
			} else {
				download.setVisibility(View.VISIBLE);
				download.setText(mContext.getResources().getString(R.string.download));
				download.setClickable(true);
				cancel.setVisibility(View.GONE);
				delete.setVisibility(View.GONE);
			}

			download.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					boolean isMobile = Utils.isMobileNetwork(mContext);
					boolean isSettingWiFiOnly = Preferences.getNetworkType(mContext).equals("2");

					if (isMobile && isSettingWiFiOnly) {
						showNetworkDialog();
					} else {
						mDownloadAddon.startDownload(mContext, item.getDownloadLink(), item.getTitle(), item.getId());
						download.setVisibility(View.GONE);
						cancel.setVisibility(View.VISIBLE);
					}
				}
			});

			cancel.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					mDownloadAddon.cancelDownload(mContext, item.getId());
					download.setVisibility(View.VISIBLE);
					cancel.setVisibility(View.GONE);
					updateProgress(item.getId(), 0, true);
				}
			});

			delete.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					if (file.exists()) {
						file.delete();
						updateButtons(item.getId(), false);
					}					
				}
			});

			return convertView;
		}
	}
}