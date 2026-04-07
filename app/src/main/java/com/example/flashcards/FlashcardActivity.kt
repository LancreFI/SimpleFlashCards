package com.example.flashcards

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flashcards.databinding.ActivityFlashcardBinding
import com.google.gson.Gson


class FlashcardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFlashcardBinding
    private var deck = listOf<Card>()
    private var index = 0
    private var isFlipped = false
    private var sourceLang: String = ""
    private var destLang: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlashcardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra("JSON_DATA") ?: ""
        setupDeck(json)

        binding.btnRestart.setOnClickListener {
            index = 0
            isFlipped = false
            deck = deck.shuffled()
            binding.btnRestart.visibility = View.GONE
            updateDisplay()
        }

        binding.btnReturnMain.setOnClickListener {
            binding.btnRestart.visibility = View.GONE
            finish()
        }
        binding.btnReturnMain.visibility = View.VISIBLE
        
        binding.cardView.setOnClickListener {
            if (deck.isEmpty()) return@setOnClickListener

            if (!isFlipped) {
                // Reveal translation
                binding.tvWord.text = deck[index].back
                isFlipped = true
                binding.btnRestart.visibility = View.VISIBLE

                // Update bolding for destination
                binding.tvLanguageSrc.setTypeface(null, Typeface.NORMAL)
                binding.tvLanguageSrc.paintFlags = binding.tvLanguageSrc.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
                binding.tvLanguageDst.setTypeface(null, Typeface.BOLD)
                binding.tvLanguageDst.paintFlags = binding.tvLanguageDst.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                index++
                if (index < deck.size) {
                    isFlipped = false
                    updateDisplay()
                } else {
                    Toast.makeText(this, "Deck Finished!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDeck(json: String) {
        try {
            val data = Gson().fromJson(json, WordList::class.java)
            sourceLang = data.source ?: ""
            destLang = data.dest ?: ""

            deck = data.words?.map { Card(it.key, it.value) }?.shuffled() ?: emptyList()
            if (deck.isNotEmpty()) {
                updateDisplay()
            } else {
                Toast.makeText(this, "No words in deck", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            finish()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDisplay() {
        binding.tvCounter.text = "${index + 1} / ${deck.size}"
        binding.tvLanguageSrc.text = sourceLang
        binding.tvLanguageDst.text = destLang
        binding.tvWord.text = deck[index].front

        // Default bolding for source
        binding.tvLanguageSrc.setTypeface(null, Typeface.BOLD)
        binding.tvLanguageSrc.paintFlags = binding.tvLanguageSrc.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvLanguageDst.setTypeface(null, Typeface.NORMAL)
        binding.tvLanguageDst.paintFlags = binding.tvLanguageDst.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
    }
}