package com.kee.voicetranslator;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final int MIC_REQ = 10;
    private final String[] names = {"မြန်မာ", "ไทย", "中文", "English"};
    private final String[] codes = {"my", "th", "zh-CN", "en"};
    private final String[] locales = {"my-MM", "th-TH", "zh-CN", "en-US"};
    private Spinner fromSpinner, toSpinner;
    private EditText sourceText, resultText;
    private TextView status;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        tts = new TextToSpeech(this, this);
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle p) { status.setText("🎙 ပြောပါ…"); }
                public void onBeginningOfSpeech() { status.setText("အသံဖမ်းနေပါတယ်…"); }
                public void onRmsChanged(float r) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() { status.setText("ဖတ်နေပါတယ်…"); }
                public void onError(int e) { status.setText("အသံမဖတ်နိုင်ပါ။ ထပ်စမ်းပါ"); }
                public void onPartialResults(Bundle p) {}
                public void onEvent(int e, Bundle p) {}
                public void onResults(Bundle r) {
                    ArrayList<String> list = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (list != null && !list.isEmpty()) {
                        sourceText.setText(list.get(0));
                        translate(list.get(0));
                    }
                }
            });
        }
        buildUi();
    }

    private void buildUi() {
        int p = dp(16);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p,p,p,p);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Kee Voice Translator");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("ပြော → ဘာသာပြန် → အသံထွက်");
        sub.setTextSize(16);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0,8,0,16);
        root.addView(sub);

        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        LinearLayout row = new LinearLayout(this);
        fromSpinner = new Spinner(this); fromSpinner.setAdapter(ad); fromSpinner.setSelection(0);
        toSpinner = new Spinner(this); toSpinner.setAdapter(ad); toSpinner.setSelection(1);
        Button swap = new Button(this); swap.setText("⇄");
        swap.setOnClickListener(v -> {
            int a = fromSpinner.getSelectedItemPosition(), c = toSpinner.getSelectedItemPosition();
            fromSpinner.setSelection(c); toSpinner.setSelection(a);
            String x = sourceText.getText().toString();
            sourceText.setText(resultText.getText().toString()); resultText.setText(x);
        });
        row.addView(fromSpinner, new LinearLayout.LayoutParams(0,60,1));
        row.addView(swap, new LinearLayout.LayoutParams(70,60));
        row.addView(toSpinner, new LinearLayout.LayoutParams(0,60,1));
        root.addView(row);

        sourceText = new EditText(this); sourceText.setHint("စကားပြောပါ သို့ စာရိုက်ပါ"); sourceText.setMinLines(3); root.addView(sourceText);
        Button mic = new Button(this); mic.setText("🎙 စကားပြောမယ်"); mic.setOnClickListener(v -> startMic()); root.addView(mic);
        Button trans = new Button(this); trans.setText("ဘာသာပြန်မယ်"); trans.setOnClickListener(v -> translate(sourceText.getText().toString())); root.addView(trans);
        resultText = new EditText(this); resultText.setHint("ဘာသာပြန်ရလဒ်"); resultText.setMinLines(3); root.addView(resultText);
        Button speak = new Button(this); speak.setText("🔊 အသံထွက်"); speak.setOnClickListener(v -> speak(resultText.getText().toString())); root.addView(speak);
        Button key = new Button(this); key.setText("⚙ Google Translation API Key"); key.setOnClickListener(v -> keyDialog()); root.addView(key);
        status = new TextView(this); status.setText("အသင့်"); status.setPadding(0,12,0,0); root.addView(status);
        setContentView(scroll);
    }

    private void startMic() {
        if (recognizer == null) { Toast.makeText(this,"Speech recognition မရှိပါ",Toast.LENGTH_LONG).show(); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQ); return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, locales[fromSpinner.getSelectedItemPosition()]);
        recognizer.startListening(i);
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if (r == MIC_REQ && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startMic();
    }

    private void translate(String text) {
        text = text.trim();
        if (text.isEmpty()) return;
        final String key = getPreferences(MODE_PRIVATE).getString("api_key","").trim();
        if (key.isEmpty()) { keyDialog(); return; }
        final int f = fromSpinner.getSelectedItemPosition(), t = toSpinner.getSelectedItemPosition();
        status.setText("ဘာသာပြန်နေပါတယ်…");
        executor.execute(() -> {
            try {
                String endpoint = "https://translation.googleapis.com/language/translate/v2?key=" + URLEncoder.encode(key,"UTF-8");
                String body = "q=" + URLEncoder.encode(text,"UTF-8") + "&source=" + codes[f] + "&target=" + codes[t] + "&format=text";
                HttpURLConnection c = (HttpURLConnection)new URL(endpoint).openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(15000); c.setReadTimeout(20000);
                c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");
                try(OutputStream os=c.getOutputStream()){ os.write(body.getBytes(StandardCharsets.UTF_8)); }
                int rc=c.getResponseCode(); InputStream in=rc<300?c.getInputStream():c.getErrorStream();
                BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8)); StringBuilder sb=new StringBuilder(); String line;
                while((line=br.readLine())!=null) sb.append(line);
                if(rc>=300) throw new Exception("HTTP "+rc);
                JSONArray a=new JSONObject(sb.toString()).getJSONObject("data").getJSONArray("translations");
                final String out=a.getJSONObject(0).getString("translatedText");
                runOnUiThread(() -> { resultText.setText(out); status.setText("ပြီးပါပြီ ✓"); speak(out); });
            } catch(Exception e) { runOnUiThread(() -> status.setText("ဘာသာပြန်မရပါ: "+e.getMessage())); }
        });
    }

    private void speak(String s) {
        if (!ttsReady || s.trim().isEmpty()) return;
        Locale l = Locale.forLanguageTag(locales[toSpinner.getSelectedItemPosition()]);
        if (tts.setLanguage(l) < 0) { Toast.makeText(this,"ဒီဘာသာအသံကို ဖုန်းက မထောက်ပံ့သေးပါ",Toast.LENGTH_LONG).show(); return; }
        tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"kee");
    }

    private void keyDialog() {
        EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setText(getPreferences(MODE_PRIVATE).getString("api_key",""));
        new AlertDialog.Builder(this).setTitle("Google Cloud Translation API Key").setView(e)
                .setPositiveButton("Save",(d,w)->getPreferences(MODE_PRIVATE).edit().putString("api_key",e.getText().toString().trim()).apply())
                .setNegativeButton("Cancel",null).show();
    }

    @Override public void onInit(int s) { ttsReady = s == TextToSpeech.SUCCESS; }
    @Override protected void onDestroy() { super.onDestroy(); if(recognizer!=null) recognizer.destroy(); if(tts!=null) tts.shutdown(); executor.shutdownNow(); }
    private int dp(int n){ return Math.round(n*getResources().getDisplayMetrics().density); }
}
