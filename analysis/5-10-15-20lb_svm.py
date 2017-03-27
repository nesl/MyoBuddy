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


d_5lb_folders = [
        'data/0322/5lbs',
]
d_10lb_folders = [
        'data/0322/10lbs',
]
d_15lb_folders = [
        'data/0322/15lbs',
]
d_20lb_folders = [
        'data/0322/20lbs',
]

lb_2_paths = {
        5: get_file_paths(d_5lb_folders),
        10: get_file_paths(d_10lb_folders),
        15: get_file_paths(d_15lb_folders),
        20: get_file_paths(d_20lb_folders),
}

lb_2_Xy = {}
for lb in lb_2_paths:
    lb_2_Xy[lb] = parser(lb_2_paths[lb], lb)
    print('%d lbs: %d samples' % (lb, len(lb_2_Xy[lb])))

training_portion = 0.8
X_train = []
y_train = []
X_test = []
y_test = []
for lb in lb_2_Xy:
    X, y = lb_2_Xy[lb]
    middle_idx = round(len(X) * training_portion)
    X_train.extend(X[:middle_idx])
    y_train.extend(y[:middle_idx])
    X_test.extend(X[middle_idx:])
    y_test.extend(y[middle_idx:])

scaler = preprocessing.StandardScaler().fit(X_train)
X_train_scale = scaler.transform(X_train)
X_test_scale = scaler.transform(X_test)

clf = svm.SVC();
clf.fit(X_train_scale, y_train)
prediction = clf.predict(X_test_scale)

remap = {
        5: 0,
        10: 1,
        15: 2,
        20: 3,
}
confusion = numpy.zeros((4, 4))
for gnd, pred in zip(y_test, prediction):
    print(gnd, pred)
    confusion[ remap[gnd] ][ remap[pred] ] += 1

correct_cnt = sum([gnd == pred for gnd, pred in zip(y_test, prediction)])

print("Result: %d/%d" % (correct_cnt, len(y_test)))
print(confusion)
