import numpy
import os

from pylab import plot, show, bar

from sklearn import svm
from sklearn.datasets import make_multilabel_classification
from sklearn.multiclass import OneVsRestClassifier
from sklearn.model_selection import GridSearchCV
from sklearn import preprocessing
from sklearn.ensemble import RandomForestClassifier


K_NUM_CHANNELS = 8
K_SEC_PER_REP = 2
K_SKIPPED_REPS = 2


def parser(paths, symbol):
    vectors = []

    for filepath in paths:
        with open(filepath, "r") as f:
            lines = f.readlines()
        
        # preprocessing
        splitted = [l.strip().split(",") for l in lines]
        timestamps = [float(l[0]) for l in splitted]
        start_timestamp = timestamps[0]
        timestamps = [(t - start_timestamp) * 1e-3 for t in timestamps]

        features = [list(map(int, l))[2:] for l in splitted]

        # put into buckets
        buckets = {}
        bucket_time = {}
        for time, feature_row in zip(timestamps, features):
            if len(feature_row) != K_NUM_CHANNELS:
                continue

            bucket_idx = int(time / K_SEC_PER_REP)
            if bucket_idx < K_SKIPPED_REPS:
                continue

            if bucket_idx not in buckets:
                buckets[bucket_idx] = []
                bucket_time[bucket_idx] = (time, time)
            buckets[bucket_idx].append(feature_row)
            st, et = bucket_time[bucket_idx]
            bucket_time[bucket_idx] = min(st, time), max(et, time)
            
        # clean the buckets a bit
        bucket_idxs_to_be_removed = []
        for bidx in buckets:
            is_small_coverage = (bucket_time[bidx][1] - bucket_time[bidx][0] < 1.5)
            is_insufficient_samples = (len(buckets[bidx]) < 100)
            if is_small_coverage or is_insufficient_samples:
                bucket_idxs_to_be_removed.append(bidx)

        for bidx in bucket_idxs_to_be_removed:
            buckets.pop(bidx, None)

        for bidx in buckets:
            cur_bucket = buckets[bidx]
            cur_vector = []

            # channel dependent features
            for j in range(K_NUM_CHANNELS):
                values = [row[j] for row in cur_bucket]
                abs_values = [abs(v) for v in values]

                min_val = min(values)
                max_val = max(values)
                pt5_val = numpy.percentile(values, 5)
                pt95_val = numpy.percentile(values, 95)
                mean_val = numpy.mean(values)
                var_val = numpy.var(values)

                max_abs_val = max(abs_values)
                pt95_abs_val = numpy.percentile(abs_values, 95)
                pt75_abs_val = numpy.percentile(abs_values, 75)
                pt50_abs_val = numpy.percentile(abs_values, 50)
                pt25_abs_val = numpy.percentile(abs_values, 25)
                pt5_abs_val = numpy.percentile(abs_values, 5)
                mean_abs_val = numpy.mean(abs_values)
                var_abs_val = numpy.var(values)

                cur_vector.extend([min_val, max_val, pt5_val, pt95_val, mean_val, var_val,
                    max_abs_val, pt95_abs_val, pt75_abs_val, pt50_abs_val, pt25_abs_val,
                    pt5_abs_val, mean_abs_val, var_abs_val])

                #vector.extend(list(numpy.absolute(numpy.fft.fft(values)[:12])))

            vectors.append(cur_vector)

    labels = [symbol for _ in range(len(vectors))]

    return (vectors, labels);


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
]

### Knob of type and weights
workout_type = 'barbell_bicep'
weights = [20, 30, 40, 50, 60, 70]
#workout_type = 'tricep_extension_machine'
#weights = []

### Knob of data for training and testing
#person = 'renju'
person = 'bo'

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

# print(y_train)

# forest_y_train = [x / 10 - 1 for x in y_train]
# print(forest_y_train)
# forest_y_test = [x/10 -1 for x in y_test]

# enc = preprocessing.OneHotEncoder()
# forest_y_train = enc.fit_transform(numpy.array(y_train).reshape(-1,1)).astype(numpy.int)
# forest_y_test = enc.transform(numpy.array(y_test).reshape(-1,1)).astype(numpy.int)
# print(forest_y_test.shape)
# print(forest_y_train.shape)


scaler = preprocessing.StandardScaler().fit(X_train)
X_train_scale = scaler.transform(X_train)
X_test_scale = scaler.transform(X_test)

best_accuracy = -1
best_n_estimators = -1

for e in range(1, 50):
    clf = RandomForestClassifier(n_estimators=e)
    clf.fit(X_train_scale, y_train)
    prediction = clf.predict(X_test_scale)
    correct_cnt = sum([gnd == pred for gnd, pred in zip(y_test, prediction)])
    accuracy = correct_cnt / len(y_test)
    if accuracy > best_accuracy:
        best_accuracy = accuracy
        best_n_estimators = e



print('Random Forest Results: Best accuracy = %f, Best n estimators = %d' % (best_accuracy, best_n_estimators))



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
        # print('C=%12f, gamma=%12f: accuracy=%3d/%3d (%f), weight error=%.1f' % (
            # p_C, p_gamma, correct_cnt, len(y_test), accuracy, weight_error))
        if accuracy > best_accuracy:
            best_C, best_gamma, best_weight_error, best_accuracy = p_C, p_gamma, weight_error, accuracy

print('SVM Results: C=%10f, gamma=%12f, accuracy=%f, weight error=%.1f' % (
    best_C, best_gamma, best_accuracy, best_weight_error))
