package org.labcitrus.avagenclient.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.labcitrus.avagenclient.R;

import java.util.ArrayList;
import java.util.List;

/**
 * FloatingPopupManager manages a global overlay popup window that displays
 * a conversation history and a mic button for speech input.
 * The popup is triggered by an accessibility overlay and does not rely on an Activity.
 */
public class FloatingPopupManager {

    /**
     * Callback interface to notify external components (e.g., Service)
     * when the mic icon is clicked to start listening.
     */
    public interface SpeechListenerDelegate {
        void onMicTriggered();
    }

    private final Context context;                   // Application context
    private final WindowManager windowManager;       // WindowManager for overlay display

    private View popupView;                          // Root view of the popup window
    private WindowManager.LayoutParams popupParams;  // Window layout params

    private RecyclerView chatRecycler;               // RecyclerView for chat messages
    private ChatAdapter chatAdapter;                 // Adapter for managing message views
    private final List<Message> messageList = new ArrayList<>(); // Message data list

    private TextView micStatusText;                  // TextView showing mic status (e.g., "Listening...")
    private boolean isListening = false;             // Whether mic is actively listening
    private ImageView micButton;                     // Mic button reference

    private SpeechListenerDelegate delegate;         // Optional external mic listener (e.g., speech manager)

    /**
     * Constructor to initialize the manager with context.
     * @param context The application or service context
     */
    public FloatingPopupManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Assigns a delegate that will be notified when the mic is triggered.
     * @param delegate An object implementing SpeechListenerDelegate
     */
    public void setSpeechListenerDelegate(SpeechListenerDelegate delegate) {
        this.delegate = delegate;
    }

    /**
     * Displays the popup conversation window as an overlay on screen.
     * Initializes the layout, recycler view, and mic button with listeners.
     */
    public void showPopupWindow() {
        if (popupView != null) return; // Prevent duplicate popups

        popupView = LayoutInflater.from(context).inflate(R.layout.popup_conversation_layout, null);

        // Define layout params for accessibility overlay window
        popupParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        popupParams.gravity = Gravity.CENTER;

        // Add the popup view to the window manager
        windowManager.addView(popupView, popupParams);

        // Setup RecyclerView
        chatRecycler = popupView.findViewById(R.id.chat_recycler);
        chatAdapter = new ChatAdapter(context, messageList);
        chatRecycler.setAdapter(chatAdapter);
        chatRecycler.setLayoutManager(new LinearLayoutManager(context));

        // Locate mic icon and mic status text
        micButton = popupView.findViewById(R.id.mic_icon);
        micStatusText = popupView.findViewById(R.id.mic_text);

        if (micButton != null) {
            micButton.setImageResource(R.drawable.mic_icon); // Set default mic icon

            // Toggle between start/stop listening
            micButton.setOnClickListener(v -> {
                if (isListening) {
                    stopListening();
                } else {
                    startListening();
                    if (delegate != null) {
                        delegate.onMicTriggered(); // Notify external listener (e.g., trigger speech)
                    }
                }
            });
        } else {
            Log.e("FloatingPopupManager", "❌ micButton is null — check R.id.mic_icon in layout");
        }

        // Optional: tapping anywhere on the popup dismisses it
        popupView.setOnClickListener(v -> removePopupWindow());
    }

    /**
     * Removes the popup window from the screen if it exists.
     */
    public void removePopupWindow() {
        if (popupView != null) {
            windowManager.removeView(popupView);
            popupView = null;
        }
    }

    /**
     * Adds a chat message to the conversation view.
     * @param message The message string to show
     * @param isUser True if the message is from the user, false if from the app
     */
    public void addMessage(String message, boolean isUser) {
        if (chatAdapter == null) return;

        messageList.add(new Message(message, isUser));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecycler.scrollToPosition(messageList.size() - 1); // Scroll to bottom
    }

    /**
     * Enters listening state: updates UI, icon, and prompts message.
     * External components should hook actual speech recognition logic via delegate.
     */
    public void startListening() {
        isListening = true;

        // Update mic status text and visuals
        if (micStatusText != null) {
            micStatusText.setText("Listening...");
        }
        if (micButton != null) {
            micButton.setImageResource(R.drawable.mic_icon_active);
            micButton.setBackgroundResource(R.drawable.mic_button_background_active);
        }

        // Show "listening" message from system
        // addMessage("🎤 Listening...", false);
    }

    /**
     * Exits listening state: resets UI and status indicators.
     */
    public void stopListening() {
        if (!isListening) return;
        isListening = false;

        // Reset mic text and visuals
        if (micStatusText != null) {
            micStatusText.setText("Click mic to speak");
        }
        if (micButton != null) {
            micButton.setImageResource(R.drawable.mic_icon);
            micButton.setBackgroundResource(R.drawable.mic_button_background);
        }
    }
}