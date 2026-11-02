package com.example.signaldeep;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    public DatabaseHelper(Context c) { super(c, "signal.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE samples (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, net TEXT, " +
            "lvl INTEGER, pw INTEGER, q INTEGER, sinr INTEGER, " +
            "cid INTEGER, pci INTEGER, area INTEGER, freq INTEGER, " +
            "mcc INTEGER, mnc INTEGER, score INTEGER, grade TEXT, " +
            "fg TEXT, wrssi INTEGER, wssid TEXT)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {
        db.execSQL("DROP TABLE IF EXISTS samples"); onCreate(db);
    }
    public void insert(SignalSample s) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("ts", s.timestamp); v.put("net", s.networkType);
        v.put("lvl", s.level); v.put("pw", s.power);
        v.put("q", s.quality); v.put("sinr", s.sinr);
        v.put("cid", s.cellId); v.put("pci", s.pci);
        v.put("area", s.areaCode); v.put("freq", s.freqCode);
        v.put("mcc", s.mcc); v.put("mnc", s.mnc);
        v.put("score", s.score); v.put("grade", s.grade);
        v.put("fg", s.fgApp);
        v.put("wrssi", s.wifiRssi); v.put("wssid", s.wifiSsid);
        db.insert("samples", null, v); db.close();
    }
    public List<String> getRecent(int n) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
            "SELECT ts,net,lvl,pw,q,sinr,cid,pci,area,freq,score,grade,fg,wrssi " +
            "FROM samples ORDER BY ts DESC LIMIT " + n, null);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss", Locale.US);
        while (c.moveToNext()) {
            StringBuilder sb = new StringBuilder();
            sb.append(f.format(new Date(c.getLong(0)))).append("  ");
            sb.append(c.getString(1)).append("  ");
            sb.append(SignalInterpreter.bars(c.getInt(2))).append("\n");
            sb.append("   Power=").append(c.getInt(3)).append("dBm  ");
            sb.append("Quality=").append(c.getInt(4)).append("  ");
            sb.append("SINR=").append(c.getInt(5)).append("\n");
            sb.append("   Cell=").append(c.getInt(6)).append("  ");
            sb.append("PCI/PSC=").append(c.getInt(7)).append("  ");
            sb.append("TAC/LAC=").append(c.getInt(8)).append("  ");
            sb.append("Freq=").append(c.getInt(9)).append("\n");
            sb.append("   SCORE=").append(c.getInt(10)).append("/100  ");
            sb.append(c.getString(11)).append("\n");
            String fg = c.getString(12);
            if (fg != null && !fg.isEmpty()) sb.append("   FG App: ").append(fg).append("\n");
            int wr = c.getInt(13);
            if (wr != 0) sb.append("   WiFi: ").append(wr).append("dBm\n");
            out.add(sb.toString());
        }
        c.close(); db.close(); return out;
    }
}
