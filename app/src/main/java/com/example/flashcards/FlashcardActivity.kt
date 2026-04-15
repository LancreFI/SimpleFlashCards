package com.example.flashcards

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
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
    private var isRandom: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlashcardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deckName = intent.getStringExtra("DECK_NAME") ?: ""
        val json = intent.getStringExtra("JSON_DATA") ?: ""
        isRandom = intent.getBooleanExtra("IS_RANDOM", false)
        setupDeck(json, isRandom)

        binding.btnRestart.setOnClickListener {
            index = 0
            isFlipped = false
            val data = Gson().fromJson(json, WordList::class.java)
            val words = data.words?.map { Card(it.key, it.value) } ?: emptyList()
            deck = if (isRandom) words.shuffled() else words
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

        binding.btnDelete.setOnClickListener {
            showDeleteWordDialog()
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
        // ... (existing code)
    }

    private fun showDeleteWordDialog() {
        if (deck.isEmpty()) return
        val currentCard = deck[index]
        AlertDialog.Builder(this)
            .setTitle("Delete Word")
            .setMessage("Are you sure you want to delete '${currentCard.front}' from '$deckName'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteCurrentWord()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrentWord() {
        val prefs = getSharedPreferences("Decks", MODE_PRIVATE)
        val json = prefs.getString(deckName, null) ?: return
        val gson = Gson()
        val wordList = gson.fromJson(json, WordList::class.java)
        
        val words = wordList.words?.toMutableMap() ?: mutableMapOf()
        val currentCard = deck[index]
        
        // Find the key in the original wordList to remove
        // We need to be careful if languages were switched
        val keyToRemove = wordList.words?.entries?.find { 
            (it.key == currentCard.front && it.value == currentCard.back) ||
            (it.key == currentCard.back && it.value == currentCard.front)
        }?.key

        if (keyToRemove != null) {
            words.remove(keyToRemove)
            val updatedList = WordList(wordList.source, wordList.dest, words)
            prefs.edit().putString(deckName, gson.toJson(updatedList)).apply()
            
            val mutableDeck = deck.toMutableList()
            mutableDeck.removeAt(index)
            deck = mutableDeck

            if (deck.isEmpty()) {
                Toast.makeText(this, "Deck is now empty", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                if (index >= deck.size) index = deck.size - 1
                isFlipped = false
                updateDisplay()
                Toast.makeText(this, "Word deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateDisplay() {
        if (deck.isEmpty()) return
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
