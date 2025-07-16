package com.Trans2Thai

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gemweblive.databinding.ItemTranslationBinding

class TranslationAdapter : RecyclerView.Adapter<TranslationAdapter.TranslationViewHolder>() {

    private val translations = mutableListOf<Pair<String, Boolean>>() // Pair<Text, isUser>

    class TranslationViewHolder(val binding: ItemTranslationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TranslationViewHolder {
        val binding = ItemTranslationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TranslationViewHolder(binding)
    }

    ooverride fun onBindViewHolder(holder: TranslationViewHolder, position: Int) {
    val (text, isUser) = translations[position]
    holder.binding.translationText.text = text
    holder.binding.speakerLabel.text = if (isUser) "You said:" else "Translation:"
    
    val layoutParams = holder.binding.root.layoutParams as RecyclerView.LayoutParams
    val alignment = if (isUser) Gravity.END else Gravity.START
        
        val params = holder.binding.translationText.layoutParams as ViewGroup.MarginLayoutParams
        if (isUser) {
            holder.binding.translationText.gravity = Gravity.END
            params.setMargins(100, 0, 0, 0)
        } else {
            holder.binding.translationText.gravity = Gravity.START
            params.setMargins(0, 0, 100, 0)
        }
    }

    override fun getItemCount() = translations.size

    fun addOrUpdateTranslation(text: String, isUser: Boolean) {
        // This logic can be simplified if you always add a new message
        translations.add(0, text to isUser)
        notifyItemInserted(0)
    }
}
