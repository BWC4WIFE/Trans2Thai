package com.Trans2Thai

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.Trans2Thai.databinding.ItemTranslationBinding

class TranslationAdapter : RecyclerView.Adapter<TranslationAdapter.TranslationViewHolder>() {

    private val translations = mutableListOf<Pair<String, Boolean>>() // Pair<Text, isUser>
    private var lastSpeakerIsUser: Boolean? = null

    class TranslationViewHolder(val binding: ItemTranslationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationViewHolder {
        val binding = ItemTranslationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TranslationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TranslationViewHolder, position: Int) {
    val (text, isUser) = translations[position]
    holder.binding.translationText.text = text
    if (isUser) {
        holder.binding.speakerLabel.text = "You said:"
        // Assuming you have a color resource named 'user_message_background'
        holder.binding.translationText.setBackgroundResource(R.drawable.chat_bubble)
    } else {
        holder.binding.speakerLabel.text = "Translation:"
        // Assuming you have a color resource named 'translation_message_background'
        holder.binding.translationText.setBackgroundResource(R.drawable.translation_chat_bubble)
    }
}

    override fun getItemCount() = translations.size

    fun addOrUpdateTranslation(text: String, isUser: Boolean) {
        if (lastSpeakerIsUser == isUser && translations.isNotEmpty()) {
            // Update last message
            translations[0] = text to isUser
            notifyItemChanged(0)
        } else {
            // Add new message
            translations.add(0, text to isUser)
            notifyItemInserted(0)
        }
        lastSpeakerIsUser = isUser
    }
}
