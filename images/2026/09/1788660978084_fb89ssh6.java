package com.karma666;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AIService {

    private static final String TAG = "AIService";
    private static final String PREFS_NAME = "auto_reply";
    private static final String KEY_AI_API = "ai_api";
    private static final String KEY_AI_API_KEY = "ai_api_key";
    private static final String KEY_AI_MODEL = "ai_model";
    private static final String KEY_AI_PROMPT = "ai_prompt";

    private static String lastError = "";

    public static String getLastError() {
        return lastError;
    }

    public static String getAIResponse(Context context, String userMessage, String myName) {
        lastError = "";
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String apiUrl = sp.getString(KEY_AI_API, "");
        String apiKey = sp.getString(KEY_AI_API_KEY, "");
        String model = sp.getString(KEY_AI_MODEL, "gpt-3.5-turbo");
        String promptTemplate = sp.getString(KEY_AI_PROMPT, "你是一个QQ聊天助手，你的名字叫{name}。用户正在QQ上@你，请用自然、友好的方式回复。回复要简短自然，不要超过100字。");

        Log.d(TAG, "=== AI请求开始 ===");
        Log.d(TAG, "apiUrl: " + apiUrl);
        Log.d(TAG, "model: " + model);
        Log.d(TAG, "apiKey长度: " + (apiKey != null ? apiKey.length() : 0));
        Log.d(TAG, "用户消息: " + userMessage);

        if (apiUrl.isEmpty()) {
            lastError = "API地址为空";
            Log.e(TAG, lastError);
            return null;
        }
        if (apiKey.isEmpty()) {
            lastError = "API密钥为空";
            Log.e(TAG, lastError);
            return null;
        }
        if (model.isEmpty()) {
            lastError = "模型名称为空";
            Log.e(TAG, lastError);
            return null;
        }

        String systemPrompt = promptTemplate.replace("{name}", myName);
        Log.d(TAG, "systemPrompt: " + systemPrompt);

        try {
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            JSONObject userMessageObj = new JSONObject();
            userMessageObj.put("role", "user");
            userMessageObj.put("content", "有人在QQ上@你，消息内容如下：\n" + userMessage + "\n请回复这条消息。");

            JSONArray messages = new JSONArray();
            messages.put(systemMessage);
            messages.put(userMessageObj);

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 200);
            requestBody.put("temperature", 0.8);

            Log.d(TAG, "请求体: " + requestBody.toString());

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            OutputStream os = conn.getOutputStream();
            os.write(requestBody.toString().getBytes("UTF-8"));
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "AI响应码: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Log.d(TAG, "AI响应: " + response.toString());

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject firstChoice = choices.getJSONObject(0);
                    JSONObject message = firstChoice.getJSONObject("message");
                    String content = message.getString("content");
                    conn.disconnect();
                    Log.d(TAG, "AI回复内容: " + content);
                    return content.trim();
                }
                conn.disconnect();
                lastError = "choices为空";
                Log.e(TAG, lastError);
                return null;
            } else {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                errorReader.close();
                lastError = "HTTP " + responseCode + ": " + errorResponse.toString();
                Log.e(TAG, "AI API错误: " + responseCode);
                Log.e(TAG, "错误详情: " + errorResponse.toString());
                conn.disconnect();
                return null;
            }
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            Log.e(TAG, "AI请求异常", e);
            e.printStackTrace();
            return null;
        }
    }
}