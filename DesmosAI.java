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

        // --- Desmos WebView (Top 65%) ---
        desmosView = new WebView(this);
        desmosView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 2.0f));
        rootLayout.addView(desmosView);

        // --- Chat Scroll View (Bottom 35%) ---
        chatScroll = new ScrollView(this);
        chatScroll.setBackgroundColor(Color.parseColor("#121212"));
        chatScroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

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
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        
        String customHtml = "<!DOCTYPE html><html><head><script src=\"https://www.desmos.com/api/v1.8/calculator.js?apiKey=dcb31709b452b1cf9dc26972add0fda6\"></script><style>html,body{margin:0;padding:0;height:100%;overflow:hidden;} #calculator{width:100%;height:100%;}</style></head><body><div id=\"calculator\"></div><script>var elt=document.getElementById('calculator');window.Calc=Desmos.GraphingCalculator(elt,{keypad:true,expressions:true,settingsMenu:true,zoomButtons:true,expressionsTopbar:true,capExpressionSize:false});</script></body></html>";
        desmosView.loadDataWithBaseURL("https://www.desmos.com/", customHtml, "text/html", "UTF-8", null);

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

                    final JSONObject jsonObject = new JSONObject(response.toString());
                    final JSONArray models = jsonObject.getJSONArray("data");
                    
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

    private void callGroq(final String userInput) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String selectedModel = modelSpinner.getSelectedItem().toString();

                    JSONObject systemMsg = new JSONObject();
                    systemMsg.put("role", "system");
                    // MASSIVELY UPGRADED PROMPT TO FIX LATEX FORMATTING
                    systemMsg.put("content", "You are a Desmos Graphing Calculator API controller. Convert the user's request into valid JavaScript using the global `Calc` object. " +
                    "CRITICAL RULE: Desmos uses LaTeX. Math functions MUST be escaped with a backslash! Use \\sin(x), \\cos(x), \\tan(x), \\sqrt{x}, \\log(x). " +
                    "Do NOT write 'sin(x)', you MUST write '\\\\sin(x)' in the JSON string. " +
                    "Examples:\n" +
                    "- Plot a parabola: Calc.setExpression({id:'1', latex:'y=x^2'});\n" +
                    "- Plot sin x: Calc.setExpression({id:'2', latex:'y=\\\\sin(x)'});\n" +
                    "- Plot tangent graph: Calc.setExpression({id:'3', latex:'y=\\\\tan(x)'});\n" +
                    "- Add a slider for a: Calc.setExpression({id:'4', latex:'a=2', sliderBounds:{min:-10, max:10, step:0.1}});\n" +
                    "- Zoom out: Calc.setMathBounds({left:-10, right:10, bottom:-10, top:10});\n" +
                    "Respond with a JSON object containing 'explanation' and 'desmos_js'. Do not use markdown.");
                    
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
                    final String explanation = resultJson.getString("explanation");
                    final String jsCode = resultJson.getString("desmos_js").replace("```javascript", "").replace("```", "").trim();

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

                } catch (final Exception e) {
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
