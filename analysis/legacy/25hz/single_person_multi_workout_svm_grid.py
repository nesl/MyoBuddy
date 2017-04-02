import numpy
import os

from pylab import plot, show, bar

from sklearn import svm
from sklearn.datasets import make_multilabel_classification
from sklearn.multiclass import OneVsRestClassifier
from sklearn.model_selection import GridSearchCV
from sklearn import preprocessing




K_NUM_CHANNELS = 16
K_SAMPLES_PER_SEC = 25
K_SEC_PER_VECTOR = 1
K_SAMPLES_PER_VECTOR = K_SAMPLES_PER_SEC * K_SEC_PER_VECTOR


def parser(paths, symbol):
    vectors = []

    for filepath in paths:
        with open(filepath, "r") as f:
            lines = f.readlines()
        
        splitted = [l.strip().split(",") for l in lines]
        splitted = [list(map(int, l))[1:] for l in splitted]
        splitted = [nums for nums in splitted if len(nums) == K_NUM_CHANNELS]

        num_vectors = max(0, len(splitted) // K_SAMPLES_PER_VECTOR - 0)

        for i in range(num_vectors):
            si = i * K_SAMPLES_PER_SEC
            ei = (i+1) * K_SAMPLES_PER_SEC

            min_vals = []
            max_vals = []
            mean_vals = []
            mean_abs_vals = []
            var_vals = []
            range_vals = []

            vector = []
            for j in range(K_NUM_CHANNELS):
                values = [splitted[k][j] for k in range(si, ei)]

                min_val = min(values)
                max_val = max(values)
                min_vals.append(min_val)
                max_vals.append(max_val)
                mean_vals.append(numpy.mean(values))
                mean_abs_vals.append(numpy.mean([abs(v) for v in values]))
                var_vals.append(numpy.var(values))
                range_vals.append(max_val - min_val)
                #vector.extend(list(numpy.absolute(numpy.fft.fft(values)[:12])))

            vector.extend(min_vals)
            vector.extend(max_vals)
            vector.extend(mean_vals)
            vector.extend(mean_abs_vals)
            vector.extend(var_vals)
            vector.extend(range_vals)
            #vector.extend(sorted(min_vals))
            #vector.extend(sorted(max_vals))
            #vector.extend(sorted(mean_vals))
            #vector.extend(sorted(mean_abs_vals))
            #vector.extend(sorted(var_vals))
            #vector.extend(sorted(range_vals))

            vectors.append(vector)

    labels = [symbol for _ in range(len(vectors))]

    return (vectors, labels);


def get_file_paths(folders):
    ret = []
    for folder in folders:
        ret.extend([os.path.join(folder, fname) for fname in os.listdir(folder)
            if fname.startswith('log_2017')])
    return ret



### folder root
root_folder = 'data'

### dates
dates = [
        '0328',
        '0327',
        '0329',
]

### Knob of type and weights
workout_type = 'barbell_bicep'
weights = [20, 30, 40, 50, 60, 70]
#workout_type = 'tricep_extension_machine'
#weights = []

### Knob of data for training and testing
person = 'renju'
#person = 'bo'

lb_2_Xy = {}
for weight in weights:
    weight_str = str(weight) + 'lbs'
    folders = []
    for date in dates:
        folder = os.path.join(root_folder, date, workout_type, person, weight_str)
        if os.path.isdir(folder):
            folders.append(folder)
    paths = get_file_paths(folders)
    lb_2_Xy[weight] = parser(paths, weight)
    print('%d lbs: %d samples' % (weight, len(lb_2_Xy[weight][0])))

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


best_accuracy = -1
for p_C in [2**e for e in range(-8, 11)]:
    for p_gamma in [2**e for e in range(-8, 11)]:
        clf = svm.SVC(C=p_C, gamma=p_gamma)
        clf.fit(X_train_scale, y_train)
        prediction = clf.predict(X_test_scale)

        correct_cnt = sum([gnd == pred for gnd, pred in zip(y_test, prediction)])
        weight_errors = [abs(gnd - pred) for gnd, pred in zip(y_test, prediction)]

        accuracy = correct_cnt / len(y_test)
        weight_error = numpy.mean(weight_errors)
        print('C=%12f, gamma=%12f: accuracy=%3d/%3d (%f), weight error=%.1f' % (
            p_C, p_gamma, correct_cnt, len(y_test), accuracy, weight_error))
        if accuracy > best_accuracy:
            best_C, best_gamma, best_weight_error, best_accuracy = p_C, p_gamma, weight_error, accuracy

print('Final result: C=%10f, gamma=%12f, accuracy=%f, weight error=%.1f' % (
    best_C, best_gamma, best_accuracy, best_weight_error))
