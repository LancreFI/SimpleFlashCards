package com.example.flashcards

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flashcards.databinding.ActivityDrawQuizBinding
import com.google.gson.Gson
import kotlin.math.hypot

class DrawQuizActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDrawQuizBinding
    private lateinit var characters: List<CharacterData>
    private var currentIndex = 0
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra("CHAR_DECK_JSON")
        val deckName = intent.getStringExtra("DECK_NAME")
        
        val deck = if (json != null) {
            gson.fromJson(json, CharacterDeck::class.java)
        } else if (deckName != null) {
            val charPrefs = getSharedPreferences("CharacterDecks", MODE_PRIVATE)
            val savedJson = charPrefs.getString(deckName, null)
            if (savedJson != null) {
                gson.fromJson(savedJson, CharacterDeck::class.java)
            } else null
        } else null

        if (deck == null) {
            finish()
            return
        }
        characters = if (intent.getBooleanExtra("IS_RANDOM", false)) {
            deck.characters.shuffled()
        } else {
            deck.characters
        }

        showCurrentCharacter()

        binding.btnHome.setOnClickListener {
            finish()
        }

        binding.btnClear.setOnClickListener {
            binding.drawingView.clear()
        }

        binding.sbThickness.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.drawingView.setStrokeWidth(progress.toFloat().coerceAtLeast(1f))
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnSubmit.setOnClickListener {
            checkDrawing()
        }

        binding.btnNext.setOnClickListener {
            currentIndex++
            showCurrentCharacter()
        }

        binding.btnRestart.setOnClickListener {
            currentIndex = 0
            if (intent.getBooleanExtra("IS_RANDOM", false)) {
                characters = characters.shuffled()
            }
            showCurrentCharacter()
        }

        binding.btnExit.setOnClickListener {
            finish()
        }
    }

    private fun showCurrentCharacter() {
        val char = characters[currentIndex]
        binding.tvTargetName.text = "Draw: ${char.name}"
        binding.drawingView.clear()
        binding.drawingView.setGhostStrokes(null)
        binding.drawingView.isDrawingEnabled = true
        binding.drawingView.setStrokeWidth(char.strokeWidth)
        binding.sbThickness.progress = char.strokeWidth.toInt()
        
        binding.btnClear.isEnabled = true
        binding.llQuizControls.visibility = View.VISIBLE
        binding.btnSubmit.visibility = View.VISIBLE
        binding.btnNext.visibility = View.GONE
        binding.llFinalOptions.visibility = View.GONE
    }

    private fun checkDrawing() {
        val userStrokes = binding.drawingView.getStrokes()
        val targetChar = characters[currentIndex]
        
        val isCorrect = compareStrokes(
            userStrokes, 
            targetChar.strokes, 
            targetChar.checkStrokeOrder,
            targetChar.checkStrokeDirection
        )
        
        binding.btnClear.isEnabled = false
        binding.drawingView.isDrawingEnabled = false

        if (isCorrect) {
            Toast.makeText(this, "Correct!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Incorrect. See original order.", Toast.LENGTH_LONG).show()
            binding.drawingView.clear()
            binding.drawingView.setGhostStrokes(targetChar.strokes, animate = true)
        }

        binding.btnSubmit.visibility = View.GONE
        if (currentIndex < characters.size - 1) {
            binding.btnNext.visibility = View.VISIBLE
        } else {
            binding.llFinalOptions.visibility = View.VISIBLE
        }
    }

    private fun compareStrokes(
        user: List<DrawingStroke>, 
        target: List<DrawingStroke>, 
        checkOrder: Boolean,
        checkDirection: Boolean
    ): Boolean {
        if (user.size != target.size) return false
        
        if (checkOrder) {
            for (i in user.indices) {
                if (!isStrokeSimilar(user[i], target[i], checkDirection)) return false
            }
            return true
        } else {
            val remainingTargetIndices = target.indices.toMutableList()
            for (uStroke in user) {
                var bestMatchIdx = -1
                for (tIdx in remainingTargetIndices) {
                    if (isStrokeSimilar(uStroke, target[tIdx], checkDirection)) {
                        bestMatchIdx = tIdx
                        break
                    }
                }
                if (bestMatchIdx != -1) {
                    remainingTargetIndices.remove(bestMatchIdx)
                } else {
                    return false
                }
            }
            return true
        }
    }

    private fun isStrokeSimilar(user: DrawingStroke, target: DrawingStroke, checkDirection: Boolean): Boolean {
        if (user.points.isEmpty() || target.points.isEmpty()) return false
        
        val resampledUser = resample(user, 32)
        val resampledTarget = resample(target, 32)
        
        val threshold = 150f // Average distance threshold in 1000x1000 space
        
        val scoreNormal = calculateAverageDistance(resampledUser, resampledTarget)
        if (checkDirection) {
            return scoreNormal < threshold
        }
        
        val scoreReversed = calculateAverageDistance(resampledUser, resampledTarget.reversed())
        return scoreNormal < threshold || scoreReversed < threshold
    }

    private fun resample(stroke: DrawingStroke, n: Int): List<DrawingPoint> {
        if (stroke.points.size < 2) return stroke.points
        val totalLen = stroke.points.zipWithNext { a, b -> distance(a, b) }.sum()
        if (totalLen <= 0f) {
            return List(n) { stroke.points[0] }
        }
        
        val resampled = mutableListOf<DrawingPoint>()
        val interval = totalLen / (n - 1)
        
        var currentDist = 0f
        var lastPoint = stroke.points[0]
        resampled.add(lastPoint)
        
        var i = 1
        while (resampled.size < n && i < stroke.points.size) {
            val nextPoint = stroke.points[i]
            val d = distance(lastPoint, nextPoint)
            if (currentDist + d >= interval) {
                val t = if (d == 0f) 0f else (interval - currentDist) / d
                val newPoint = DrawingPoint(
                    lastPoint.x + t * (nextPoint.x - lastPoint.x),
                    lastPoint.y + t * (nextPoint.y - lastPoint.y)
                )
                resampled.add(newPoint)
                lastPoint = newPoint
                currentDist = 0f
            } else {
                currentDist += d
                lastPoint = nextPoint
                i++
            }
        }
        while (resampled.size < n) resampled.add(stroke.points.last())
        return resampled
    }

    private fun calculateAverageDistance(p1: List<DrawingPoint>, p2: List<DrawingPoint>): Float {
        return p1.zip(p2).map { (a, b) -> distance(a, b) }.average().toFloat()
    }

    private fun distance(p1: DrawingPoint, p2: DrawingPoint): Float {
        return hypot(p1.x - p2.x, p1.y - p2.y)
    }
}
