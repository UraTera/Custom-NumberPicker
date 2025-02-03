## Fully customizable NumberPicker. Open source.

![NumberPicker](https://github.com/user-attachments/assets/f17061b6-9041-4cdb-a415-c24f4286b8b1)
![NumberPicker2](https://github.com/user-attachments/assets/5f4c4ed5-e5a2-4f22-a5fe-eb52d979d86f)

The libs folder contains the compiled NumberPicker.aar library.

Dependencies:
```
implementation(files("libs/NumberPicker.aar"))
```

### Attributes
|Attributes          |Description     |Default value|
|--------------------|----------------|-------------|
|np_dividerColor     |Divider color   |black
|np_dividerHeight    |Divider height |2dp
|np_dividerOffset    |Divider offset|0dp
|np_fadingExtent     |Edges fading extent (0-10)|7
|np_fontFamily       |Text font      |default
|np_hintText         |Text hint       |nothing
|np_hintTextColor    |Color text hint |black
|np_hintTextSize     |Size text hint |18sp
|np_intervalLongPress|Interval update of long press |300
|np_maxValue         |Maximum value|100
|np_minValue         |Minimum value|0
|np_textArray        |Array of strings |nothing
|np_textColor        |Color unselected text |gray
|np_textColorSel     |Color selected text |black
|np_textOffset       |Offset for long words |8dp
|np_textSize         |Size unselected text |20sp
|np_textSizeSel      |Size selected text|24sp
|np_showRows5        |Show 5 rows |false
|np_showZeros        |Show nonsignificant zeros |false

Value change listener.

Kotlin
```
picker.setOnChangeListener { picker, value -> 
   mValue = value 
}
```
Java
```
npCustom.setOnChangeListener((numberPickerCustom, integer) -> {
    mValue = integer
    return null;
});
```

Methods:
```
setMaxValue, setValue, getValue, getValueString, setDisplayedValues, setScroll
```

