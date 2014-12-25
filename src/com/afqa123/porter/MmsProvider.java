package com.afqa123.porter;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class MmsProvider {

	private static final String CONTENT_MMS = "content://mms";
	private static final String CONTENT_MMS_PART = "content://mms/part";
	private static final String TEXT_PLAIN = "text/plain";
	private static final String ADDR_TYPE_INCOMING = "137";
	private static final String ADDR_TYPE_OUTGOING = "151";
	
	public static List<Entry> readAll(final ContentResolver aResolver) {
		List<Entry> entries = new ArrayList<Entry>();
		Cursor cursor = null;
		try {
			cursor = aResolver.query(Uri.parse(CONTENT_MMS), new String[] { "_id", "date", "m_type" }, null, null, null);
			if (cursor.moveToFirst()) {
				do {
					MmsEntry e = new MmsEntry();
					e.setId(cursor.getString(cursor.getColumnIndex("_id")));
					e.setDate(cursor.getString(cursor.getColumnIndex("date")));
					e.setType(cursor.getString(cursor.getColumnIndex("m_type")));
					readMessageAddress(aResolver, e, e.isOutgoing() ? ADDR_TYPE_OUTGOING : ADDR_TYPE_INCOMING);
					readMessageBody(aResolver, e);
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
	
	public static void readMessageAddress(final ContentResolver aResolver, final MmsEntry anEntry, final String anAddrType) {
		Uri uriAddrPart = Uri.parse(String.format("%s/%s/addr", CONTENT_MMS, anEntry.getId()));
        Cursor cursor = null;
        try {
        	cursor = aResolver.query(uriAddrPart, null, "type=" + anAddrType, null, "_id");
	        if (cursor.moveToFirst()) {
	        	anEntry.setAddress(cursor.getString(cursor.getColumnIndex("address")));
	        }
        } finally {
        	if (cursor != null) {
        		cursor.close();
        	}
        }
	}
		
	public static void readMessageBody(final ContentResolver aResolver, final MmsEntry anEntry) {
		Uri uri = Uri.parse(CONTENT_MMS_PART);
		String[] projection = new String[] { "ct", "text" };
		String selection = String.format("mid = %s", anEntry.getId());
		Cursor cursor = null;
		try {
			cursor = aResolver.query(uri, projection, selection, null, null);
			StringBuilder sb = new StringBuilder();
			if (cursor.moveToFirst()) {
				do {
					if (!TEXT_PLAIN.equals(cursor.getString(cursor.getColumnIndex("ct")))) {
						continue;
					}
					sb.append(cursor.getString(cursor.getColumnIndex("text")));
				} while (cursor.moveToNext());				
			}
			anEntry.setBody(sb.toString());
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}
}
