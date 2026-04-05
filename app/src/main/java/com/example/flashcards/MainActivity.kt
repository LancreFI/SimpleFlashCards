package com.example.flashcards // Change this to your package name

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.flashcards.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences("Decks", MODE_PRIVATE) }
    private val gson = Gson()

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleFileUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnHelp.setOnClickListener { showHelp() }
        binding.btnAdd.setOnClickListener { showAddOptions() }
        loadDecksFromStorage()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("JSON Format")
            .setMessage("File must be formatted as:\n\n{\n \"source language\": \"English\",\n \"destination language\": \"Finnish\",\n \"words\": {\"apple\": \"omena\", \n                   \"uncle\": \"setä\"}\n}")
            .setPositiveButton("OK", null).show()
    }

    private fun showAddOptions() {
        val options = arrayOf("Download from URL", "Select Local File")
        AlertDialog.Builder(this)
            .setTitle("Add Wordlist")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDownloadDialog()
                    1 -> pickFileLauncher.launch("application/json")
                }
            }
            .show()
    }

    private fun showDownloadDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
        }
        val nameInput = EditText(this).apply { hint = "Deck Name (e.g. Spanish)" }
        val urlInput = EditText(this).apply { hint = "JSON URL" }
        layout.addView(nameInput)
        layout.addView(urlInput)

        AlertDialog.Builder(this)
            .setTitle("Download Deck")
            .setView(layout)
            .setPositiveButton("Download") { _, _ ->
                val url = urlInput.text.toString()
                val name = nameInput.text.toString()
                if (url.isEmpty()) Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
                else if (name.isEmpty()) Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                else downloadJson((url), (name))
            }.show()
    }

    private fun downloadJson(url: String, nickname: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = URL(url).readText()
                processAndSaveJson(json, nickname)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleFileUri(uri: Uri) {
        val nicknameInput = EditText(this).apply { hint = "Deck Name" }
        AlertDialog.Builder(this)
            .setTitle("Enter Deck Name")
            .setView(nicknameInput)
            .setPositiveButton("Add") { _, _ ->
                val name = nicknameInput.text.toString()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            if (json != null) {
                                processAndSaveJson(json, name)
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "Failed to read file", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }.show()
    }

    private suspend fun processAndSaveJson(json: String, name: String) {
        val validatedJson = validateAndSanitizeJson(json)
        withContext(Dispatchers.Main) {
            if (validatedJson != null) {
                prefs.edit().putString(name, validatedJson).apply()
                loadDecksFromStorage()
                Toast.makeText(this@MainActivity, "Deck '$name' added!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Invalid JSON file", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validateAndSanitizeJson(json: String): String? {
        return try {
            val wordList = gson.fromJson(json, WordList::class.java)
            
            // Validation
            if (wordList.source.isNullOrBlank() || 
                wordList.dest.isNullOrBlank() ||
                wordList.words.isNullOrEmpty()
            ) {
                return null
            }

            // Sanitization: Create a clean copy with only string entries
            val sanitizedWords = mutableMapOf<String, String>()
            wordList.words.forEach { (k, v) ->
                // Basic sanitization: trim and ensure they aren't empty
                val cleanKey = k.trim()
                val cleanValue = v.trim()
                if (cleanKey.isNotEmpty() && cleanValue.isNotEmpty()) {
                    sanitizedWords[cleanKey] = cleanValue
                }
            }

            if (sanitizedWords.isEmpty()) return null

            val sanitizedList = WordList(
                source = wordList.source.trim(),
                dest = wordList.dest.trim(),
                words = sanitizedWords
            )
            
            gson.toJson(sanitizedList)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadDecksFromStorage() {
        binding.deckListContainer.removeAllViews()
        prefs.all.forEach { (name, json) ->
            val btn = MaterialButton(this).apply {
                text = name
                setOnClickListener {
                    val intent = Intent(this@MainActivity, FlashcardActivity::class.java)
                    intent.putExtra("JSON_DATA", json as String)
                    startActivity(intent)
                }
                setOnLongClickListener {
                    showDeckOptions(name)
                    true
                }
            }
            binding.deckListContainer.addView(btn)
        }
    }

    private fun showDeckOptions(name: String) {
        val options = arrayOf("Rename", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Options for $name")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(name)
                    1 -> showDeleteConfirmation(name)
                }
            }
            .show()
    }

    private fun showRenameDialog(oldName: String) {
        val nameInput = EditText(this).apply {
            hint = "New Name"
            setText(oldName)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Deck")
            .setView(nameInput)
            .setPositiveButton("Rename") { _, _ ->
                val newName = nameInput.text.toString()
                if (newName.isNotEmpty() && newName != oldName) {
                    val json = prefs.getString(oldName, null)
                    prefs.edit().remove(oldName).putString(newName, json).apply()
                    loadDecksFromStorage()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Deck")
            .setMessage("Are you sure you want to delete '$name'?")
            .setPositiveButton("Delete") { _, _ ->
                prefs.edit().remove(name).apply()
                loadDecksFromStorage()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}