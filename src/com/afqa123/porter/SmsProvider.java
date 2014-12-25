package com.afqa123.porter;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class SmsProvider {

	final static String CONTENT_SMS = "content://sms";
	final static String[] COLUMNS_SMS = { "address", "date", "type", "body" };

	public static List<Entry> readAll(final ContentResolver aResolver) {
		Cursor cursor = null;
		List<Entry> entries = new ArrayList<Entry>();
		try {
			cursor = aResolver.query(Uri.parse(CONTENT_SMS), COLUMNS_SMS, null, null, null);			    
		    if (cursor.moveToFirst()) {
		    	do {
		    		SmsEntry e = new SmsEntry();
		    		e.setAddress(cursor.getString(cursor.getColumnIndex("address")));
		    		e.setDate(cursor.getString(cursor.getColumnIndex("date")));
		    		e.setType(cursor.getString(cursor.getColumnIndex("type")));
		    		e.setBody(cursor.getString(cursor.getColumnIndex("body")));
		    		entries.add(e);
		    	} while (cursor.moveToNext());
		    }
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return entries;
	}
}
