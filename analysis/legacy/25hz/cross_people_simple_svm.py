from sklearn import svm

from sklearn.datasets import make_multilabel_classification
from sklearn.multiclass import OneVsRestClassifier
from sklearn.svm import SVC
from sklearn import preprocessing

from pylab import plot, show, bar

import numpy
import os

LABEL_HEAVY = 1
LABEL_LIGHT = 0

K_NUM_CHANNELS = 16
K_SAMPLES_PER_SEC = 25


def parser(paths, symbol):
    vectors = []

    for filepath in paths:
        with open(filepath, "r") as f:
            lines = f.readlines()
        
        splitted = [l.strip().split(",") for l in lines]
        splitted = [list(map(int, l))[1:] for l in splitted]
        splitted = [nums for nums in splitted if len(nums) == K_NUM_CHANNELS]

        num_vectors = len(splitted) // K_SAMPLES_PER_SEC

        for i in range(num_vectors):
            si = i * K_SAMPLES_PER_SEC
            ei = (i+1) * K_SAMPLES_PER_SEC

            vector = []
            for j in range(K_NUM_CHANNELS):
                values = [splitted[k][j] for k in range(si, ei)]
                min_val = min(values)
                max_val = max(values)
                mean = numpy.mean(values)
                var = numpy.var(values)
                ran = max_val - min_val
                vector.append(min_val)
                vector.append(max_val)
                vector.append(mean)
                vector.append(var)
                vector.append(ran)

            vectors.append(vector)

    labels = [symbol for _ in range(len(vectors))]

    return (vectors, labels);


def get_file_paths(folders):
    ret = []
    for folder in folders:
        ret.extend([os.path.join(folder, fname) for fname in os.listdir(folder)
            if fname.startswith('log_2017')])
    return ret


def parse_n_extend(paths, symbol, ret_X, ret_y):
    X, y = parser(paths, symbol)
    ret_X.extend(X)
    ret_y.extend(y)


### folder root
folder_root = 'data/0327'

### Knob of type and weights
#workout_type = 'barbell_bicep'
#light_weight = '40lbs'
#heavy_weight = '50lbs'
workout_type = 'tricep_extension_machine'
light_weight = '27.5lbs'
heavy_weight = '37.5lbs'

### Knob of data for training and testing
person_for_training = 'renju'
person_for_testing = 'bo'

training_light_folders = [
        os.path.join(folder_root, workout_type, person_for_training, light_weight),
]
training_heavy_folders = [
        os.path.join(folder_root, workout_type, person_for_training, heavy_weight),
]
testing_light_folders = [
        os.path.join(folder_root, workout_type, person_for_testing, light_weight),
]
testing_heavy_folders = [
        os.path.join(folder_root, workout_type, person_for_testing, heavy_weight),
]

training_light_paths = get_file_paths(training_light_folders)
training_heavy_paths = get_file_paths(training_heavy_folders)
testing_light_paths = get_file_paths(testing_light_folders)
testing_heavy_paths = get_file_paths(testing_heavy_folders)

X_train = []
y_train = []
parse_n_extend(training_heavy_paths, LABEL_HEAVY, X_train, y_train)
parse_n_extend(training_light_paths, LABEL_LIGHT, X_train, y_train)

X_test = []
y_test = []
parse_n_extend(testing_heavy_paths, LABEL_HEAVY, X_test, y_test)
parse_n_extend(testing_light_paths, LABEL_LIGHT, X_test, y_test)


scaler = preprocessing.StandardScaler().fit(X_train)
X_train_scale = scaler.transform(X_train)
X_test_scale = scaler.transform(X_test)

clf = svm.SVC();
clf.fit(X_train_scale, y_train)
prediction = clf.predict(X_test_scale)

confusion = numpy.zeros((2, 2))
for gnd, pred in zip(y_test, prediction):
    print(gnd, pred)
    confusion[gnd][pred] += 1

correct_cnt = sum([gnd == pred for gnd, pred in zip(y_test, prediction)])

print("Result: %d/%d (%f)" % (correct_cnt, len(y_test), (correct_cnt / len(y_test))))
print(confusion)
