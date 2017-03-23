import os
import subprocess


ANDROID_IN_DIR = '/sdcard/Documents/'
LOCAL_OUT_DIR = 'data/waiting_zone/'


process = subprocess.Popen(['adb', 'shell', 'ls', ANDROID_IN_DIR], stdout=subprocess.PIPE)
(output, _) = process.communicate()
file_names = [n.strip() for n in output.decode().split('\n') if n.startswith('log')]

for name in file_names:
    android_in_path = os.path.join(ANDROID_IN_DIR, name)
    local_out_path = os.path.join(LOCAL_OUT_DIR, name)
    subprocess.call(['adb', 'pull', android_in_path, local_out_path])
    subprocess.call(['adb', 'shell', 'rm', android_in_path])
    print(name)
