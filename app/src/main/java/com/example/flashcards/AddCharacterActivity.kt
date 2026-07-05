package com.example.flashcards

import android.os.Bundle
import android.view.View
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
    private var currentIndex = -1 // -1 means new character mode

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

        binding.btnHome.setOnClickListener {
            finish()
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

        binding.btnDeleteChar.setOnClickListener {
            deleteCurrentCharacter()
        }

        binding.btnPrevious.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                displayCharacter(currentIndex)
            } else if (currentIndex == -1 && characters.isNotEmpty()) {
                currentIndex = characters.size - 1
                displayCharacter(currentIndex)
            }
        }

        binding.btnNext.setOnClickListener {
            if (currentIndex != -1 && currentIndex < characters.size - 1) {
                currentIndex++
                displayCharacter(currentIndex)
            } else {
                switchToNewCharacterMode()
            }
        }

        binding.btnFinish.setOnClickListener {
            finish()
        }
        
        updateNavigationButtons()
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
                    switchToNewCharacterMode()
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
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading deck", Toast.LENGTH_SHORT).show()
            }
        }
        
        if (characters.isNotEmpty()) {
            val editCharName = intent.getStringExtra("EDIT_CHAR_NAME")
            if (editCharName != null) {
                currentIndex = characters.indexOfFirst { it.name == editCharName }
                if (currentIndex == -1) currentIndex = 0
            } else {
                currentIndex = 0
            }
            displayCharacter(currentIndex)
        } else {
            switchToNewCharacterMode()
        }
    }

    private fun displayCharacter(index: Int) {
        val char = characters[index]
        binding.etCharName.setText(char.name)
        binding.cbRecordStrokeOrder.isChecked = char.checkStrokeOrder
        binding.cbRecordStrokeDirection.isChecked = char.checkStrokeDirection
        binding.sbThickness.progress = char.strokeWidth.toInt()
        binding.drawingView.setStrokeWidth(char.strokeWidth)
        binding.drawingView.setStrokes(char.strokes)
        
        binding.tvCharCounter.text = "${index + 1} / ${characters.size}"
        binding.btnDeleteChar.visibility = View.VISIBLE
        updateNavigationButtons()
    }

    private fun switchToNewCharacterMode() {
        currentIndex = -1
        binding.etCharName.text.clear()
        binding.drawingView.clear()
        binding.tvCharCounter.text = "New Character"
        binding.btnDeleteChar.visibility = View.GONE
        
        if (characters.isNotEmpty()) {
            val lastWidth = characters.last().strokeWidth.toInt()
            binding.sbThickness.progress = lastWidth
            binding.drawingView.setStrokeWidth(lastWidth.toFloat())
        }
        updateNavigationButtons()
    }

    private fun updateNavigationButtons() {
        binding.btnPrevious.isEnabled = currentIndex > 0 || (currentIndex == -1 && characters.isNotEmpty())
        binding.btnNext.isEnabled = currentIndex != -1 || characters.isEmpty() // Allow "Next" to go to New mode
        // If we are at the last character, Next goes to New mode.
        // If we are in New mode, Next is disabled (or we could say it's always enabled if we want infinite new)
        if (currentIndex == -1) {
             binding.btnNext.isEnabled = false
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
        
        if (currentIndex == -1) {
            characters.add(character)
            currentIndex = characters.size - 1
            Toast.makeText(this, "Character '$name' added!", Toast.LENGTH_SHORT).show()
        } else {
            characters[currentIndex] = character
            Toast.makeText(this, "Character '$name' updated!", Toast.LENGTH_SHORT).show()
        }
        
        saveDeckToStorage()
        displayCharacter(currentIndex)
    }

    private fun deleteCurrentCharacter() {
        if (currentIndex == -1) return
        
        AlertDialog.Builder(this)
            .setTitle("Delete Character")
            .setMessage("Are you sure you want to delete '${characters[currentIndex].name}'?")
            .setPositiveButton("Delete") { _, _ ->
                characters.removeAt(currentIndex)
                saveDeckToStorage()
                if (characters.isEmpty()) {
                    switchToNewCharacterMode()
                } else {
                    if (currentIndex >= characters.size) currentIndex = characters.size - 1
                    displayCharacter(currentIndex)
                }
                Toast.makeText(this, "Character deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDeckToStorage() {
        currentDeckName?.let { name ->
            val deck = CharacterDeck(name, characters)
            prefs.edit { putString(name, gson.toJson(deck)) }
        }
    }
}
