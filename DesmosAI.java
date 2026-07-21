package com.desmosai;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;

public class DesmosAI extends Activity {
    WebView desmosView;
    EditText inputText;
    Spinner modelSpinner;
    LinearLayout chatLayout;
    ScrollView chatScroll;
    String apiKey = "%%GROQ_API_KEY%%"; 
    
    // Chat history for context memory
    JSONArray chatHistory = new JSONArray();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // --- Root Layout ---
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(Color.parseColor("#121212"));
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // --- Top Bar (Model Selector) ---
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(Color.parseColor("#1F1F1F"));
        topBar.setPadding(10, 10, 10, 10);

        modelSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item);
        adapter.add("Loading models...");
        modelSpinner.setAdapter(adapter);
        topBar.addView(modelSpinner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        rootLayout.addView(topBar);

        // --- Desmos WebView (Top Half) ---
        desmosView = new WebView(this);
        desmosView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.5f));
        rootLayout.addView(desmosView);

        // --- Chat Scroll View (Bottom Half) ---
        chatScroll = new ScrollView(this);
        chatScroll.setBackgroundColor(Color.parseColor("#121212"));
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        chatLayout = new LinearLayout(this);
        chatLayout.setOrientation(LinearLayout.VERTICAL);
        chatLayout.setPadding(20, 20, 20, 20);
        chatScroll.addView(chatLayout);
        rootLayout.addView(chatScroll);

        // --- Bottom Input Bar ---
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#1F1F1F"));
        bottomBar.setPadding(10, 10, 10, 10);

        inputText = new EditText(this);
        inputText.setHint("Ask Desmos AI...");
        inputText.setTextColor(Color.WHITE);
        inputText.setHintTextColor(Color.GRAY);
        inputText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button sendBtn = new Button(this);
        sendBtn.setText("Send");
        sendBtn.setTextColor(Color.WHITE);
        sendBtn.setBackgroundColor(Color.parseColor("#6200EE"));
        sendBtn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        bottomBar.addView(inputText);
        bottomBar.addView(sendBtn);
        rootLayout.addView(bottomBar);

        setContentView(rootLayout);

        // --- Configure Desmos WebView ---
        WebSettings settings = desmosView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        desmosView.loadUrl("https://www.desmos.com/calculator?embed");

        // --- Fetch Models ---
        fetchGroqModels();

        // --- Handle Button Clicks ---
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userPrompt = inputText.getText().toString();
                if (!userPrompt.isEmpty()) {
                    inputText.setText("");
                    addChatBubble("You: " + userPrompt, true);
                    callGroq(userPrompt);
                }
            }
        });
    }

    private void fetchGroqModels() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://api.groq.com/openai/v1/models");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();

                    JSONObject jsonObject = new JSONObject(response.toString());
                    JSONArray models = jsonObject.getJSONArray("data");
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ArrayAdapter<String> adapter = new ArrayAdapter<>(DesmosAI.this, android.R.layout.simple_spinner_dropdown_item);
                                for (int i = 0; i < models.length(); i++) {
                                    String modelId = models.getJSONObject(i).getString("id");
                                    adapter.add(modelId);
                                }
                                modelSpinner.setAdapter(adapter);
                                // Set default to llama-3.3-70b-versatile if it exists
                                for (int i = 0; i < adapter.getCount(); i++) {
                                    if (adapter.getItem(i).equals("llama-3.3-70b-versatile")) {
                                        modelSpinner.setSelection(i);
                                        break;
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void addChatBubble(String text, boolean isUser) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(Color.WHITE);
        bubble.setPadding(30, 20, 30, 20);

        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(30f);

        if (isUser) {
            shape.setColor(Color.parseColor("#6200EE"));
        } else {
            shape.setColor(Color.parseColor("#333333"));
        }
        bubble.setBackground(shape);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 20);
        
        if (isUser) {
            params.gravity = Gravity.RIGHT;
        } else {
            params.gravity = Gravity.LEFT;
        }

        bubble.setLayoutParams(params);
        chatLayout.addView(bubble);
        chatScroll.post(new Runnable() {
            @Override
            public void run() {
                chatScroll.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void callGroq(String userInput) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String selectedModel = modelSpinner.getSelectedItem().toString();

                    // Build chat history
                    JSONObject systemMsg = new JSONObject();
                    systemMsg.put("role", "system");
                    systemMsg.put("content", "You are a Desmos API controller. Respond with a JSON object containing two keys: 'explanation' (a short text describing what you did) and 'desmos_js' (the raw JavaScript to execute using the 'Calc' object). Do not use markdown.");
                    
                    if (chatHistory.length() == 0) {
                        chatHistory.put(systemMsg);
                    }
                    
                    JSONObject userMsg = new JSONObject();
                    userMsg.put("role", "user");
                    userMsg.put("content", userInput);
                    chatHistory.put(userMsg);

                    URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);

                    String jsonInput = "{"
                        + "\"model\": \"" + selectedModel + "\","
                        + "\"messages\": " + chatHistory.toString() + ","
                        + "\"temperature\": 0.2,"
                        + "\"response_format\": {\"type\": \"json_object\"}"
                        + "}";

                    OutputStream os = conn.getOutputStream();
                    os.write(jsonInput.getBytes("UTF-8"));
                    os.close();

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    br.close();

                    JSONObject jsonObject = new JSONObject(response.toString());
                    String content = jsonObject.getJSONArray("choices")
                                             .getJSONObject(0)
                                             .getJSONObject("message")
                                             .getString("content");

                    JSONObject resultJson = new JSONObject(content);
                    String explanation = resultJson.getString("explanation");
                    String jsCode = resultJson.getString("desmos_js").replace("```javascript", "").replace("```", "").trim();

                    // Add assistant message to memory
                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", content);
                    chatHistory.put(assistantMsg);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addChatBubble("AI: " + explanation, false);
                            desmosView.evaluateJavascript(jsCode, null);
                        }
                    });

                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addChatBubble("Error: " + e.getMessage(), false);
                        }
                    });
                }
            }
        }).start();
    }
            }
