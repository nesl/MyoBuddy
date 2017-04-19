import numpy
import os


def parse_and_get_length(paths):
    ret_length = 0.
    for filepath in paths:
        with open(filepath, "r") as f:
            lines = f.readlines()
        
        splitted = [l.strip().split(",") for l in lines]
        timestamps = [float(l[0]) for l in splitted]
        ret_length += (timestamps[-1] - timestamps[0]) * 1e-3

    return ret_length


def get_file_paths(folders):
    ret = []
    for folder in folders:
        ret.extend([os.path.join(folder, fname) for fname in os.listdir(folder)
            if fname.startswith('log_2017')])
    return ret


### folder root
root_folder = '../Data'

### dates
dates = [
        '0401',
        '0404',
        '0404_night',
        '0405',
        '0408_afternoon',
]

### Knob of type and weights
workout_type = 'barbell_bicep'
#workout_type = 'barbell_hold'
weights = [20, 30, 40, 50, 60, 70]
#workout_type = 'tricep_extension_machine'
#weights = []

### Knob of data for training and testing
people = [
    'renju',
    'bo',
]

total_len_sec = 0.
for person in people:
    for weight in weights:
        weight_str = str(weight) + 'lbs'
        folders = []
        for date in dates:
            folder = os.path.join(root_folder, date, workout_type, person, weight_str)
            if os.path.isdir(folder):
                folders.append(folder)
        paths = get_file_paths(folders)
        total_len_sec += parse_and_get_length(paths)

print('%.1f seconds' % total_len_sec)
