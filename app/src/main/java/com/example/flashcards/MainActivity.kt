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

    private var exportJson: String? = null
    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let { saveJsonToUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnHelp.setOnClickListener { showHelp() }
        binding.btnAdd.setOnClickListener { showAddOptions() }
        loadDecksFromStorage()
    }

    override fun onResume() {
        super.onResume()
        loadDecksFromStorage()
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("JSON Format")
            .setMessage("File must be formatted as:\n\n{\n \"source language\": \"English\",\n \"destination language\": \"Finnish\",\n \"words\": {\"apple\": \"omena\", \n                   \"uncle\": \"setä\"}\n}")
            .setPositiveButton("OK", null).show()
    }

    private fun showAddOptions() {
        val options = arrayOf("Download from URL", "Select Local File", "Create Empty Wordlist")
        AlertDialog.Builder(this)
            .setTitle("Add Wordlist")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDownloadDialog()
                    1 -> pickFileLauncher.launch("application/json")
                    2 -> showCreateEmptyDeckDialog()
                }
            }
            .show()
    }

    private fun showCreateEmptyDeckDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
        }
        val nameInput = EditText(this).apply { hint = "Deck Name" }
        val sourceInput = EditText(this).apply { hint = "Source Language" }
        val destInput = EditText(this).apply { hint = "Destination Language" }
        layout.addView(nameInput)
        layout.addView(sourceInput)
        layout.addView(destInput)

        AlertDialog.Builder(this)
            .setTitle("Create Empty Wordlist")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val source = sourceInput.text.toString().trim()
                val dest = destInput.text.toString().trim()
                
                if (name.isEmpty() || source.isEmpty() || dest.isEmpty()) {
                    Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                } else if (prefs.contains(name)) {
                    Toast.makeText(this, "Deck already exists", Toast.LENGTH_SHORT).show()
                } else {
                    val emptyList = WordList(source, dest, emptyMap())
                    prefs.edit().putString(name, gson.toJson(emptyList)).apply()
                    loadDecksFromStorage()
                    Toast.makeText(this, "Deck '$name' created!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
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
        prefs.all.keys.sorted().forEach { name ->
            val btn = MaterialButton(this).apply {
                text = name
                setOnClickListener {
                    val currentJson = prefs.getString(name, null) ?: return@setOnClickListener
                    showOrderDialog(name, currentJson)
                }
                setOnLongClickListener {
                    showDeckOptions(name)
                    true
                }
            }
            binding.deckListContainer.addView(btn)
        }
    }

    private fun showOrderDialog(name: String, json: String) {
        val options = arrayOf("In Order", "Random")
        AlertDialog.Builder(this)
            .setTitle("Select Study Mode")
            .setItems(options) { _, which ->
                val intent = Intent(this, FlashcardActivity::class.java).apply {
                    putExtra("DECK_NAME", name)
                    putExtra("JSON_DATA", json)
                    putExtra("IS_RANDOM", which == 1)
                }
                startActivity(intent)
            }
            .show()
    }

    private fun showDeckOptions(name: String) {
        val options = arrayOf("Add Word", "Remove Word", "Search Word", "Rename", "Export (Save to file)", "Share", "Delete")
        AlertDialog.Builder(this)
            .setTitle("Options for $name")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddWordDialog(name)
                    1 -> showRemoveWordDialog(name)
                    2 -> showSearchWordDialog(name)
                    3 -> showRenameDialog(name)
                    4 -> {
                        exportJson = prefs.getString(name, null)
                        if (exportJson != null) {
                            createDocumentLauncher.launch("$name.json")
                        }
                    }
                    5 -> {
                        val json = prefs.getString(name, null)
                        if (json != null) {
                            shareDeck(name, json)
                        }
                    }
                    6 -> showDeleteConfirmation(name)
                }
            }
            .show()
    }

    private fun showSearchWordDialog(deckName: String) {
        val json = prefs.getString(deckName, null) ?: return
        val wordList = gson.fromJson(json, WordList::class.java)

        val input = EditText(this).apply {
            hint = "Search in source or destination language"
        }

        AlertDialog.Builder(this)
            .setTitle("Search in $deckName")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    showSearchResults(deckName, wordList, query)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSearchResults(deckName: String, wordList: WordList, query: String) {
        val words = wordList.words ?: emptyMap()
        val results = words.filter { 
            it.key.contains(query, ignoreCase = true) || it.value.contains(query, ignoreCase = true) 
        }

        if (results.isEmpty()) {
            Toast.makeText(this, "No matches found for '$query'", Toast.LENGTH_SHORT).show()
            return
        }

        val resultsList = results.entries.map { "${it.key} -> ${it.value}" }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("Search Results for '$query'")
            .setItems(resultsList, null)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showRemoveWordDialog(deckName: String) {
        val json = prefs.getString(deckName, null) ?: return
        val wordList = gson.fromJson(json, WordList::class.java)
        val sourceLang = wordList.source ?: "Key"
        val destLang = wordList.dest ?: "Value"

        val input = EditText(this).apply {
            hint = "Search $sourceLang or $destLang"
        }

        AlertDialog.Builder(this)
            .setTitle("Remove from $deckName")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isEmpty()) return@setPositiveButton

                val words = wordList.words ?: emptyMap()
                val matches = words.filter { it.key.equals(query, ignoreCase = true) || it.value.equals(query, ignoreCase = true) }

                if (matches.isNotEmpty()) {
                    showConfirmRemovalDialog(deckName, wordList, matches)
                } else {
                    showNotFoundDialog(deckName, query)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showConfirmRemovalDialog(deckName: String, wordList: WordList, matches: Map<String, String>) {
        val message = matches.entries.joinToString("\n") { "${it.key} -> ${it.value}" }
        AlertDialog.Builder(this)
            .setTitle("Confirm Removal")
            .setMessage("Are you sure you want to remove these items?\n\n$message")
            .setPositiveButton("Remove") { _, _ ->
                val updatedWords = wordList.words?.toMutableMap() ?: mutableMapOf()
                matches.keys.forEach { updatedWords.remove(it) }
                
                val updatedList = WordList(wordList.source, wordList.dest, updatedWords)
                val updatedJson = gson.toJson(updatedList)
                prefs.edit().putString(deckName, updatedJson).commit()
                loadDecksFromStorage()
                Toast.makeText(this, "Items removed from $deckName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNotFoundDialog(deckName: String, query: String) {
        AlertDialog.Builder(this)
            .setTitle("Not Found")
            .setMessage("'$query' was not found in $deckName. Would you like to add it instead?")
            .setPositiveButton("Add") { _, _ ->
                showAddWordDialog(deckName)
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showAddWordDialog(deckName: String) {
        val json = prefs.getString(deckName, null) ?: return
        val wordList = gson.fromJson(json, WordList::class.java)
        val sourceLang = wordList.source ?: "Key"
        val destLang = wordList.dest ?: "Value"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 20, 60, 0)
        }
        val keyInput = EditText(this).apply { hint = "Word ($sourceLang)" }
        val valueInput = EditText(this).apply { hint = "Translation ($destLang)" }
        layout.addView(keyInput)
        layout.addView(valueInput)

        AlertDialog.Builder(this)
            .setTitle("Add Word to $deckName")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val key = keyInput.text.toString().trim()
                var value = valueInput.text.toString().trim()
                
                if (key.isEmpty() || value.isEmpty()) {
                    Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Convert double quotes to single quotes in value
                value = value.replace("\"", "'")

                val words = wordList.words?.toMutableMap() ?: mutableMapOf()

                if (words.containsKey(key)) {
                    showOverwriteDialog(deckName, wordList, words, key, value)
                } else {
                    saveWordToDeck(deckName, wordList, words, key, value)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverwriteDialog(
        deckName: String,
        wordList: WordList,
        words: MutableMap<String, String>,
        key: String,
        value: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("Word Exists")
            .setMessage("The word '$key' already exists. Do you want to overwrite it or rename your entry?")
            .setPositiveButton("Overwrite") { _, _ ->
                saveWordToDeck(deckName, wordList, words, key, value)
            }
            .setNegativeButton("Rename") { _, _ ->
                showAddWordDialog(deckName) // Re-open add dialog
            }
            .show()
    }

    private fun saveWordToDeck(
        deckName: String,
        wordList: WordList,
        words: MutableMap<String, String>,
        key: String,
        value: String
    ) {
        words[key] = value
        val updatedList = WordList(wordList.source, wordList.dest, words)
        val updatedJson = gson.toJson(updatedList)

        // Lint check / Validation
        val sanitizedJson = validateAndSanitizeJson(updatedJson)
        if (sanitizedJson != null) {
            prefs.edit().putString(deckName, sanitizedJson).commit()
            loadDecksFromStorage()
            Toast.makeText(this, "Word added to $deckName", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to update deck: Invalid structure", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareDeck(name: String, json: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Flashcard Deck: $name")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, "Share Deck"))
    }

    private fun saveJsonToUri(uri: Uri) {
        val json = exportJson ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Deck exported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                exportJson = null
            }
        }
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