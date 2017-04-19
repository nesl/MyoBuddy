import numpy
import os

from pylab import plot, show, bar

from sklearn import svm
from sklearn.datasets import make_multilabel_classification
from sklearn.multiclass import OneVsRestClassifier
from sklearn.model_selection import GridSearchCV
from sklearn import preprocessing


K_NUM_CHANNELS = 8
K_SEC_PER_REP = 2
K_SKIPPED_REPS = 2
K_NUM_FOLDS = 5


def extract_features_from_window(values):
    abs_values = [abs(v) for v in values]

    num_samples = len(values)

    min_val = min(values)
    max_val = max(values)
    pt5_val = numpy.percentile(values, 5)
    pt95_val = numpy.percentile(values, 95)
    mean_val = numpy.mean(values)
    var_val = numpy.var(values)

    diff_mean = numpy.mean(abs(numpy.diff(values)))

    max_abs_val = max(abs_values)
    pt95_abs_val = numpy.percentile(abs_values, 95)
    pt75_abs_val = numpy.percentile(abs_values, 75)
    pt50_abs_val = numpy.percentile(abs_values, 50)
    pt25_abs_val = numpy.percentile(abs_values, 25)
    pt5_abs_val = numpy.percentile(abs_values, 5)
    mean_abs_val = numpy.mean(abs_values)
    var_abs_val = numpy.var(values)

    cnt0_40 = len([v for v in abs_values if v <= 40])
    cnt0_80 = len([v for v in abs_values if v <= 80])
    cnt40_80 = len([v for v in abs_values if 40 <= v and v <= 80])
    cnt80_120 = len([v for v in abs_values if 80 <= v and v <= 120])
    cnt125_inf = len([v for v in abs_values if v <= 125])

    ratio0_40 = cnt0_40 / num_samples
    ratio0_80 = cnt0_80 / num_samples
    ratio40_80 = cnt40_80 / num_samples
    ratio80_120 = cnt80_120 / num_samples
    ratio125_inf = cnt125_inf / num_samples

    #vector.extend(list(numpy.absolute(numpy.fft.fft(values)[:12])))

    return [min_val, max_val, pt5_val, pt95_val, mean_val, var_val, diff_mean,
        max_abs_val, pt95_abs_val, pt75_abs_val, pt50_abs_val, pt25_abs_val,
        pt5_abs_val, mean_abs_val, var_abs_val,
        #num_samples,
        ratio0_40, ratio0_80, ratio40_80, ratio80_120, ratio125_inf]


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
                cur_vector.extend(extract_features_from_window(values))

            # aggregate channel features
            values = [numpy.mean(row) for row in cur_bucket]
            cur_vector.extend(extract_features_from_window(values))

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
root_folder = 'data'

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


best_accuracy = -1
for p_C in [2**e for e in range(-8, 11)]:
    for p_gamma in [2**e for e in range(-8, 11)]:
        total_test_instances = 0
        correct_test_instances = 0
        fold_accuracies = []
        fold_weight_errors = []

        for fold_idx in range(K_NUM_FOLDS):
            X_train = []
            y_train = []
            X_test = []
            y_test = []
            for lb in lb_2_Xy:
                X, y = lb_2_Xy[lb]
                st_idx = len(X) * fold_idx / K_NUM_FOLDS
                ed_idx = len(X) * (fold_idx + 1) / K_NUM_FOLDS
                for i in range(len(X)):
                    if st_idx <= i and i < ed_idx:
                        X_test.append(X[i])
                        y_test.append(y[i])
                    else:
                        X_train.append(X[i])
                        y_train.append(y[i])
            
            scaler = preprocessing.StandardScaler().fit(X_train)
            X_train_scale = scaler.transform(X_train)
            X_test_scale = scaler.transform(X_test)

            clf = svm.SVC(C=p_C, gamma=p_gamma)
            clf.fit(X_train_scale, y_train)
            prediction = clf.predict(X_test_scale)

            correct_cnt = sum([gnd == pred for gnd, pred in zip(y_test, prediction)])
            weight_errors = [abs(gnd - pred) for gnd, pred in zip(y_test, prediction)]

            accuracy = correct_cnt / len(y_test)
            weight_error = numpy.mean(weight_errors)

            correct_test_instances += correct_cnt
            total_test_instances += len(y_test)
            fold_accuracies.append(accuracy)
            fold_weight_errors.append(weight_error)

        avg_accuracy = numpy.mean(fold_accuracies)
        avg_weight_error = numpy.mean(fold_weight_errors)

        print('C=%12f, gamma=%12f: accuracy=%3d/%3d (%f), weight error=%.1f' % (
            p_C, p_gamma, correct_test_instances, total_test_instances, avg_accuracy, avg_weight_error))
        if avg_accuracy > best_accuracy:
            best_C, best_gamma = p_C, p_gamma
            best_weight_error, best_accuracy = avg_weight_error, avg_accuracy

print('Final result: C=%10f, gamma=%12f, accuracy=%f, weight error=%.1f' % (
    best_C, best_gamma, best_accuracy, best_weight_error))
