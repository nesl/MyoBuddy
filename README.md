# myo_AndoridEMG
We got the Myo's EMG-data on Android by hacking bluetooth.  
Getting the EMG-data and try to detect the user "defined" pose.  

## How to use :
1. From the MENU list, choose the "Find Myo". Then, start another page.
1. From the MENU select or "SCAN" button, scanning bluetooth devices for 5 (s).
1. Stop scanning, there are some devices' name, and select your Myo. (If there are no Myo name, re-try scanning.)
1. Return to the MAIN page, and showing the Myo's status. You can try "VIBLATE" and "EMG".
1. If running on EMG data, try to "SAVE" 3 different hand pouses (ex. rock, scissors, and paper). Tap "SAVE" is to start to record a pouse.
1. Then you can try "DETECT" !

## Tips :
- This application make a new file (/sdcard/Myo_compare/compareData.dat) on your Android. If you need a different file path or something, please check the "MyoDataFileReader.class".
- Because of the Myo settings, sometimes the Myo kills the Bluetooth connection. You will pay attention to the Myo status LED. If the RED light is flushing, some errors occar on Myo. When you find no blue light at there, select "Good by" from the MENU list and re-try connecting.

## Reference :
- Thalmic Lab. myo bluetooth protocol : 
[https://github.com/thalmiclabs/myo-bluetooth]

## License :
- This software is released under the MIT License, see LICENSE.
