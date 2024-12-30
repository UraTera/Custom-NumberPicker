## Customizable NumberPicker. Four view options. Open source.

![NumberPicker1_6_1](https://github.com/user-attachments/assets/cf4d5b4a-6a54-4643-9f4d-a7fb3aaf164e)
![NumberPicker21](https://github.com/user-attachments/assets/e77357c6-7bf8-497d-8bcc-8ffab5c305e1)

The libs folder contains the compiled NumberPicker.aar library.

Connection:
```
implementation(files("libs/NumberPicker.aar"))
```
### Attributes
|Attributes |Description|Related method(s)|
|-----------|-----------|-----------------|
|np_dividerColor |Divider color||
|np_dividerHeight |Divider height||
|np_dividerOffset |Divider offset||
|np_max |Maximum value|setMax, getMax|
|np_min |Minimum value| setMin, getMin|
|np_showRows5|false – 3 rows, true – 5 rows||
|np_textArray |Array of values|setDisplayedValues, getDisplayedValues|
|np_textColorNormal |Unselected text color||
|np_textColorSelected |Selected text color||
|np_textSizeNormal |Unselected text size||
|np_textSizeSelected |Selected text size||
|np_textHint |Hint text||
|np_textColorHint |Hint text color||
|np_textSizeHint |Hint text size||



Value change listener:
```
picker.setOnChangeListener { picker, value -> 
    
}
```
Methods:
```
setValue, getValue
```
