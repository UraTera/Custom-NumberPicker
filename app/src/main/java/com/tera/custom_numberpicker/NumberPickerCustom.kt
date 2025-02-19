package com.tera.custom_numberpicker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

typealias OnChangeListener = (value: Int) -> Unit

class NumberPickerCustom(
    context: Context,
    attrs: AttributeSet?,
    defStyleRes: Int

) : View(context, attrs, defStyleRes) {

    constructor(context: Context, attributesSet: AttributeSet?) :
            this(context, attributesSet, 0)

    constructor(context: Context) : this(context, null)

    companion object {
        private const val MIN_VALUE = 0
        private const val MAX_VALUE = 100
        private const val TEXT_COLOR = -12763843
        private const val TEXT_COLOR_SEL = Color.BLACK
        private const val TEXT_SIZE = 52
        private const val TEXT_SIZE_SEL = 63
        private const val TEXT_OFFSET = 20
        private const val HINT_COLOR = Color.BLACK
        private const val HINT_SIZE = 47
        private const val HINT_OFFSET = 30f
        private const val DIVIDER_COLOR = Color.BLACK
        private const val DIVIDER_HEIGHT = 5
        private const val FADING_EXTENT = 7
        // Высота строки
        private const val ITEM_HEIGHT = 158
        // Интервал времени для прокрутки расстояния высоты одного элемента
        private const val HANDLER_INTERVAL_REFRESH = 32 // millisecond
        // Длительность прокрутки элемента
        private const val INTERVAL_REVISE = 300
        // Интервал обновления при длительном нажатии
        private const val INTERVAL_LONG_PRESS = 300
        // Длительность прокрутки при нажатии
        private const val DURATION_PRESS = 300
    }

    private val mPaintDivider = Paint()
    private val mPaintText = TextPaint()
    private val mPaintHint = Paint()

    private var mScroller = Scroller(context)
    private val mVelocityTracker: VelocityTracker = VelocityTracker.obtain()
    private val mMinVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    // Слушатель значения
    private var mListener: OnChangeListener? = null
    private var mMinShowIndex = -1
    private var mMaxShowIndex = -1
    private var mPrevPickedIndex = 0
    private var mScaledTouchSlop = 8

    private var mFriction = 1f
    private var mTextOffset = 0f
    private var mTextOffsetSel = 0f

    // true для установки текущей позиции, false для установки позиции на 0 (false)
    private var mCurrentItemIndexEffect = false

    // true, если NumberPicker инициализирован
    private var mHasInit = false

    // Ответ на изменение в главной теме
    private var mRespondChangeInMainThread = true
    private var mHandlerThread: HandlerThread? = null
    private var mHandlerInNewThread: Handler? = null
    private var mHandlerInMainThread: Handler? = null

    // Индекс содержимого первого показанного элемента
    private var mCurrDrawFirstItemIndex = 0

    // Y первого показанного элемента
    private var mCurrDrawFirstItemY = 0

    // Глобальный Y, соответствующий скроллеру
    private var mCurrDrawGlobalY = 0
    private var mInScrollingPickedOldValue = 0
    private var mInScrollingPickedNewValue = 0

    // Y последнего нажатия.
    private var mLastDownEventY = 0f

    // Y последнего события нажатия или перемещение.
    private var mLastDownOrMoveEventY = 0f

    // Изменить позицию на единицу при нажатии
    private var mChangeCurrentByLongPress: ChangeCurrentByLongPress? = null

    private var mDownYGlobal = 0f
    private var mDownY = 0f
    private var mCurrY = 0f
    private var mDividerTop = 0f    // Позиция верхнего разделителя.
    private var mDividerBottom = 0f // Позиция нижнего разделителя.
    private var mKeyPress = false
    private var mKeyScroll = false
    private var mMoveSpeed = 600

    private var mItemHeight = 0     // Высота строки
    private var mRectClip = RectF() // Область видимости текста
    private var mViewWidth = 165    // Ширина элемента
    private var mOffset = 20
    private var mOffDivider = 14    // Смещение разделителя по Y
    private var mHintHeight = 0f    // Высота подсказки
    private var mShownCount = 3     // Количество строк
    private var mValue = 0

    // Атрибуты
    private var mDisplayedValues = emptyArray<String>()
    private var mMin = 0
    private var mMax = 0
    private var mDividerColor = 0
    private var mDividerHeight = 0
    private var mDividerOffset = 0

    private var mFontFamily: Typeface? = null
    private var mFadingExtent = 0f

    private var mTextColor = 0
    private var mTextColorSel = 0
    private var mTextSize = 0f
    private var mTextSizeSel = 0f
    private var mTextOffsetHor = 0

    private var mHintText: String? = null
    private var mHintColor = 0
    private var mHintSize = 0f

    private var mShowRows5 = false
    private var mShowZeros = false
    private var mIntervalLongPress = 0L

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerCustom)

        mMin = a.getInt(R.styleable.NumberPickerCustom_np_minValue, MIN_VALUE)
        mMax = a.getInt(R.styleable.NumberPickerCustom_np_maxValue, MAX_VALUE)

        mShowZeros = a.getBoolean(R.styleable.NumberPickerCustom_np_showZeros, false)

        // Текст
        val char = a.getTextArray(R.styleable.NumberPickerCustom_np_textArray)
        mDisplayedValues = convertCharSequenceArrayToStringArray(char)

        mTextColorSel = a.getColor(R.styleable.NumberPickerCustom_np_textColorSel, TEXT_COLOR_SEL)
        mTextSizeSel =
            a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_textSizeSel, TEXT_SIZE_SEL)
                .toFloat()
        mTextColor = a.getColor(R.styleable.NumberPickerCustom_np_textColor, TEXT_COLOR)
        mTextSize =
            a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_textSize, TEXT_SIZE).toFloat()
        mTextOffsetHor =
            a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_textOffset, TEXT_OFFSET)

        // Разделитель
        mDividerHeight =
            a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_dividerHeight, DIVIDER_HEIGHT)
        mDividerColor = a.getColor(R.styleable.NumberPickerCustom_np_dividerColor, DIVIDER_COLOR)
        mDividerOffset = a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_dividerOffset, 0)

        // Подсказка
        mHintText = a.getString(R.styleable.NumberPickerCustom_np_hintText)
        mHintSize =
            a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_hintTextSize, HINT_SIZE)
                .toFloat()
        mHintColor = a.getColor(R.styleable.NumberPickerCustom_np_hintTextColor, HINT_COLOR)

        mFontFamily = a.getFont(R.styleable.NumberPickerCustom_np_fontFamily)
        mShowRows5 = a.getBoolean(R.styleable.NumberPickerCustom_np_showRows5, false)
        val fading = a.getInt(R.styleable.NumberPickerCustom_np_fadingExtent, FADING_EXTENT)
        mFadingExtent = fading / 10f

        mIntervalLongPress =
            a.getInt(R.styleable.NumberPickerCustom_np_intervalLongPress, INTERVAL_LONG_PRESS).toLong()

        a.recycle()

        initPaints()
        initParams()
        initHandler()
    }

    // Кисти
    private fun initPaints() {
        mPaintText.color = mTextColor
        mPaintText.isAntiAlias = true
        mPaintText.textAlign = Paint.Align.CENTER
        mPaintText.typeface = mFontFamily

        mPaintDivider.color = mDividerColor
        mPaintDivider.isAntiAlias = true
        mPaintDivider.style = Paint.Style.STROKE
        mPaintDivider.strokeWidth = mDividerHeight.toFloat()

        mPaintHint.color = mHintColor
        mPaintHint.isAntiAlias = true
        mPaintHint.textAlign = Paint.Align.CENTER
        mPaintHint.textSize = mHintSize
    }

    // Параметры
    private fun initParams() {
        mShownCount = if (mShowRows5)  5
        else  3

        mHintHeight = mHintSize + HINT_OFFSET
        mDividerOffset += mOffset

        mScaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
        if (mMinShowIndex == -1 || mMaxShowIndex == -1) {
            updateValueForInit()
        }

        mPaintText.textSize = mTextSizeSel
        mTextOffsetSel = getTextCenterYOffset(mPaintText.fontMetrics)
        mPaintText.textSize = mTextSize
        mTextOffset = getTextCenterYOffset(mPaintText.fontMetrics)
        updateWidth()
    }

    // Преобразовать CharSequenceArray в StringArray
    private fun convertCharSequenceArrayToStringArray(charSequences: Array<CharSequence>?): Array<String> {
        if (charSequences == null) {
            val arrayInt = (mMin..mMax).toList().toIntArray()
            val arrayStr = arrayInt.map { it.toString() }.toTypedArray()

            if (!mShowZeros) return arrayStr
            else {
                if (mMin > 9) return arrayStr
                val n = if (mMax < 9) mMax
                else 9 - mMin

                for (i in 0..n) {
                    arrayStr[i] = "0${i + mMin}"
                }
                return arrayStr
            }
        } else {
            val array = arrayOfNulls<String>(charSequences.size)
            for (i in charSequences.indices) {
                array[i] = charSequences[i].toString()
            }
            val arrayStr = array.map { it.toString() }.toTypedArray()
            mMax = array.size
            return arrayStr
        }
    }

    // Получить смещение текста по Y
    private fun getTextCenterYOffset(fontMetrics: Paint.FontMetrics?): Float {
        if (fontMetrics == null) {
            return 0f
        }
        return (abs((fontMetrics.top + fontMetrics.bottom).toDouble()) / 2).toFloat()
    }

    private fun initHandler() {
        mHandlerThread = HandlerThread("HandlerThread-For-Refreshing")
        mHandlerThread!!.start()

        mHandlerInNewThread = object : Handler(mHandlerThread!!.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    1 -> if (!mScroller.isFinished) {
                        mHandlerInNewThread!!.sendMessageDelayed(
                            getMsg(
                                1,
                                0,
                                0,
                                msg.obj
                            ), HANDLER_INTERVAL_REFRESH.toLong()
                        )
                    } else {
                        var duration = 0
                        val willPickIndex: Int
                        // Если скроллер закончился (не прокручивается), то отрегулируйте положение
                        if (mCurrDrawFirstItemY != 0) {
                            if (mCurrDrawFirstItemY < (-mItemHeight / 2)) {
                                // Отрегулируйте, чтобы прокрутить вверх
                                duration =
                                    (INTERVAL_REVISE.toFloat() * (mItemHeight + mCurrDrawFirstItemY) / mItemHeight).toInt()
                                mScroller.startScroll(
                                    0,
                                    mCurrDrawGlobalY,
                                    0,
                                    mItemHeight + mCurrDrawFirstItemY,
                                    duration * 3
                                )
                                willPickIndex =
                                    getWillPickIndexByGlobalY(mCurrDrawGlobalY + mItemHeight + mCurrDrawFirstItemY)
                            } else {
                                // Отрегулируйте, чтобы прокрутить вниз
                                duration =
                                    (INTERVAL_REVISE.toFloat() * (-mCurrDrawFirstItemY) / mItemHeight).toInt()
                                mScroller.startScroll(
                                    0,
                                    mCurrDrawGlobalY,
                                    0,
                                    mCurrDrawFirstItemY,
                                    duration * 3
                                )
                                willPickIndex =
                                    getWillPickIndexByGlobalY(mCurrDrawGlobalY + mCurrDrawFirstItemY)
                            }
                            postInvalidate()
                        } else {
                            // Получить индекс, который будет выбран
                            willPickIndex = getWillPickIndexByGlobalY(mCurrDrawGlobalY)
                        }
                        val changeMsg = getMsg(
                            2,
                            mPrevPickedIndex,
                            willPickIndex,
                            msg.obj
                        )
                        if (mRespondChangeInMainThread) {
                            mHandlerInMainThread!!.sendMessageDelayed(
                                changeMsg,
                                (duration * 2).toLong()
                            )
                        } else {
                            mHandlerInNewThread!!.sendMessageDelayed(
                                changeMsg,
                                (duration * 2).toLong()
                            )
                        }
                    }
                }
            }
        }
        mHandlerInMainThread = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                when (msg.what) {
                    3 -> requestLayout()
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mHandlerThread == null || !mHandlerThread!!.isAlive) {
            initHandler()
        }
    }

    // Обновить значение для Init
    private fun updateValueForInit() {
        if (mMinShowIndex == -1) {
            mMinShowIndex = 0
        }
        if (mMaxShowIndex == -1) {
            mMaxShowIndex = mDisplayedValues.size
        }
    }

    // Получить индекс по индексу строки
    private fun getIndexByRowIndex(indexF: Int, size: Int): Int {
        var index = indexF
        if (size <= 0) {
            return 0
        }
        index %= size
        if (index < 0) {
            index += size
        }
        return index
    }

    // Один размер переработки
    private val oneRecycleSize: Int
        get() = mMaxShowIndex - mMinShowIndex

    // Выбранный индекс относительно строки
    private var mPickedIndexRelativeToRow: Int
        get() {
            val willPickIndex: Int = if (mCurrDrawFirstItemY != 0) {
                if (mCurrDrawFirstItemY < (-mItemHeight / 2)) {
                    getWillPickIndexByGlobalY(mCurrDrawGlobalY + mItemHeight + mCurrDrawFirstItemY)
                } else {
                    getWillPickIndexByGlobalY(mCurrDrawGlobalY + mCurrDrawFirstItemY)
                }
            } else {
                getWillPickIndexByGlobalY(mCurrDrawGlobalY)
            }
            return willPickIndex
        }
        set(pickedIndexToRaw) {
            if (mMinShowIndex > -1) {
                if (pickedIndexToRaw in mMinShowIndex..mMaxShowIndex) {
                    mPrevPickedIndex = pickedIndexToRaw
                    correctPositionByDefaultValue(
                        pickedIndexToRaw - mMinShowIndex,
                    )
                    postInvalidate()
                }
            }
        }

    // Возвращает индекс относительно mDisplayedValues 0
    private fun getWillPickIndexByGlobalY(globalY: Int): Int {
        if (mItemHeight == 0) {
            return 0
        }
        val willPickIndex = globalY / mItemHeight + mShownCount / 2
        val index = getIndexByRowIndex(
            willPickIndex, oneRecycleSize
        )
        if (index in 0..<oneRecycleSize) {
            return index + mMinShowIndex
        } else {
            throw IllegalArgumentException(
                ("getWillPickIndexByGlobalY illegal index : " + index
                        + " getOneRecycleSize() : " + oneRecycleSize)
            )
        }
    }

    // Выбранный индекс по умолчанию относительно показанной части
    private fun correctPositionByDefaultValue(defaultPickedIndex: Int) {
        mCurrDrawFirstItemIndex = defaultPickedIndex - (mShownCount - 1) / 2
        mCurrDrawFirstItemIndex = getIndexByRowIndex(mCurrDrawFirstItemIndex, oneRecycleSize)

        if (mItemHeight == 0) {
            mCurrentItemIndexEffect = true
        } else {
            mCurrDrawGlobalY = mCurrDrawFirstItemIndex * mItemHeight
            mInScrollingPickedOldValue = mCurrDrawFirstItemIndex + mShownCount / 2
            mInScrollingPickedOldValue %= oneRecycleSize
            if (mInScrollingPickedOldValue < 0) {
                mInScrollingPickedOldValue += oneRecycleSize
            }
            mInScrollingPickedNewValue = mInScrollingPickedOldValue
            calculateFirstItemParameterByGlobalY()
        }
    }

    // Плавное изменение цвета текста
    private fun getEvaluateColor(fraction: Float, startColor: Int, endColor: Int): Int {
        val color = ColorUtils.blendARGB(startColor, endColor, fraction)
        return color
    }

    // Плавное изменение размера текста
    private fun getEvaluateSize(fraction: Float, startSize: Float, endSize: Float): Float {
        return startSize + (endSize - startSize) * fraction
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        mKeyScroll = false
        if (!isEnabled) {
            return false
        }
        mVelocityTracker.addMovement(event)
        mCurrY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mKeyPress = true
                mHandlerInNewThread!!.removeMessages(1)
                stopScrolling()
                scrollOnPress(event) // Прокрутить при нажатии
                mDownY = mCurrY
                mDownYGlobal = mCurrDrawGlobalY.toFloat()
                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val spanY = mDownY - mCurrY
                if (abs(spanY) > 20) {
                    mKeyPress = false
                    mCurrDrawGlobalY = (mDownYGlobal + spanY).toInt()
                    calculateFirstItemParameterByGlobalY() // 1
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeChangeCurrentByOneFromLongPress() // Удалить команду изменения текущего значения
                if (mKeyPress) {
                    selectScrollDirection(event.y.toInt()) // Выбрать направление прокрутки
                } else {
                    countVelocityTracker()
                }
                mHandlerInNewThread!!.sendMessageDelayed(getMsg(), 0)
            }
        }
        return true
    }

    // Остановить прокрутку
    private fun stopScrolling() {
        if (!mScroller.isFinished) {
            mScroller.startScroll(0, mScroller.currY, 0, 0, 1)
            mScroller.abortAnimation()
            postInvalidate()
        }
    }

    // Прокрутить при нажатии
    private fun scrollOnPress(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        if (!mKeyPress)
            return false

        val action = event.action and MotionEvent.ACTION_MASK
        if (action != MotionEvent.ACTION_DOWN) {
            return false
        }

        removeAllCallbacks()
        parent.requestDisallowInterceptTouchEvent(true)

        mLastDownEventY = event.y
        mLastDownOrMoveEventY = mLastDownEventY

        if (mLastDownEventY < mDividerTop)
            postChangeCurrentByOneFromLongPress(false)
        else if ((mLastDownEventY > mDividerBottom))
            postChangeCurrentByOneFromLongPress(true)
        return true
    }

    // Удалить все ожидающие обратные вызовы из очереди сообщений
    private fun removeAllCallbacks() {
        if (mChangeCurrentByLongPress != null) {
            removeCallbacks(mChangeCurrentByLongPress) // Штатный
        }
    }

    // Команда на изменение текущего значения на единицу
    private fun postChangeCurrentByOneFromLongPress(
        increment: Boolean,
        delayMillis: Long = ViewConfiguration.getLongPressTimeout().toLong()
    ) {
        if (!mKeyPress) return
        if (mChangeCurrentByLongPress == null) {
            mChangeCurrentByLongPress = ChangeCurrentByLongPress()
        } else {
            removeCallbacks(mChangeCurrentByLongPress)
        }
        mChangeCurrentByLongPress!!.setStep(increment)
        postDelayed(mChangeCurrentByLongPress, delayMillis)
    }

    // Команда изменения текущего значения при длительном нажатии на единицу
    private inner class ChangeCurrentByLongPress : Runnable {
        private var mIncrement = false

        fun setStep(increment: Boolean) { // Короткое нажатие
            mIncrement = increment
        }
        override fun run() {
            if (mKeyPress) {
                changeValueByOne(mIncrement)
                postDelayed(this, mIntervalLongPress)
            }
        }
    }

    // Изменить текущее значение на единицу
    private fun changeValueByOne(increment: Boolean) {
        val duration = DURATION_PRESS
        val dy = if (increment) mItemHeight
        else -mItemHeight
        mScroller.startScroll(0, mCurrDrawGlobalY, 0, dy, duration)
        postInvalidate()
    }

    // Остановить команду изменения текущего значения на единицу
    private fun removeChangeCurrentByOneFromLongPress() {
        if (mChangeCurrentByLongPress != null) {
            removeCallbacks(mChangeCurrentByLongPress)
        }
    }

    // Выбрать направление прокрутки
    private fun selectScrollDirection(eventY: Int){
        if (eventY < mDividerTop)
            changeValueByOne(false)
        else if (eventY > mDividerBottom)
            changeValueByOne(true)
    }

    // Переместить и получить выбранное значение
    private fun calculateFirstItemParameterByGlobalY() {
        mCurrDrawFirstItemIndex =
            floor((mCurrDrawGlobalY.toFloat() / mItemHeight).toDouble()).toInt()
        mCurrDrawFirstItemY = -(mCurrDrawGlobalY - mCurrDrawFirstItemIndex * mItemHeight)

        if (mListener != null && !mKeyScroll) {
            mInScrollingPickedNewValue = if (-mCurrDrawFirstItemY > mItemHeight / 2) {
                mCurrDrawFirstItemIndex + 1 + mShownCount / 2
            } else {
                mCurrDrawFirstItemIndex + mShownCount / 2
            }
            mInScrollingPickedNewValue %= oneRecycleSize
            if (mInScrollingPickedNewValue < 0) {
                mInScrollingPickedNewValue += oneRecycleSize
            }
            if (mInScrollingPickedOldValue != mInScrollingPickedNewValue) {
                mValue = mInScrollingPickedNewValue + mMin
                mListener!!.invoke(mValue)
            }
            mInScrollingPickedOldValue = mInScrollingPickedNewValue
        }
    }

    // Скорость трекера
    private fun countVelocityTracker() {
        mVelocityTracker.computeCurrentVelocity(mMoveSpeed) // 600
        val velocityY = (mVelocityTracker.yVelocity * mFriction).toInt()
        if (abs(velocityY.toDouble()) > mMinVelocity) {
            mScroller.fling(
                0,
                mCurrDrawGlobalY,
                0,
                -velocityY,
                Int.MIN_VALUE,
                Int.MAX_VALUE,
                Int.MIN_VALUE,
                Int.MAX_VALUE
            )
            invalidate()
        }
        mVelocityTracker.clear()
    }

    // Фиксировать прокрутку
    override fun computeScroll() {
        if (mItemHeight == 0) {
            return
        }
        if (mScroller.computeScrollOffset()) {
            mCurrDrawGlobalY = mScroller.currY
            calculateFirstItemParameterByGlobalY()
            postInvalidate()
        }
    }

    private fun getMsg(): Message {
        return getMsg(1, 0, 0, null)
    }

    private fun getMsg(what: Int, arg1: Int, arg2: Int, obj: Any?): Message {
        val msg = Message.obtain()
        msg.what = what
        msg.arg1 = arg1
        msg.arg2 = arg2
        msg.obj = obj
        return msg
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawText(canvas)
        drawDivider(canvas)
        drawHint(canvas)
//        drawAxis(canvas)
    }

    // Текст
    private fun drawText(canvas: Canvas) {
        val xc = width / 2f
        var index: Int
        var textColor: Int
        var textSize: Float
        var textOffsetY: Float // Смещение текста по Y
        var fraction = 0f

        for (i in 0 until mShownCount + 1) {
            // Y первого показанного элемента
            val y = (mCurrDrawFirstItemY + mItemHeight * i).toFloat() + mHintHeight

            index =
                getIndexByRowIndex(mCurrDrawFirstItemIndex + i, oneRecycleSize)

            when (i) {
                mShownCount / 2 -> { // Центр
                    fraction = (mItemHeight + mCurrDrawFirstItemY).toFloat() / mItemHeight
                    textColor = getEvaluateColor(fraction, mTextColor, mTextColorSel)
                    textSize = getEvaluateSize(fraction, mTextSize, mTextSizeSel)
                    textOffsetY = getEvaluateSize(fraction, mTextOffset, mTextOffsetSel)
                }

                mShownCount / 2 + 1 -> { // Нижний
                    textColor = getEvaluateColor(1 - fraction, mTextColor, mTextColorSel)
                    textSize = getEvaluateSize(1 - fraction, mTextSize, mTextSizeSel)
                    textOffsetY = getEvaluateSize(1 - fraction, mTextOffset, mTextOffsetSel)
                }

                else -> { // Верхний
                    textColor = mTextColor
                    textSize = mTextSize
                    textOffsetY = mTextOffset
                }
            }

            mPaintText.color = textColor
            mPaintText.textSize = textSize

            if (index in 0..<oneRecycleSize) {
                val strValue: String = mDisplayedValues[index + mMinShowIndex]

                val yt = y + mItemHeight / 2 + textOffsetY
                val fh = mHintHeight.toInt()

                // Рисовать текст
                if (fh != 0) { // Обрезать подсказку
                    canvas.save()
                    canvas.clipRect(mRectClip)
                    canvas.drawText(strValue, xc, yt, mPaintText)
                    canvas.restore()
                } else
                    canvas.drawText(strValue, xc, yt, mPaintText)
            }
        }
    }

    // Разделитель
    private fun drawDivider(canvas: Canvas) {
        val x1 = mDividerOffset.toFloat()
        val x2 = width - mDividerOffset.toFloat()
        var y = mDividerTop + mOffDivider
        canvas.drawLine(x1, y, x2, y, mPaintDivider)
        y = mDividerBottom - mOffDivider
        canvas.drawLine(x1, y, x2, y, mPaintDivider)
    }

    // Ось
    private fun drawAxis(canvas: Canvas) {
        mPaintDivider.color = Color.RED
        mPaintDivider.strokeWidth = 5f
        val x1 = 0f
        val x2 = width.toFloat()
        val dy = mItemHeight / 2f
        var y = dy + mHintHeight
        for (i in 0..<mShownCount) {
            canvas.drawLine(x1, y, x2, y, mPaintDivider)
            y += mItemHeight.toFloat()
        }
    }

    // Подсказка
    private fun drawHint(canvas: Canvas) {
        if (mHintText == null) {
            return
        }
        val x = width / 2f
        val y = mHintHeight - HINT_OFFSET
        canvas.drawText(mHintText!!, x, y, mPaintHint)
    }

    // Инициализировать затухающие края
    private fun initializeFadingEdges() {
        isVerticalFadingEdgeEnabled = true
        val len = (height - mHintHeight - mItemHeight) / 2
        setFadingEdgeLength(len.toInt())
    }

    override fun getTopFadingEdgeStrength(): Float {
        return mFadingExtent
    }

    override fun getBottomFadingEdgeStrength(): Float {
        return mFadingExtent
    }

    override fun isPaddingOffsetRequired(): Boolean {
        return true
    }

    override fun getTopPaddingOffset(): Int {
        return mHintHeight.toInt()
    }

    // Обновть ширину
    private fun updateWidth() {
        if (mDisplayedValues.isEmpty()) {
            return
        }
        val array = mDisplayedValues
        var maxWidth = 0
        for (item in array) {
            val itemWidth = getTextWidth(item)
            maxWidth = max(itemWidth.toDouble(), maxWidth.toDouble()).toInt()
        }
        if (maxWidth > mViewWidth) {
            mViewWidth = maxWidth + mTextOffsetHor
            invalidate()
        }
    }

    // Получить ширину текста
    private fun getTextWidth(text: String): Int {
        mPaintText.textSize = mTextSizeSel
        val rect = Rect()
        mPaintText.getTextBounds(text, 0, text.length, rect)
        return rect.width()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        mItemHeight = ITEM_HEIGHT

        if (mHintText == null) mHintHeight = 0f

        val wC = mViewWidth + (mOffset * 2)
        val hC = mItemHeight * mShownCount + mHintHeight.toInt()

        setMeasuredDimension(
            resolveSize(wC, widthMeasureSpec),
            resolveSize(hC, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        mViewWidth = w - mOffset * 2
        mItemHeight = (h - mHintHeight.toInt()) / mShownCount

        mRectClip = RectF(0f, mHintHeight, width.toFloat(), h.toFloat())

        // Положение разделителей
        mDividerTop = (h - mItemHeight + mHintHeight) / 2
        mDividerBottom = mDividerTop + mItemHeight

        // Затухание краев
        initializeFadingEdges()

        // Вычислить индекс первого видимого элемента
        var defaultValue = 0
        if (oneRecycleSize > 1) {
            defaultValue = if (mHasInit) {
                value - mMin
            } else if (mCurrentItemIndexEffect) {
                mCurrDrawFirstItemIndex + (mShownCount - 1) / 2
            } else {
                0
            }
        }
        correctPositionByDefaultValue(defaultValue)
        mHasInit = true
    }

    // Слушатель изменения значения
    fun setOnChangeListener(listener: OnChangeListener) {
        mListener = listener
    }

    var displayedValues: Array<String>
        get() = mDisplayedValues
        set(value) {
            mMax = value.size
            mDisplayedValues = value
            mMaxShowIndex = mMax
            updateWidth()
        }

    var maxValue: Int
        get() = mMax
        set(value) {
            mMax = value
            mMaxShowIndex = mMax - mMin + mMinShowIndex + 1
        }

    var value: Int
        get() = mPickedIndexRelativeToRow + mMin
        set(value) {
            mPickedIndexRelativeToRow = value - mMin
        }

    var valueString: String
        get() = mDisplayedValues[mValue - mMin]
        set(value) {} // For Java compatibility

    // Перемещение
    var scroll: Int = 0
        set(value) {
            field = value
            mKeyScroll = true
            scrollTo(value)
        }

    private fun scrollTo(dy: Int) {
        mScroller.forceFinished(true)
        mScroller.startScroll(0, mCurrDrawGlobalY, 0, dy, 10)
        invalidate()
    }

}