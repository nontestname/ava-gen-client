package org.labcitrus.avagenclient.ui;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import org.labcitrus.avagenclient.R;


public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final List<Message> messages;
    private final Context context;
    private final int maxBubbleWidth;

    public ChatAdapter(Context context, List<Message> messages) {
        this.context = context;
        this.messages = messages;


        // Calculate 75% of screen width
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        this.maxBubbleWidth = (int) (metrics.widthPixels * 0.75f);
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.chat_item, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        Message msg = messages.get(position);
        holder.messageText.setText(msg.text);
        holder.messageText.setMaxWidth(maxBubbleWidth);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.messageText.getLayoutParams();

        if (msg.isUser) {
            holder.messageText.setBackgroundResource(R.drawable.chat_bubble_user);
            params.gravity = Gravity.END;
        } else {
            holder.messageText.setBackgroundResource(R.drawable.chat_bubble_app);
            params.gravity = Gravity.START;
        }

        holder.messageText.setLayoutParams(params);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }


    static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.message_text);
        }
    }
}
