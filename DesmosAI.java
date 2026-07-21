package com.desmosai;

import android.app.Activity;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.graphics.Color;
import java.io.OutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;

public class DesmosAI extends Activity {
    WebView desmosView;
    EditText inputText;
    String apiKey = "%%GROQ_API_KEY%%"; // Safe placeholder

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // --- Build UI Programmatically ---
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        desmosView = new WebView(this);
        desmosView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor("#222222"));
        bottomBar.setPadding(10, 10, 10, 10);

        inputText = new EditText(this);
        inputText.setHint("Ask Desmos AI...");
        inputText.setTextColor(Color.WHITE);
        inputText.setHintTextColor(Color.GRAY);
        inputText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button sendBtn = new Button(this);
        sendBtn.setText("Plot");
        sendBtn.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        bottomBar.addView(inputText);
        bottomBar.addView(sendBtn);
        rootLayout.addView(desmosView);
        rootLayout.addView(bottomBar);
        setContentView(rootLayout);

        // --- Configure Desmos WebView ---
        WebSettings settings = desmosView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        desmosView.loadUrl("https://www.desmos.com/calculator?embed");

        // --- Handle Button Clicks ---
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String userPrompt = inputText.getText().toString();
                if (!userPrompt.isEmpty()) {
                    inputText.setText("");
                    String jsCode = callGroq(userPrompt);
                    desmosView.evaluateJavascript(jsCode, null);
                }
            }
        });
    }

    // --- Pure Groq LLM Logic ---
    private String callGroq(String userInput) {
        try {
            URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String systemPrompt = "You are a Desmos API controller. Convert the user's request into valid JavaScript for the Desmos Calculator API. Use the global object 'Calc'. Examples: Calc.setExpression({id:'1', latex:'y=x^2'}); Calc.setExpression({id:'2', latex:'y=sin(x)', color:'red'}); Output ONLY valid JavaScript. No markdown, no backticks, no explanations.";
            
            String jsonInput = "{"
                + "\"model\": \"llama-3.3-70b-versatile\","
                + "\"messages\": ["
                + "  {\"role\": \"system\", \"content\": \"" + systemPrompt.replace("\"", "\\\"") + "\"},"
                + "  {\"role\": \"user\", \"content\": \"" + userInput.replace("\"", "\\\"") + "\"}"
                + "], \"temperature\": 0.2}";

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
            String jsCode = jsonObject.getJSONArray("choices")
                                     .getJSONObject(0)
                                     .getJSONObject("message")
                                     .getString("content");

            return jsCode.replace("```javascript", "").replace("```", "").trim();

        } catch (Exception e) {
            return "console.log('Error: " + e.getMessage() + "');";
        }
    }
}
