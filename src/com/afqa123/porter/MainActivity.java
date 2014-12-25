package com.afqa123.porter;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

public class MainActivity extends Activity {
	
	private final static String LOG_SOURCE = "Porter";
	private final static String DEFAULT_FILE = "sms.xml";

	private ProgressDialog _pd;
	private EditText _editTextFile;
	private StoppableThread _worker;
		
	private Handler _updateProgressHandler = new Handler() {
		public void handleMessage(Message msg) {
			_pd.incrementProgressBy(1);
		}
	};
	
	private final Handler _workCompletedHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			
			try {
				_pd.dismiss();
				_worker = null;
				
				if (msg.obj == null) {
					AlertDialog.Builder dlg = new AlertDialog.Builder(MainActivity.this);
					dlg.setTitle(R.string.dialog_success);
					dlg.setMessage(R.string.label_success);
					dlg.setPositiveButton(R.string.button_ok, null);
					dlg.show();
				} else {
					Exception ex = (Exception)msg.obj;
					AlertDialog.Builder dlg = new AlertDialog.Builder(MainActivity.this);
					dlg.setTitle(R.string.dialog_error);
					dlg.setMessage(getString(R.string.label_error, ex.toString()));
					dlg.setPositiveButton(R.string.button_ok, null);
					dlg.show();
				}
				
			} catch (Exception ex) {
				Log.e(LOG_SOURCE, "Error handling message.", ex);
			}
		}
	};
	
	private class ImportThread extends StoppableThread {

		private static final String StopRequested = "Stop requested.";
		
		private class MyHandler extends DefaultHandler {
			@Override
			public void startElement(String uri, String localName, String qName,
					Attributes attributes) throws SAXException {
				super.startElement(uri, localName, qName, attributes);

				if (isStopped()) {
					throw new SAXException(StopRequested);
				}
				
				if (localName == "message") {
					ContentResolver cr = getContentResolver();
					ContentValues values = new ContentValues();
					for (String col : SmsProvider.COLUMNS_SMS) {
						values.put(col, attributes.getValue(col));    			
					}
					cr.insert(Uri.parse(SmsProvider.CONTENT_SMS), values);
					
					// TODO: update count
				}
			}		
		}

		@Override
		public void run() {
			super.run();

			Message m = Message.obtain(_workCompletedHandler);				

			try {
				SAXParserFactory spf = 	SAXParserFactory.newInstance();
		    	SAXParser sp = spf.newSAXParser();
		    	XMLReader xr = sp.getXMLReader();
		    	MyHandler mh = new MyHandler();
		    	xr.setContentHandler(mh);
		    	xr.parse(new InputSource(new FileReader(_editTextFile.getText().toString()))); 
				m.sendToTarget();					
		    	
			} catch (Exception ex) {
				if (!StopRequested.equals(ex.getMessage()))
				{
					m.obj = ex;
					m.sendToTarget();					
				}
			}
		}
	};
	
	private class ExportThread extends StoppableThread {

		private boolean exportMMS;
		
		public ExportThread(boolean exportMMS) {
			this.exportMMS = exportMMS;
		}
		
		@Override
		public void run() {
			Message m = Message.obtain(_workCompletedHandler);		
			FileWriter f = null;			
			try {
				// do this first, in case we cannot write to the file
				f = new FileWriter(_editTextFile.getText().toString());
								
				ContentResolver contentResolver = getContentResolver();
				List<Entry> messages = SmsProvider.readAll(contentResolver);
				if (exportMMS) {
					messages.addAll(MmsProvider.readAll(contentResolver));
				}
				
				_pd.setMax(messages.size());
			    f.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<messages>\n");		    	
				for (Entry e : messages) {
					if (isStopped()) {
						throw new InterruptedException();
					}
					writeEntry(f, e);
					_updateProgressHandler.sendMessage(_updateProgressHandler.obtainMessage());
				}
		    	f.write("</messages>");
				f.flush();
				m.sendToTarget();					
			} catch (InterruptedException e) {
		    	// process was stopped...
			} catch (Exception e) {
				Log.e(LOG_SOURCE, "Error during export.", e);
				m.obj = e;
				m.sendToTarget();					
			} finally {
				try {
					if (f != null) {
						f.close();
					}
				} catch (IOException e) {
				}
			}
		}
		
		private void writeEntry(FileWriter f, final Entry e) throws IOException {
			f.write(String.format("<message address=\"%s\" date=\"%s\" type=\"%s\" body=\"%s\" />\n", 
					TextUtils.htmlEncode(e.getAddress()), e.getDate(), e.isOutgoing() ? "2" : "1", TextUtils.htmlEncode(e.getBody())));
		}
	}

	private OnCancelListener _progressCancel = new OnCancelListener() {
		@Override
		public void onCancel(DialogInterface dialog) {
			if (_worker != null) {
				_worker.requestStop();
			}
		}
	};
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        Button buttonImport = (Button)findViewById(R.id.ButtonImport);
        buttonImport.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				importMessages();
			}
        });
        
        Button buttonExport = (Button)findViewById(R.id.ButtonExport);
        buttonExport.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				exportMessages();					
			}
        });
        
        _editTextFile = (EditText)findViewById(R.id.EditTextFile);
        String path = String.format("%s/%s",
        		Environment.getExternalStorageDirectory().getPath(), DEFAULT_FILE);
        _editTextFile.setText(path);
    }
    
    private void importMessages() {
    	_pd = new ProgressDialog(this);
    	_pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
    	_pd.setTitle(getString(R.string.dialog_progress));
    	_pd.setIndeterminate(true);
    	_pd.setCancelable(true);
    	_pd.setOnCancelListener(_progressCancel);
    	_pd.setMessage(getString(R.string.label_import));
    	_pd.show();    	
    	    	
		_worker = new ImportThread();
		_worker.start();
    }
    
    private void exportMessages() {
    	_pd = new ProgressDialog(this);
    	_pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	_pd.setTitle(getString(R.string.dialog_progress));
    	_pd.setIndeterminate(false);
    	_pd.setCancelable(true);
    	_pd.setOnCancelListener(_progressCancel);
    	_pd.setMessage(getString(R.string.label_export));
    	_pd.setProgress(0);
    	_pd.show();
    	
    	CheckBox checkMMS = (CheckBox)findViewById(R.id.check_mms);
    	_worker = new ExportThread(checkMMS.isChecked());
    	_worker.start();
	}
}