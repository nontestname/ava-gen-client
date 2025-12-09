package org.labcitrus.avagenclient.stt;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

public class SpeechRecognizerManager {
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isListening = false;
    private boolean speechEndedNaturally = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final int TIMEOUT = 30000; // 30 seconds

    // Callback interface to deliver speech result
    public interface SpeechResultListener {
        void onResult(String result);
        void onError(int error);
    }

    private SpeechResultListener listener;

    // Runnable for automatic stop after timeout
    private Runnable stopRunnable = new Runnable() {
        @Override
        public void run() {
            if (isListening) {
                stopListening();
                Log.d("SpeechRecognizerManager", "Stopped listening after timeout");
            }
        }
    };

    public SpeechRecognizerManager(Context context) {
        // Create and configure the SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechRecognizerManager", "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d("SpeechRecognizerManager", "Speech started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // Not used
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // Not used
            }

            @Override
            public void onEndOfSpeech() {
                Log.d("SpeechRecognizerManager", "Speech ended");
                speechEndedNaturally = true;
            }

            @Override
            public void onError(int error) {
                Log.e("SpeechRecognizerManager", "Error code: " + error);
                isListening = false;
                handler.removeCallbacks(stopRunnable);
                if (listener != null) {
                    listener.onError(error);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String recognizedText = "";
                if (matches != null && !matches.isEmpty()) {
                    recognizedText = matches.get(0);
                }
                isListening = false;
                speechEndedNaturally = false;
                handler.removeCallbacks(stopRunnable);
                if (listener != null) {
                    listener.onResult(recognizedText);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Not used
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // Not used
            }
        });

        // Configure the recognizer intent
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
    }

    // Call this method to start listening. The result (or error) will be delivered via the listener.
    public void startListening(SpeechResultListener listener) {
        if (!isListening) {
            this.listener = listener;
            speechEndedNaturally = false;
            speechRecognizer.startListening(recognizerIntent);
            isListening = true;
            handler.postDelayed(stopRunnable, TIMEOUT);
            Log.d("SpeechRecognizerManager", "Started listening");
        }
    }

    // Call this method to manually stop listening (if needed)
    public void stopListening() {
        if (isListening) {
            handler.removeCallbacks(stopRunnable);
            speechRecognizer.stopListening();
            isListening = false;
        }
    }

    // Clean up resources
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}