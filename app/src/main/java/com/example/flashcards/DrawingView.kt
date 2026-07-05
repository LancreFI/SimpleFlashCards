package com.example.flashcards

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val ghostPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 10f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    fun setStrokeWidth(width: Float) {
        paint.strokeWidth = width
        ghostPaint.strokeWidth = width
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (width < height) width else height
        setMeasuredDimension(size, size)
    }

    private var currentPath = Path()
    private var currentStroke = mutableListOf<DrawingPoint>()
    private val strokes = mutableListOf<DrawingStroke>()
    private val redoStrokes = mutableListOf<DrawingStroke>()
    
    // For displaying the original character in quiz mode
    private var ghostStrokes: List<DrawingStroke>? = null
    private var animatedStrokeIndex = -1
    private var animatedPointIndex = -1
    private var isAnimating = false
    private var cachedNormalizedStrokes: List<DrawingStroke>? = null

    var isDrawingEnabled = true
        set(value) {
            field = value
            if (!value) {
                cachedNormalizedStrokes = normalizeStrokes(strokes)
            } else {
                cachedNormalizedStrokes = null
            }
            invalidate()
        }

    fun setGhostStrokes(strokes: List<DrawingStroke>?, animate: Boolean = false) {
        ghostStrokes = strokes?.let { normalizeStrokes(it) }
        if (animate && ghostStrokes != null) {
            startAnimation()
        } else {
            isAnimating = false
            invalidate()
        }
    }

    private fun startAnimation() {
        isAnimating = true
        animatedStrokeIndex = 0
        animatedPointIndex = 0
        animateNextStep()
    }

    private fun animateNextStep() {
        if (!isAnimating || ghostStrokes == null) return

        val strokes = ghostStrokes!!
        if (animatedStrokeIndex < strokes.size) {
            val stroke = strokes[animatedStrokeIndex]
            if (animatedPointIndex < stroke.points.size) {
                animatedPointIndex++
                invalidate()
                postDelayed({ animateNextStep() }, 15) // Adjust speed here
            } else {
                animatedStrokeIndex++
                animatedPointIndex = 0
                postDelayed({ animateNextStep() }, 100) // Pause between strokes
            }
        } else {
            isAnimating = false
        }
    }

    fun normalizeStrokes(inputStrokes: List<DrawingStroke>): List<DrawingStroke> {
        if (inputStrokes.isEmpty()) return emptyList()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var foundPoint = false
        for (stroke in inputStrokes) {
            for (point in stroke.points) {
                if (point.x < minX) minX = point.x
                if (point.x > maxX) maxX = point.x
                if (point.y < minY) minY = point.y
                if (point.y > maxY) maxY = point.y
                foundPoint = true
            }
        }
        if (!foundPoint) return inputStrokes
        val drawWidth = maxX - minX
        val drawHeight = maxY - minY
        val targetArea = 950f // Use 95% of the area to allow for stroke width and margin
        val scale = Math.min(targetArea / drawWidth.coerceAtLeast(1f), targetArea / drawHeight.coerceAtLeast(1f))
        val centerX = (maxX + minX) / 2f
        val centerY = (maxY + minY) / 2f
        return inputStrokes.map { stroke ->
            DrawingStroke(stroke.points.map { point ->
                DrawingPoint(
                    500f + (point.x - centerX) * scale,
                    500f + (point.y - centerY) * scale
                )
            })
        }
    }

    fun getStrokes(): List<DrawingStroke> = normalizeStrokes(strokes)

    fun setStrokes(inputStrokes: List<DrawingStroke>) {
        val size = width.toFloat()
        if (size <= 0) {
            // If view not measured yet, post it
            post { setStrokes(inputStrokes) }
            return
        }
        val scale = size / 1000f
        strokes.clear()
        redoStrokes.clear()
        strokes.addAll(inputStrokes.map { stroke ->
            DrawingStroke(stroke.points.map { DrawingPoint(it.x * scale, it.y * scale) })
        })
        currentPath.reset()
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            redoStrokes.add(strokes.removeAt(strokes.size - 1))
            if (cachedNormalizedStrokes != null) cachedNormalizedStrokes = normalizeStrokes(strokes)
            invalidate()
        }
    }

    fun redo() {
        if (redoStrokes.isNotEmpty()) {
            strokes.add(redoStrokes.removeAt(redoStrokes.size - 1))
            if (cachedNormalizedStrokes != null) cachedNormalizedStrokes = normalizeStrokes(strokes)
            invalidate()
        }
    }

    fun clear() {
        strokes.clear()
        redoStrokes.clear()
        currentPath.reset()
        isAnimating = false
        cachedNormalizedStrokes = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val size = width.toFloat()
        val scale = size / 1000f

        // Draw ghost character if available (normalized 0-1000)
        ghostStrokes?.let { gStrokes ->
            for (i in gStrokes.indices) {
                if (isAnimating && i > animatedStrokeIndex) break
                
                val stroke = gStrokes[i]
                val path = Path()
                val pointsToDraw = if (isAnimating && i == animatedStrokeIndex) {
                    stroke.points.take(animatedPointIndex)
                } else {
                    stroke.points
                }

                if (pointsToDraw.isNotEmpty()) {
                    path.moveTo(pointsToDraw[0].x * scale, pointsToDraw[0].y * scale)
                    for (j in 1 until pointsToDraw.size) {
                        path.lineTo(pointsToDraw[j].x * scale, pointsToDraw[j].y * scale)
                    }
                }
                canvas.drawPath(path, ghostPaint)
            }
        }

        // Draw existing strokes
        val strokesToDraw = if (isDrawingEnabled) strokes else cachedNormalizedStrokes ?: emptyList()
        val s = if (isDrawingEnabled) 1f else scale

        strokesToDraw.forEach { stroke ->
            val path = Path()
            if (stroke.points.isNotEmpty()) {
                path.moveTo(stroke.points[0].x * s, stroke.points[0].y * s)
                for (i in 1 until stroke.points.size) {
                    path.lineTo(stroke.points[i].x * s, stroke.points[i].y * s)
                }
            }
            canvas.drawPath(path, paint)
        }

        // Draw current path
        if (isDrawingEnabled) {
            canvas.drawPath(currentPath, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isDrawingEnabled || isAnimating) return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                redoStrokes.clear()
                currentPath.moveTo(x, y)
                currentStroke = mutableListOf(DrawingPoint(x, y))
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
                currentStroke.add(DrawingPoint(x, y))
            }
            MotionEvent.ACTION_UP -> {
                strokes.add(DrawingStroke(currentStroke))
                currentPath.lineTo(x, y)
                currentStroke.add(DrawingPoint(x, y))
                // Reset path but keep the strokes in the list for redraw
                currentPath = Path() 
            }
        }
        invalidate()
        return true
    }
}
