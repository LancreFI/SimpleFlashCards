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
    private var deckName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlashcardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deckName = intent.getStringExtra("DECK_NAME") ?: ""
        val json = intent.getStringExtra("JSON_DATA") ?: ""
        val isRandom = intent.getBooleanExtra("IS_RANDOM", false)
        setupDeck(json, isRandom)

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

        binding.btnSwitch.setOnClickListener {
            // Swap labels
            val temp = sourceLang
            sourceLang = destLang
            destLang = temp

            // Swap words in deck
            deck = deck.map { Card(it.back, it.front) }
            
            updateDisplay()
        }

        binding.btnNext.setOnClickListener {
            if (index < deck.size - 1) {
                index++
                isFlipped = false
                updateDisplay()
            } else {
                Toast.makeText(this, "End of deck", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnPrevious.setOnClickListener {
            if (index > 0) {
                index--
                isFlipped = false
                updateDisplay()
            }
        }

        binding.btnStar.setOnClickListener {
            starCurrentWord()
        }

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
                if (index < deck.size - 1) {
                    index++
                    isFlipped = false
                    updateDisplay()
                } else {
                    Toast.makeText(this, "Deck Finished!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDeck(json: String, isRandom: Boolean) {
        try {
            val data = Gson().fromJson(json, WordList::class.java)
            sourceLang = data.source ?: ""
            destLang = data.dest ?: ""

            val words = data.words?.map { Card(it.key, it.value) } ?: emptyList()
            deck = if (isRandom) words.shuffled() else words

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

    private fun starCurrentWord() {
        val currentCard = deck[index]
        val reviewDeckName = "Words to review: $deckName"
        val prefs = getSharedPreferences("Decks", MODE_PRIVATE)
        val gson = Gson()

        val existingJson = prefs.getString(reviewDeckName, null)
        val wordList = if (existingJson != null) {
            gson.fromJson(existingJson, WordList::class.java)
        } else {
            WordList(sourceLang, destLang, mutableMapOf())
        }

        val words = wordList.words?.toMutableMap() ?: mutableMapOf()
        words[currentCard.front] = currentCard.back
        
        val updatedList = WordList(wordList.source, wordList.dest, words)
        prefs.edit().putString(reviewDeckName, gson.toJson(updatedList)).apply()
        
        Toast.makeText(this, "Added to review list", Toast.LENGTH_SHORT).show()
        binding.btnStar.setImageResource(android.R.drawable.btn_star_big_on)
    }

    @SuppressLint("SetTextI18n")
    private fun updateDisplay() {
        binding.tvCounter.text = "${index + 1} / ${deck.size}"
        binding.tvLanguageSrc.text = sourceLang
        binding.tvLanguageDst.text = destLang
        binding.tvWord.text = deck[index].front
        binding.btnStar.setImageResource(android.R.drawable.btn_star_big_off)

        // Default bolding for source
        binding.tvLanguageSrc.setTypeface(null, Typeface.BOLD)
        binding.tvLanguageSrc.paintFlags = binding.tvLanguageSrc.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.tvLanguageDst.setTypeface(null, Typeface.NORMAL)
        binding.tvLanguageDst.paintFlags = binding.tvLanguageDst.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
    }
}
