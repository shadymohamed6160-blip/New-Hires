package com.newhires.camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQ_CAPTURE = 1;
    private static final String[] DEPTS = {
            "اختار القسم",
            // الأكثر استخدامًا
            "V.H",
            "sheep",
            "VH-HOG17",
            "Others",
            // باقي الأقسام
            "Adminstration",
            "Aux. Warehouse",
            "Bengali",
            "Byproducts 1-2",
            "Byproducts-3 (Beef)",
            "Drivers",
            "Exp.Frigo",
            "Green.",
            "Hog (Supervisors)",
            "Horse",
            "Housekeeping",
            "Imp.Frigo",
            "Maintenance & Works",
            "Maintenance(Admin)",
            "QulaityAssurance",
            "Security (مبيت)",
            "Security (نهارى)",
            "Sheep (Supervisors)",
            "Vh.Hog (Supervisors)",
            "Washing & Cleaning",
            "ثلاجات و تكييفات",
            "نظافه (الغرف)"
    };
    private static final String FOLDER = Environment.DIRECTORY_PICTURES + "/NewHires/";

    private static final int ID_NAME = 1001;
    private static final int ID_DEPT = 1002;

    private EditText nameInput;
    private Spinner deptSpinner;
    private TextView todayList;

    private Uri pendingUri;
    private String pendingFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("بصمة الجداد");
        setContentView(buildUi());

        if (savedInstanceState != null) {
            String u = savedInstanceState.getString("pendingUri");
            if (u != null) pendingUri = Uri.parse(u);
            pendingFileName = savedInstanceState.getString("pendingFileName");
        }
        askReadPermission();
        refreshToday();
    }

    // Lets the app see photos saved by older installs of the app too.
    private void askReadPermission() {
        String perm = Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_IMAGES"
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, 2);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshToday();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pendingUri != null) out.putString("pendingUri", pendingUri.toString());
        if (pendingFileName != null) out.putString("pendingFileName", pendingFileName);
    }

    // ---------- UI ----------

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        TextView hint = new TextView(this);
        hint.setText("اكتب الاسم واختار القسم وبعدين صوّر. الصورة بتتحفظ باسم الموظف في فولدر Pictures/NewHires على الموبايل.");
        hint.setTextSize(14);
        hint.setTextColor(Color.parseColor("#5E6B74"));
        root.addView(hint);

        root.addView(label("الاسم"));
        nameInput = new EditText(this);
        nameInput.setId(ID_NAME);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        nameInput.setSingleLine(true);
        nameInput.setTextSize(18);
        root.addView(nameInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(label("القسم"));
        deptSpinner = new Spinner(this);
        deptSpinner.setId(ID_DEPT);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DEPTS);
        deptSpinner.setAdapter(adapter);
        root.addView(deptSpinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView tips = new TextView(this);
        tips.setText("• الوش من قدام وباين كله\n• ورا حيطة سادة وإضاءة كويسة\n• من غير نضارة شمس أو كاب");
        tips.setTextSize(13);
        tips.setTextColor(Color.parseColor("#5E6B74"));
        tips.setPadding(0, dp(18), 0, 0);
        root.addView(tips);

        Button shoot = new Button(this);
        shoot.setText("صوّر واحفظ");
        shoot.setTextSize(20);
        shoot.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(20);
        shoot.setOnClickListener(v -> capture());
        root.addView(shoot, lp);

        Button sheet = new Button(this);
        sheet.setText("شيت إكسل من تاريخ لتاريخ");
        sheet.setTextSize(16);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        lp2.topMargin = dp(10);
        sheet.setOnClickListener(v -> pickDateForSheet());
        root.addView(sheet, lp2);

        root.addView(label("اتصوروا النهارده"));
        todayList = new TextView(this);
        todayList.setTextSize(15);
        todayList.setLineSpacing(0, 1.3f);
        todayList.setGravity(Gravity.START);
        root.addView(todayList);

        return scroll;
    }

    // ---------- capture ----------

    private static String cleanName(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]+", " ").replaceAll("\\s+", " ").trim();
    }

    private void capture() {
        String name = cleanName(nameInput.getText().toString());
        int deptIndex = deptSpinner.getSelectedItemPosition();

        if (name.isEmpty()) {
            nameInput.requestFocus();
            toast("اكتب الاسم الأول");
            return;
        }
        if (deptIndex <= 0) {
            toast("اختار القسم");
            return;
        }

        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        String fileName = name + " - " + DEPTS[deptIndex] + " - " + date + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER);

        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri;
        try {
            uri = getContentResolver().insert(collection, values);
        } catch (Exception e) {
            uri = null;
        }
        if (uri == null) {
            toast("مش قادر أعمل ملف الصورة. اتأكد إن فيه مساحة على الموبايل.");
            return;
        }

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        pendingUri = uri;
        pendingFileName = fileName;
        try {
            startActivityForResult(intent, REQ_CAPTURE);
        } catch (ActivityNotFoundException e) {
            deletePending();
            toast("مفيش أبلكيشن كاميرا على الموبايل");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE || pendingUri == null) return;

        if (resultCode == RESULT_OK && fileSize(pendingUri) > 0) {
            toast("اتحفظت: " + pendingFileName);
            nameInput.setText("");
            pendingUri = null;
            pendingFileName = null;
            refreshToday();
        } else {
            deletePending();
        }
    }

    private long fileSize(Uri uri) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            return pfd == null ? 0 : pfd.getStatSize();
        } catch (Exception e) {
            return 0;
        }
    }

    private void deletePending() {
        if (pendingUri != null) {
            try {
                getContentResolver().delete(pendingUri, null, null);
            } catch (Exception ignored) {
            }
        }
        pendingUri = null;
        pendingFileName = null;
    }

    // ---------- today's list ----------

    private void refreshToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long startSeconds = c.getTimeInMillis() / 1000;

        StringBuilder sb = new StringBuilder();
        int count = 0;
        ContentResolver cr = getContentResolver();
        String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
        String selection = MediaStore.Images.Media.RELATIVE_PATH + "=? AND " + MediaStore.Images.Media.DATE_ADDED + ">=?";
        String[] args = {FOLDER, String.valueOf(startSeconds)};

        try (Cursor cur = cr.query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection, selection, args, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            if (cur != null) {
                while (cur.moveToNext()) {
                    String n = cur.getString(0);
                    if (n != null && n.toLowerCase(Locale.US).endsWith(".jpg")) n = n.substring(0, n.length() - 4);
                    sb.append("• ").append(n).append('\n');
                    count++;
                }
            }
        } catch (Exception ignored) {
        }

        todayList.setText(count == 0 ? "لسه محدش اتصور النهارده" : sb.toString().trim());
    }

    // ---------- Excel sheet for a day ----------

    private static final Pattern FILE_PATTERN =
            Pattern.compile("^(.*) - (.*) - (\\d{2}-\\d{2}-\\d{4})(?: ?\\(\\d+\\))?\\.jpe?g$", Pattern.CASE_INSENSITIVE);

    private void pickDateForSheet() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog from = new DatePickerDialog(this, (v1, y1, m1, d1) -> {
            Calendar start = Calendar.getInstance();
            start.set(y1, m1, d1);
            DatePickerDialog to = new DatePickerDialog(this, (v2, y2, m2, d2) -> {
                Calendar end = Calendar.getInstance();
                end.set(y2, m2, d2);
                if (end.before(start)) exportSheet(end, start);
                else exportSheet(start, end);
            }, y1, m1, d1);
            to.setTitle("إلى تاريخ");
            to.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        from.setTitle("من تاريخ");
        from.show();
    }

    private static int dayKey(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    // "dd-MM-yyyy" -> yyyymmdd
    private static int dayKey(String ddMMyyyy) {
        String[] p = ddMMyyyy.split("-");
        return Integer.parseInt(p[2]) * 10000 + Integer.parseInt(p[1]) * 100 + Integer.parseInt(p[0]);
    }

    private static String csv(String v) {
        return "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"";
    }

    private void exportSheet(Calendar start, Calendar end) {
        SimpleDateFormat dayFmt = new SimpleDateFormat("dd-MM-yyyy", Locale.US);
        String fromText = dayFmt.format(start.getTime());
        String toText = dayFmt.format(end.getTime());
        int fromKey = dayKey(start), toKey = dayKey(end);
        boolean oneDay = fromKey == toKey;
        String rangeText = oneDay ? "يوم " + fromText : "من " + fromText + " إلى " + toText;

        java.util.List<String[]> rows = new java.util.ArrayList<>();   // name, dept, date, time, sortKey
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.US);
        String[] projection = {MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED};
        String selection = MediaStore.Images.Media.RELATIVE_PATH + "=?";
        String[] args = {FOLDER};

        try (Cursor cur = getContentResolver().query(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                projection, selection, args, MediaStore.Images.Media.DATE_ADDED + " ASC")) {
            if (cur != null) {
                while (cur.moveToNext()) {
                    String fileName = cur.getString(0);
                    if (fileName == null) continue;
                    Matcher m = FILE_PATTERN.matcher(fileName);
                    if (!m.matches()) continue;
                    int key = dayKey(m.group(3));
                    if (key < fromKey || key > toKey) continue;
                    long added = cur.getLong(1);
                    rows.add(new String[]{
                            m.group(1).trim(), m.group(2).trim(), m.group(3),
                            timeFmt.format(new Date(added * 1000L)),
                            String.format(Locale.US, "%08d%012d", key, added)});
                }
            }
        } catch (Exception e) {
            toast("حصلت مشكلة وأنا بقرا الصور");
            return;
        }

        if (rows.isEmpty()) {
            toast("مفيش حد اتصور " + rangeText);
            return;
        }
        rows.sort((x, y) -> x[4].compareTo(y[4]));

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append(csv("م")).append(',').append(csv("الاسم")).append(',').append(csv("القسم")).append(',')
          .append(csv("تاريخ التعيين")).append(',').append(csv("الساعة")).append("\r\n");
        int count = 0;
        for (String[] r : rows) {
            count++;
            sb.append(count).append(',').append(csv(r[0])).append(',').append(csv(r[1])).append(',')
              .append(csv(r[2])).append(',').append(csv(r[3])).append("\r\n");
        }

        String sheetName = oneDay ? "NewHires " + fromText + ".csv" : "NewHires " + fromText + " to " + toText + ".csv";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, sheetName);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/NewHires/");

        Uri uri;
        try {
            uri = getContentResolver().insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (uri == null) throw new Exception();
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            toast("مش قادر أحفظ الشيت");
            return;
        }

        final Uri sheetUri = uri;
        new AlertDialog.Builder(this)
                .setTitle("الشيت جاهز")
                .setMessage("فيه " + count + " موظف اتعينوا " + rangeText + ".\nاتحفظ في Download/NewHires على الموبايل.")
                .setPositiveButton("ابعته", (d, w) -> shareSheet(sheetUri))
                .setNegativeButton("تمام", null)
                .show();
    }

    private void shareSheet(Uri uri) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(send, "ابعت الشيت"));
        } catch (ActivityNotFoundException e) {
            toast("مفيش أبلكيشن يقدر يبعت الملف");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
