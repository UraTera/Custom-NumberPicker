package com.tera.custom_numberpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.Scroller
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import kotlin.math.floor

typealias OnValueListener = (picker: NumberPickerCustom, value: Int) -> Unit

class NumberPickerCustom(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int

) : View(context, attrs, defStyleAttr, defStyleRes) {

    constructor(context: Context, attributesSet: AttributeSet?, defStyleAttr: Int) :
            this(context, attributesSet, defStyleAttr, 0)

    constructor(context: Context, attributesSet: AttributeSet?) :
            this(context, attributesSet, 0)

    constructor(context: Context) : this(context, null)

    companion object {
        // Значения по умолчанию
        private const val MIN = 0
        private const val MAX = 21

        // Цвет текста по умолчанию для невыбранного элемента
        private const val TEXT_COLOR_NORMAL = -12698050
        private const val TEXT_COLOR_NORMAL_F = -6250334

        // Цвет текста по умолчанию для выбранного элемента
        private const val TEXT_COLOR_SELECTED = Color.BLACK

        // Высота строки. Для onMeasure
        private const val ITEM_HEIGHT = 158

        // Цвет разделителя
        private const val DIVIDER_COLOR = -12245513

        // Высота разделителя
        private const val DIVIDER_HEIGHT = 5

        // Размер текста для не выбранного элемента
        private const val TEXT_SIZE_NORMAL = 55

        // Размер текста для выбранного элемента
        private const val TEXT_SIZE_SELECTED = 60

        // Размер текста подсказки
        private const val TEXT_SIZE_HINT = 40

        // Отступ текста подсказки от низа
        private const val HINT_OFFSET = 30f

        // Аргумент сообщения what для обновления текущего состояния, используемый mHandler
        private const val HANDLER_WHAT_REFRESH = 1

        // Сообщения что для ответа значение изменилось событие, используется mHandler
        private const val HANDLER_WHAT_LISTENER_VALUE_CHANGED = 2

        // mСообщения что для запроса макета, используемый mHandlerInMainThread
        private const val HANDLER_WHAT_REQUEST_LAYOUT = 3

        // Интервал времени для прокрутки расстояния высоты одного элемента
        private const val HANDLER_INTERVAL_REFRESH = 32 //millisecond

        // Длительность прокрутки элемента по умолчанию
        private const val INTERVAL_REVISE_DURATION = 300

        // Минимальная длительность при прокрутке от одного значения к другому
        private const val MIN_SCROLL_BY_INDEX_DURATION = INTERVAL_REVISE_DURATION

        // Максимальная длительность при прокрутке от одного значения к другому
        private const val MAX_SCROLL_BY_INDEX_DURATION = INTERVAL_REVISE_DURATION * 2

        // Колесо селектора прокрутки
        private const val WRAP_SELECTOR_WHEEL = true
    }

    // Кисти
    private val mPaintDivider = Paint()
    private val mPaintText = TextPaint()
    private val mPaintHint = Paint()
    private val mPaintRect = Paint()

    private var mMinShowIndex = -1
    private var mMaxShowIndex = -1
    private var mPrevPickedIndex = 0
    private var mMiniVelocityFling = 150
    private var mScaledTouchSlop = 8

    private var mFriction = 1f
    private var mTextSizeNormalCenterYOffset = 0f
    private var mTextSizeSelectedCenterYOffset = 0f
    private var mTextSizeHintCenterYOffset = 0f

    // true для переноса отображаемых значений
    private var mWrapSelectorWheel = WRAP_SELECTOR_WHEEL

    // true для установки текущей позиции, false для установки позиции на 0 (false)
    private var mCurrentItemIndexEffect = false

    // true, если NumberPicker инициализирован
    private var mHasInit = false

    // Если количество отображаемых значений меньше количества показов, то это значение будет ложным (true).
    private var mWrapSelectorWheelCheck = true

    // Если вы хотите переключиться из режима переноса в линейный режим при прокрутке, то это значение будет true.
    private var mPendingWrapToLinear = false

    // Ответ на изменение в главной теме
    private var mRespondChangeInMainThread = true

    private var mScroller: Scroller? = null
    private var mVelocityTracker: VelocityTracker? = null

    private var mHandlerThread: HandlerThread? = null
    private var mHandlerInNewThread: Handler? = null
    private var mHandlerInMainThread: Handler? = null

    // Слушатель изменения значений при отпускании
    interface OnValueChangeUpListener {
        fun onValueChange(picker: NumberPickerCustom, value: Int)
    }

    // Слушатель изменения значений относительно необработанного
    interface OnValueChangeListenerRelativeToRaw {
        fun onValueChangeRelativeToRaw(
            picker: NumberPickerCustom, oldPickedIndex: Int, newPickedIndex: Int,
            displayedValues: Array<String>
        )
    }

    // Слушатель прокрутки
    interface OnScrollListener {
        fun onScrollStateChange(view: NumberPickerCustom, scrollState: Int)

        companion object {
            const val SCROLL_STATE_IDLE: Int = 0
            const val SCROLL_STATE_TOUCH_SCROLL: Int = 1
            const val SCROLL_STATE_FLING: Int = 2
        }
    }

    private var mOnValueChangeListenerRaw: OnValueChangeListenerRelativeToRaw? = null
    private var mOnValueChangeListener: OnValueChangeUpListener? = null
    private var mOnScrollListener: OnScrollListener? = null

    // Слушатель значения
    private var mListener: OnValueListener? = null

    // Текущее состояние прокрутки
    private var mScrollState = OnScrollListener.SCROLL_STATE_IDLE

    // Индекс содержимого первого показанного элемента
    private var mCurrDrawFirstItemIndex = 0

    // Y первого показанного элемента
    private var mCurrDrawFirstItemY = 0

    // Глобальный Y, соответствующий скроллеру
    private var mCurrDrawGlobalY = 0

    private var mInScrollingPickedOldValue = 0
    private var mInScrollingPickedNewValue = 0
    private var mNotWrapLimitYTop = 0
    private var mNotWrapLimitYBottom = 0

    private var downYGlobal = 0f
    private var downY = 0f
    private var currY = 0f

    private var mItemHeight = 0     // Высота элемента

    private val mRectView = RectF() // Область компонента
    private var mRectClip = RectF() // Область видимости текста
    private var mWidthItem = 165    // Ширина элемента
    private var mOffset = 20
    private var mOffDivider = 14    // Смещение разделителя по Y
    private var mFieldHint = 0f     // Поле подсказки
    private var mShownCount = 3     // Количество строк

    // Атрибуты
    private var mDisplayedValues: Array<String>
    private var mTextHint: String? = null // Текст подсказки
    private var mMin = 0
    private var mMax = 0

    private var mTextColorNormal = 0
    private var mTextColorSelected = 0
    private var mTextColorHint = 0
    private var mTextSizeNormal = 0
    private var mTextSizeSelected = 0
    private var mTextSizeHint = 0

    private var mDividerColor = 0
    private var mDividerHeight = 0
    private var mDividerOffset = 0

    private var mShowRows5 = false

    // Атрибуты
    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.NumberPickerCustom)

        mMin = a.getInt(R.styleable.NumberPickerCustom_np_min, 0)
        mMax = a.getInt(R.styleable.NumberPickerCustom_np_max, 0)

        val char = a.getTextArray(R.styleable.NumberPickerCustom_np_textArray)
        mDisplayedValues = convertCharSequenceArrayToStringArray(char)
        // Текст
        mTextSizeNormal = a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_textSizeNormal, TEXT_SIZE_NORMAL)
        mTextSizeSelected = a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_textSizeSelected,
            TEXT_SIZE_SELECTED
        )
        mTextColorNormal = a.getColor(R.styleable.NumberPickerCustom_np_textColorNormal, TEXT_COLOR_NORMAL)
        mTextColorSelected =
            a.getColor(R.styleable.NumberPickerCustom_np_textColorSelected, TEXT_COLOR_SELECTED)

        // Разделитель
        mDividerHeight =
            a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_dividerHeight, DIVIDER_HEIGHT)
        mDividerColor = a.getColor(R.styleable.NumberPickerCustom_np_dividerColor, DIVIDER_COLOR)
        mDividerOffset = a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_dividerOffset, 0)

        // Подсказка
        mTextHint = a.getString(R.styleable.NumberPickerCustom_np_textHint)
        mTextSizeHint =
            a.getDimensionPixelSize(R.styleable.NumberPickerCustom_np_textSizeHint, TEXT_SIZE_HINT)
        mTextColorHint = a.getColor(R.styleable.NumberPickerCustom_np_textColorHint, TEXT_COLOR_NORMAL)

        // Число строк
        mShowRows5 = a.getBoolean(R.styleable.NumberPickerCustom_np_showRows5, false)

        if (mTextHint != null)
            mTextColorNormal = a.getColor(R.styleable.NumberPickerCustom_np_textColorNormal, TEXT_COLOR_NORMAL_F)
        else
            mTextColorNormal = a.getColor(R.styleable.NumberPickerCustom_np_textColorNormal, TEXT_COLOR_NORMAL)

        a.recycle()

        initPaints()
        initParams()
        initHandler()
        setRows()
    }

    // Кисти
    private fun initPaints() {
        mPaintDivider.color = mDividerColor
        mPaintDivider.isAntiAlias = true
        mPaintDivider.style = Paint.Style.STROKE
        mPaintDivider.strokeWidth = mDividerHeight.toFloat()

        mPaintText.color = mTextColorNormal
        mPaintText.isAntiAlias = true
        mPaintText.textAlign = Paint.Align.CENTER

        mPaintHint.color = mTextColorHint
        mPaintHint.isAntiAlias = true
        mPaintHint.textAlign = Paint.Align.CENTER
        mPaintHint.textSize = mTextSizeHint.toFloat()
    }

    // Параметры
    private fun initParams() {
        mScroller = Scroller(context)
        mMiniVelocityFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
        mScaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

        if (mMinShowIndex == -1 || mMaxShowIndex == -1) {
            updateValueForInit()
        }

        mFieldHint = mTextSizeHint.toFloat() + HINT_OFFSET
        mDividerOffset += mOffset

        if (mTextHint != null) {
            isVerticalFadingEdgeEnabled = false
        } else {
            isVerticalFadingEdgeEnabled = true
        }

    }

    // Преобразовать CharSequenceArray в StringArray
    private fun convertCharSequenceArrayToStringArray(charSequences: Array<CharSequence>?): Array<String> {
        if (charSequences == null) {
            if (mMin == 0 && mMax == 0) {
                val arrayInt = getDisplayedValues(MIN, MAX)
                val arrayStr = arrayInt.map { it }.toTypedArray()
                mMax = arrayInt.size
                return arrayStr
            } else {
                return getDisplayedValues(mMin, mMax)
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

    // Получить массив строк
    private fun getDisplayedValues(min: Int, max: Int): Array<String> {
        val arrayInt = (min..max).toList().toIntArray()      // IntArray
        val arrayStr = arrayInt.map { it.toString() }.toTypedArray()
        return arrayStr
    }

    private fun initHandler() {
        mHandlerThread = HandlerThread("HandlerThread-For-Refreshing")
        mHandlerThread!!.start()

        mHandlerInNewThread = object : Handler(mHandlerThread!!.looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    HANDLER_WHAT_REFRESH -> if (!mScroller!!.isFinished) {
                        if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                            onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                        }
                        mHandlerInNewThread!!.sendMessageDelayed(
                            getMsg(
                                HANDLER_WHAT_REFRESH,
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
                            if (mScrollState == OnScrollListener.SCROLL_STATE_IDLE) {
                                onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
                            }
                            if (mCurrDrawFirstItemY < (-mItemHeight / 2)) {
                                // Отрегулируйте, чтобы прокрутить вверх
                                duration =
                                    (INTERVAL_REVISE_DURATION.toFloat() * (mItemHeight + mCurrDrawFirstItemY) / mItemHeight).toInt()
                                mScroller!!.startScroll(
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
                                    (INTERVAL_REVISE_DURATION.toFloat() * (-mCurrDrawFirstItemY) / mItemHeight).toInt()
                                mScroller!!.startScroll(
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
                            onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE)
                            // Получить индекс, который будет выбран
                            willPickIndex = getWillPickIndexByGlobalY(mCurrDrawGlobalY)
                        }
                        val changeMsg = getMsg(
                            HANDLER_WHAT_LISTENER_VALUE_CHANGED,
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

                    HANDLER_WHAT_LISTENER_VALUE_CHANGED -> respondPickedValueChanged(
                        msg.arg1,
                        msg.arg2,
                        msg.obj
                    )
                }
            }
        }
        mHandlerInMainThread = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                when (msg.what) {
                    HANDLER_WHAT_REQUEST_LAYOUT -> requestLayout()
                    HANDLER_WHAT_LISTENER_VALUE_CHANGED -> respondPickedValueChanged(
                        msg.arg1,
                        msg.arg2,
                        msg.obj
                    )
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

    private val oneRecycleSize: Int
        get() = mMaxShowIndex - mMinShowIndex

    // Используется обработчиками для ответа на обратные вызовы изменений
    private fun respondPickedValueChanged(oldVal: Int, newVal: Int, respondChange: Any?) {
        onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE)
        if (oldVal != newVal) {
            if (respondChange == null || respondChange !is Boolean || respondChange) {
                if (mOnValueChangeListener != null) {
                    mOnValueChangeListener!!.onValueChange(
                        this@NumberPickerCustom,
                        newVal + mMin
                    )
                }
                if (mOnValueChangeListenerRaw != null) {
                    mOnValueChangeListenerRaw!!.onValueChangeRelativeToRaw(
                        this@NumberPickerCustom,
                        oldVal,
                        newVal,
                        mDisplayedValues
                    )
                }
            }
        }
        mPrevPickedIndex = newVal
        if (mPendingWrapToLinear) {
            mPendingWrapToLinear = false
            internalSetWrapToLinear()
        }
    }

    private fun scrollByIndexSmoothly(deltaIndex: Int) {
        scrollByIndexSmoothly(deltaIndex, true)
    }

    // Плавно прокручивать по индексу
    private fun scrollByIndexSmoothly(deltaIndexF: Int, needRespond: Boolean) {
        var deltaIndex = deltaIndexF
        if (!(mWrapSelectorWheel && mWrapSelectorWheelCheck)) {
            val willPickRawIndex = mPickedIndexRelativeToRaw
            if (willPickRawIndex + deltaIndex > mMaxShowIndex) {
                deltaIndex = mMaxShowIndex - willPickRawIndex
            } else if (willPickRawIndex + deltaIndex < mMinShowIndex) {
                deltaIndex = mMinShowIndex - willPickRawIndex
            }
        }
        var duration: Int
        var dy: Int

        if (mCurrDrawFirstItemY < (-mItemHeight / 2)) {
            // Прокрутить вверх на расстояние менее mItemHeight
            dy = mItemHeight + mCurrDrawFirstItemY
            duration =
                (INTERVAL_REVISE_DURATION.toFloat() * (mItemHeight + mCurrDrawFirstItemY) / mItemHeight).toInt()
            duration = if (deltaIndex < 0) {
                -duration - deltaIndex * INTERVAL_REVISE_DURATION
            } else {
                duration + deltaIndex * INTERVAL_REVISE_DURATION
            }
        } else {
            // Прокрутите вниз на расстояние менее mItemHeight
            dy = mCurrDrawFirstItemY
            duration =
                (INTERVAL_REVISE_DURATION.toFloat() * (-mCurrDrawFirstItemY) / mItemHeight).toInt()
            duration = if (deltaIndex < 0) {
                duration - deltaIndex * INTERVAL_REVISE_DURATION
            } else {
                duration + deltaIndex * INTERVAL_REVISE_DURATION
            }
        }
        dy += deltaIndex * mItemHeight
        if (duration < MIN_SCROLL_BY_INDEX_DURATION) {
            duration = MIN_SCROLL_BY_INDEX_DURATION
        }
        if (duration > MAX_SCROLL_BY_INDEX_DURATION) {
            duration = MAX_SCROLL_BY_INDEX_DURATION
        }
        mScroller!!.startScroll(0, mCurrDrawGlobalY, 0, dy, duration)
        if (needRespond) {
            mHandlerInNewThread!!.sendMessageDelayed(
                getMsg(HANDLER_WHAT_REFRESH),
                (duration / 4).toLong()
            )
        } else {
            mHandlerInNewThread!!.sendMessageDelayed(
                getMsg(
                    HANDLER_WHAT_REFRESH,
                    0,
                    0,
                    needRespond
                ), (duration / 4).toLong()
            )
        }
        postInvalidate()
    }

    private var mPickedIndexRelativeToRaw: Int
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
                if (mMinShowIndex <= pickedIndexToRaw && pickedIndexToRaw <= mMaxShowIndex) {
                    mPrevPickedIndex = pickedIndexToRaw
                    correctPositionByDefaultValue(
                        pickedIndexToRaw - mMinShowIndex,
                        mWrapSelectorWheel && mWrapSelectorWheelCheck
                    )
                    postInvalidate()
                }
            }
        }

    private fun onScrollStateChange(scrollState: Int) {
        if (mScrollState == scrollState) {
            return
        }
        mScrollState = scrollState
        if (mOnScrollListener != null) {
            mOnScrollListener!!.onScrollStateChange(this, scrollState)
        }
    }

    // Возвращает индекс относительно mDisplayedValues 0
    private fun getWillPickIndexByGlobalY(globalY: Int): Int {
        if (mItemHeight == 0) {
            return 0
        }
        val willPickIndex = globalY / mItemHeight + mShownCount / 2
        val index = getIndexByRawIndex(
            willPickIndex,
            oneRecycleSize, mWrapSelectorWheel && mWrapSelectorWheelCheck
        )
        if (index in 0..<oneRecycleSize) {
            return index + mMinShowIndex
        } else {
            throw IllegalArgumentException(
                ("getWillPickIndexByGlobalY illegal index : " + index
                        + " getOneRecycleSize() : " + oneRecycleSize + " mWrapSelectorWheel : " + mWrapSelectorWheel)
            )
        }
    }

    private fun getIndexByRawIndex(indexF: Int, size: Int, wrap: Boolean): Int {
        var index = indexF
        if (size <= 0) {
            return 0
        }
        if (wrap) {
            index %= size
            if (index < 0) {
                index += size
            }
            return index
        } else {
            return index
        }
    }

    // Внутренний набор обернуть в линейный
    private fun internalSetWrapToLinear() {
        val rawIndex = mPickedIndexRelativeToRaw
        correctPositionByDefaultValue(rawIndex - mMinShowIndex, false)
        mWrapSelectorWheel = false
        postInvalidate()
    }

    private fun updateFontAttr() {
        if (mTextSizeNormal > mItemHeight) {
            mTextSizeNormal = mItemHeight
        }
        if (mTextSizeSelected > mItemHeight) {
            mTextSizeSelected = mItemHeight
        }

        mPaintHint.textSize = mTextSizeHint.toFloat()
        mTextSizeHintCenterYOffset = getTextCenterYOffset(mPaintHint.fontMetrics)

        mPaintText.textSize = mTextSizeSelected.toFloat()
        mTextSizeSelectedCenterYOffset = getTextCenterYOffset(mPaintText.fontMetrics)
        mPaintText.textSize = mTextSizeNormal.toFloat()
        mTextSizeNormalCenterYOffset = getTextCenterYOffset(mPaintText.fontMetrics)
    }

    private fun updateNotWrapYLimit() {
        mNotWrapLimitYTop = 0
        mNotWrapLimitYBottom = -mShownCount * mItemHeight

        mNotWrapLimitYTop = (oneRecycleSize - mShownCount / 2 - 1) * mItemHeight
        mNotWrapLimitYBottom = -(mShownCount / 2) * mItemHeight
    }

    private fun limitY(currDrawGlobalYPreferredF: Int): Int {
        var currDrawGlobalYPreferred = currDrawGlobalYPreferredF
        if (mWrapSelectorWheel && mWrapSelectorWheelCheck) {
            return currDrawGlobalYPreferred
        }
        if (currDrawGlobalYPreferred < mNotWrapLimitYBottom) {
            currDrawGlobalYPreferred = mNotWrapLimitYBottom
        } else if (currDrawGlobalYPreferred > mNotWrapLimitYTop) {
            currDrawGlobalYPreferred = mNotWrapLimitYTop
        }
        return currDrawGlobalYPreferred
    }

    private var mFlagMayPress = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }
        if (mItemHeight == 0) {
            return true
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker!!.addMovement(event)
        currY = event.y

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {
                mFlagMayPress = true
                mHandlerInNewThread!!.removeMessages(HANDLER_WHAT_REFRESH)
                stopScrolling()
                downY = currY
                downYGlobal = mCurrDrawGlobalY.toFloat()
                onScrollStateChange(OnScrollListener.SCROLL_STATE_IDLE)
                parent.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val spanY = downY - currY

                if (mFlagMayPress && (-mScaledTouchSlop < spanY && spanY < mScaledTouchSlop)) {
                    //
                } else {
                    mFlagMayPress = false
                    mCurrDrawGlobalY = limitY((downYGlobal + spanY).toInt())
                    calculateFirstItemParameterByGlobalY()
                    invalidate()
                }
                onScrollStateChange(OnScrollListener.SCROLL_STATE_TOUCH_SCROLL)
            }

            MotionEvent.ACTION_UP -> if (mFlagMayPress) {
                click(event)
            } else {
                val velocityTracker = mVelocityTracker
                velocityTracker!!.computeCurrentVelocity(1000)
                val velocityY = (velocityTracker.yVelocity * mFriction).toInt()
                if (abs(velocityY.toDouble()) > mMiniVelocityFling) {
                    mScroller!!.fling(
                        0,
                        mCurrDrawGlobalY,
                        0,
                        -velocityY,
                        Int.MIN_VALUE,
                        Int.MAX_VALUE,
                        limitY(Int.MIN_VALUE),
                        limitY(Int.MAX_VALUE)
                    )
                    invalidate()
                    onScrollStateChange(OnScrollListener.SCROLL_STATE_FLING)
                }
                mHandlerInNewThread!!.sendMessageDelayed(getMsg(HANDLER_WHAT_REFRESH), 0)
                // Освободить трекер скорости
                releaseVelocityTracker()
            }

            MotionEvent.ACTION_CANCEL -> {
                downYGlobal = mCurrDrawGlobalY.toFloat()
                stopScrolling()
                mHandlerInNewThread!!.sendMessageDelayed(getMsg(HANDLER_WHAT_REFRESH), 0)
            }
        }
        return true
    }

    private fun click(event: MotionEvent) {
        val y = event.y
        for (i in 0 until mShownCount) {
            if (mItemHeight * i <= y && y < mItemHeight * (i + 1)) {
                clickItem(i)
                break
            }
        }
    }

    // Определить индекс нажатой строки
    private fun clickItem(showCountIndex: Int) {
        if (showCountIndex in 0..<mShownCount) {
            scrollByIndexSmoothly(showCountIndex - mShownCount / 2)
        }
    }

    private fun getTextCenterYOffset(fontMetrics: Paint.FontMetrics?): Float {
        if (fontMetrics == null) {
            return 0f
        }
        return (abs((fontMetrics.top + fontMetrics.bottom).toDouble()) / 2).toFloat()
    }

    // Выбранный индекс по умолчанию относительно показанной части
    private fun correctPositionByDefaultValue(defaultPickedIndex: Int, wrap: Boolean) {
        mCurrDrawFirstItemIndex = defaultPickedIndex - (mShownCount - 1) / 2
        mCurrDrawFirstItemIndex = getIndexByRawIndex(
            mCurrDrawFirstItemIndex,
            oneRecycleSize, wrap
        )
        if (mItemHeight == 0) {
            mCurrentItemIndexEffect = true
        } else {
            mCurrDrawGlobalY = mCurrDrawFirstItemIndex * mItemHeight

            mInScrollingPickedOldValue = mCurrDrawFirstItemIndex + mShownCount / 2
            mInScrollingPickedOldValue = mInScrollingPickedOldValue % oneRecycleSize
            if (mInScrollingPickedOldValue < 0) {
                mInScrollingPickedOldValue = mInScrollingPickedOldValue + oneRecycleSize
            }
            mInScrollingPickedNewValue = mInScrollingPickedOldValue
            calculateFirstItemParameterByGlobalY()
        }
    }

    // Фиксировать прокрутку
    override fun computeScroll() {
        if (mItemHeight == 0) {
            return
        }
        if (mScroller!!.computeScrollOffset()) {
            mCurrDrawGlobalY = mScroller!!.currY
            calculateFirstItemParameterByGlobalY()
            postInvalidate()
        }
    }

    // Получить выбранное значение
    private fun calculateFirstItemParameterByGlobalY() {
        mCurrDrawFirstItemIndex =
            floor((mCurrDrawGlobalY.toFloat() / mItemHeight).toDouble()).toInt()
        mCurrDrawFirstItemY = -(mCurrDrawGlobalY - mCurrDrawFirstItemIndex * mItemHeight)
        if (mListener != null) {
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
                val value = mInScrollingPickedNewValue + mMin
                mListener!!.invoke(this, value)
            }
            mInScrollingPickedOldValue = mInScrollingPickedNewValue
        }
    }

    private fun releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker!!.clear()
            mVelocityTracker!!.recycle()
            mVelocityTracker = null
        }
    }

    private fun updateValueForInit() {
        updateWrapStateByContent()
        if (mMinShowIndex == -1) {
            mMinShowIndex = 0
        }
        if (mMaxShowIndex == -1) {
            mMaxShowIndex = mDisplayedValues.size - 1
        }
    }

    private fun updateWrapStateByContent() {
        mWrapSelectorWheelCheck = if (mDisplayedValues.size <= mShownCount) false else true
    }

    private fun stopScrolling() {
        if (mScroller != null) {
            if (!mScroller!!.isFinished) {
                mScroller!!.startScroll(0, mScroller!!.currY, 0, 0, 1)
                mScroller!!.abortAnimation()
                postInvalidate()
            }
        }
    }

    private fun getMsg(what: Int): Message {
        return getMsg(what, 0, 0, null)
    }

    private fun getMsg(what: Int, arg1: Int, arg2: Int, obj: Any?): Message {
        val msg = Message.obtain()
        msg.what = what
        msg.arg1 = arg1
        msg.arg2 = arg2
        msg.obj = obj
        return msg
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawContent(canvas)
        drawDivider(canvas)
        drawHint(canvas)
    }

    // Текст
    private fun drawContent(canvas: Canvas) {
        val xc = width / 2f
        var index: Int
        var textColor: Int
        var textSize: Float

        // Доля элемента в состоянии между нормальным и выбранным элементами, в [0, 1]
        var fraction = 0f

        // Смещение текста по Y
        var textSizeCenterYOffset: Float

        for (i in 0 until mShownCount + 1) {
            // Y первого показанного элемента + Высота Элемента * i
            val y = (mCurrDrawFirstItemY + mItemHeight * i).toFloat() + mFieldHint

            index = getIndexByRawIndex(
                mCurrDrawFirstItemIndex + i,
                oneRecycleSize, mWrapSelectorWheel && mWrapSelectorWheelCheck
            )
            if (i == mShownCount / 2) { // Выбранный элемент (средний)
                fraction = (mItemHeight + mCurrDrawFirstItemY).toFloat() / mItemHeight
                textColor = getEvaluateColor(fraction, mTextColorNormal, mTextColorSelected)
                textSize = getEvaluateSize(
                    fraction,
                    mTextSizeNormal.toFloat(),
                    mTextSizeSelected.toFloat()
                )
                textSizeCenterYOffset = getEvaluateSize(
                    fraction, mTextSizeNormalCenterYOffset,
                    mTextSizeSelectedCenterYOffset
                )

            } else if (i == mShownCount / 2 + 1) { // Нижний
                textColor = getEvaluateColor(1 - fraction, mTextColorNormal, mTextColorSelected)
                textSize = getEvaluateSize(
                    1 - fraction,
                    mTextSizeNormal.toFloat(),
                    mTextSizeSelected.toFloat()
                )

                textSizeCenterYOffset = getEvaluateSize(
                    1 - fraction, mTextSizeNormalCenterYOffset,
                    mTextSizeSelectedCenterYOffset
                )
            } else { // Верхний
                textColor = mTextColorNormal
                textSize = mTextSizeNormal.toFloat()

                textSizeCenterYOffset = mTextSizeNormalCenterYOffset
            }

            mPaintText.color = textColor
            mPaintText.textSize = textSize

            if (index in 0..<oneRecycleSize) {
                val strValue: String = mDisplayedValues[index + mMinShowIndex]

                val yt = y + mItemHeight / 2 + textSizeCenterYOffset
                val fh = mFieldHint.toInt()

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
        var y1: Float

        if (mShowRows5) y1 = mItemHeight.toFloat() * 2 + mFieldHint + mOffDivider
        else y1 = mItemHeight.toFloat() + mFieldHint + mOffDivider

        canvas.drawLine(x1, y1, x2, y1, mPaintDivider)
        y1 += mItemHeight.toFloat()

        if (mShowRows5) y1 = mItemHeight.toFloat() * 3 + mFieldHint - mOffDivider
        else y1 = mItemHeight.toFloat() * 2 + mFieldHint - mOffDivider
        canvas.drawLine(x1, y1, x2, y1, mPaintDivider)
    }

    // Подсказка
    private fun drawHint(canvas: Canvas) {
        if (mTextHint == null) {
            return
        }

        val x = width / 2f
        val y = mFieldHint - HINT_OFFSET
        canvas.drawText(mTextHint!!, x, y, mPaintHint)
    }

    // Инициализировать затухающие края
    private fun initializeFadingEdges() {
        setEdgeLength()
    }

    private fun setEdgeLength() {
        setFadingEdgeLength(getPixels())
    }

    override fun getTopFadingEdgeStrength(): Float {
        return 1.0f
    }

    override fun getBottomFadingEdgeStrength(): Float {
        return 1.0f
    }

    override fun hasOverlappingRendering(): Boolean {
        return true
    }

    public override fun onSetAlpha(alpha: Int): Boolean {
        return false
    }

    private fun getPixels(): Int {
        val r = context.resources

        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            mLength.toFloat(), r.displayMetrics
        ).toInt()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        mItemHeight = ITEM_HEIGHT

        if (mTextHint == null) mFieldHint = 0f

        val wC = mWidthItem + (mOffset * 2)
        val hC = mItemHeight * mShownCount + mFieldHint.toInt()

        setMeasuredDimension(
            resolveSize(wC, widthMeasureSpec),
            resolveSize(hC, heightMeasureSpec)
        )
    }

    private var mLength = 0 // Высота области затухания

    // Настройка строк
    private fun setRows() {
        if (mShowRows5) {
            mShownCount = 5
            mLength = mItemHeight
        }
        else {
            mShownCount = 3
            mLength = mItemHeight / 2
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        setRows()
        mWidthItem = w - mOffset * 2
        mItemHeight = (h - mFieldHint.toInt()) / mShownCount

        mRectClip = RectF(0f, mFieldHint, width.toFloat(), h.toFloat())

        // Затухание
        initializeFadingEdges()

        // Вычислить индекс первого видимого элемента (верхнего)
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

        correctPositionByDefaultValue(defaultValue, mWrapSelectorWheel && mWrapSelectorWheelCheck)
        updateFontAttr()
        updateNotWrapYLimit()
        mHasInit = true
    }

    //**********************

    var min: Int
        get() = mMin
        set(value) {
            mMin = value
            mMinShowIndex = 0
            updateNotWrapYLimit()
        }

    var max: Int
        get() = mMax
        set(value) {
            mMax = value
            mMaxShowIndex = mMax - mMin + mMinShowIndex
            updateNotWrapYLimit()
        }

    var value: Int
        get() = mPickedIndexRelativeToRaw + mMin
        set(value) {
            mPickedIndexRelativeToRaw = value - mMin
        }

    // Получить значение
    fun setOnChangeListener(listener: OnValueListener){
        mListener = listener
    }

    var displayedValues: Array<String>
        get() = mDisplayedValues
        set(value) {
            mMax = value.size
            mDisplayedValues = value
            mMaxShowIndex = mMax
        }

}