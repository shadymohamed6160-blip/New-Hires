package com.newhires.camera;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
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

import java.text.SimpleDateFormat;
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
