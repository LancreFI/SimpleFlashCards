package com.example.flashcards

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.flashcards.databinding.ActivityAddCharacterBinding
import com.google.gson.Gson
import androidx.core.content.edit

class AddCharacterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddCharacterBinding
    private val prefs by lazy { getSharedPreferences("CharacterDecks", MODE_PRIVATE) }
    private val gson = Gson()
    private var currentDeckName: String? = null
    private val characters = mutableListOf<CharacterData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddCharacterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intentDeckName = intent.getStringExtra("DECK_NAME")
        if (intentDeckName != null) {
            currentDeckName = intentDeckName
            loadDeck(intentDeckName)
        } else {
            showDeckSelectionDialog()
        }

        binding.btnClear.setOnClickListener {
            binding.drawingView.clear()
        }

        binding.btnUndo.setOnClickListener {
            binding.drawingView.undo()
        }

        binding.btnRedo.setOnClickListener {
            binding.drawingView.redo()
        }

        binding.sbThickness.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.drawingView.setStrokeWidth(progress.toFloat().coerceAtLeast(1f))
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnSave.setOnClickListener {
            saveCharacter()
        }

        binding.btnFinish.setOnClickListener {
            finish()
        }
    }

    private fun showDeckSelectionDialog() {
        val decks = prefs.all.keys.toList()
        val options = mutableListOf<String>().apply {
            addAll(decks)
            add("Create New Deck")
        }

        AlertDialog.Builder(this)
            .setTitle("Select Character Deck")
            .setItems(options.toTypedArray()) { _, which ->
                if (which == options.size - 1) {
                    showCreateDeckDialog()
                } else {
                    currentDeckName = options[which]
                    loadDeck(currentDeckName!!)
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showCreateDeckDialog() {
        val input = android.widget.EditText(this).apply { hint = "Deck Name" }
        AlertDialog.Builder(this)
            .setTitle("New Character Deck")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    currentDeckName = name
                } else {
                    finish()
                }
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun loadDeck(name: String) {
        val json = prefs.getString(name, null)
        characters.clear()
        if (json != null) {
            try {
                val deck = gson.fromJson(json, CharacterDeck::class.java)
                if (deck?.characters != null) {
                    characters.addAll(deck.characters)
                }
                if (characters.isNotEmpty()) {
                    val lastWidth = characters.last().strokeWidth.toInt()
                    binding.sbThickness.progress = lastWidth
                    binding.drawingView.setStrokeWidth(lastWidth.toFloat())
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading deck", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCharacter() {
        val name = binding.etCharName.text.toString().trim()
        val strokes = binding.drawingView.getStrokes()

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
            return
        }

        if (strokes.isEmpty()) {
            Toast.makeText(this, "Please draw the character", Toast.LENGTH_SHORT).show()
            return
        }

        val strokeWidth = binding.sbThickness.progress.toFloat().coerceAtLeast(1f)
        val character = CharacterData(
            name,
            strokes,
            binding.cbRecordStrokeOrder.isChecked,
            binding.cbRecordStrokeDirection.isChecked,
            strokeWidth
        )
        characters.add(character)
        
        saveDeckToStorage()
        
        Toast.makeText(this, "Character '$name' saved!", Toast.LENGTH_SHORT).show()
        binding.etCharName.text.clear()
        binding.drawingView.clear()

        // If this is the first character, the thickness is already set by the user.
        // For subsequent characters, we hide/disable the thickness slider to enforce consistency?
        // Or just keep it as is, but the user asked for it to default to the same.
        if (characters.size == 1) {
            // We could disable it here if we want to enforce it.
            // binding.sbThickness.isEnabled = false 
        }
    }

    private fun saveDeckToStorage() {
        currentDeckName?.let { name ->
            val deck = CharacterDeck(name, characters)
            prefs.edit { putString(name, gson.toJson(deck)) }
        }
    }
}
