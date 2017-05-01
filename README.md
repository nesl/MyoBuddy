# MyoBuddy
MyoBuddy project aims at using electromyogram (EMG) sensors to detect weight one lifts when performing exercises, and help patients who suffer from muscle dystrophy track their rehabilitation progress.

Our paper got accepted in BioMarker 2017, a workshop of MobiSys. Currently, we are making our paper camera-ready, and the link wlil appear once the paper is finalized.

## Structure of repository:
- `BLE_myo` folder is the Android app which collects the EMG data from Myo armband.
- `Data` folder is the EMG data collected from our app.
- `analysis` folder includes several scripts we developed for classifying weights.

#### BLE\_myo
The interface should be simple enough to guide you the process. In short, you have to scan Myo devices over Bluetooth Low Energy, select the desired device, and click start to collect data. The data is saved in `/sdcard/Documents/` folder. We modified the cloned app and make it possible to collect 200Hz of EMG samples from Myo.

#### Data
The hierarchy is `<date>`/`<type>`/`<person>`/`<weight>`/`<one_session_of_exercise>`. Each line in these files represent one EMG sample, with the following format:

`<Android timestamp>`,`<BLE characteristic index>`,`<EMG reading 1>`, ..., `<EMG reading 8>`

#### analysis
Several python scripts are included in this folder. Most can be executed right after you download this repository.

## Reference :
Thalmic Lab. myo bluetooth protocol:
* https://github.com/thalmiclabs/myo-bluetooth
* http://developerblog.myo.com/myocraft-emg-in-the-bluetooth-protocol/
* https://github.com/thalmiclabs/myo-bluetooth/blob/master/myohw.h

EMG Sampling Rate
* https://developer.thalmic.com/forums/topic/1945/

## License :
- This software is released under the BSD-3 License, see LICENSE.
