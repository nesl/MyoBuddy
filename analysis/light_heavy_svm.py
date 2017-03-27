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

# heavy_path = input("Please enter the heavy path: ")
# light_path = input("Please enter the light path: ")
# test_file = input("Please enter the test file path: ")

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


#heavy_folders = [
#        'data/0321/heavy',
#]
#light_folders = [
#        'data/0321/light',
#]
heavy_folders = [
        'data/0322/20lbs',
]
light_folders = [
        'data/0322/5lbs',
]

heavy_file_paths = get_file_paths(heavy_folders)
light_file_paths = get_file_paths(light_folders)

X_heavy, y_heavy = parser(heavy_file_paths, LABEL_HEAVY)
X_light, y_light = parser(light_file_paths, LABEL_LIGHT)

num_heavy_samples = len(X_heavy)
num_light_samples = len(X_light)

print('num_heavy', num_heavy_samples, 'num_light', num_light_samples)

for row in X_heavy:
    print(0, row)
for row in X_light:
    print(1, row)

trainin_portion = 0.8
middle_heavy_idx = round(num_heavy_samples * trainin_portion)
middle_light_idx = round(num_light_samples * trainin_portion)

X_train = X_heavy[:middle_heavy_idx] + X_light[:middle_light_idx]
y_train = y_heavy[:middle_heavy_idx] + y_light[:middle_light_idx]
X_test = X_heavy[middle_heavy_idx:] + X_light[middle_light_idx:]
y_test = y_heavy[middle_heavy_idx:] + y_light[middle_light_idx:]

scaler = preprocessing.StandardScaler().fit(X_train)
X_train_scale = scaler.transform(X_train)
X_test_scale = scaler.transform(X_test)

clf = svm.SVC();
clf.fit(X_train_scale, y_train)
prediction = clf.predict(X_test_scale)

for gnd, pred in zip(y_test, prediction):
    print(gnd, pred)

correct_cnt = sum([gnd == pred for gnd, pred in zip(y_test, prediction)])
print("Result: %d/%d" % (correct_cnt, len(y_test)))
