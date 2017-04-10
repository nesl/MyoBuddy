import numpy
import os

from pylab import plot, show, bar
from scipy import stats

from sklearn import svm
from sklearn.datasets import make_multilabel_classification
from sklearn.multiclass import OneVsRestClassifier
from sklearn.model_selection import GridSearchCV
from sklearn.ensemble import RandomForestClassifier
from sklearn import preprocessing


K_NUM_CHANNELS = 8
K_SEC_PER_REP = 2
K_SKIPPED_REPS = 2


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
    ret_Xy = []
    for filepath in paths:
        vectors = []
        
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

        ret_Xy.append((vectors, labels))

    return ret_Xy


def get_file_paths(folders):
    ret = []
    for folder in folders:
        ret.extend([os.path.join(folder, fname) for fname in os.listdir(folder)
            if fname.startswith('log_2017')])
    return ret


def get_lb_2_Xy_sets(root_folder, dates, workout_type, weight_categories, person):
    weight_label_2_Xy_sets = {}
    for wlabel, weights in enumerate(weight_categories):
        weight_label_2_Xy_sets[wlabel] = []
        for weight in weights:
            weight_str = str(weight) + 'lbs'
            folders = []
            for date in dates:
                folder = os.path.join(root_folder, date, workout_type, person, weight_str)
                if os.path.isdir(folder):
                    folders.append(folder)
            paths = get_file_paths(folders)
            weight_label_2_Xy_sets[wlabel].extend(parser(paths, wlabel))
        num_total_reps = 0
        for X, _ in weight_label_2_Xy_sets[wlabel]:
            num_total_reps += len(X)
        print('weight label %d: %d sets, %d reps' % (
            wlabel, len(weight_label_2_Xy_sets[wlabel]), num_total_reps))
    return weight_label_2_Xy_sets


def decide_set_label(pred_labels, method='majority vote'):
    if method == 'majority vote':
        return int(stats.mode(pred_labels)[0][0])
    elif method == 'mean':
        return int(round(numpy.mean(pred_labels), 0))
    else:
        raise Exception('Non-existed method')


def evaluate_classification(classifier, X_train, y_train, testing_lb_2_Xy_sets):
    total_test_reps = 0
    correct_test_reps = 0
    rep_weight_errors = []

    total_test_sets = 0
    correct_test_sets = 0

    scaler = preprocessing.StandardScaler().fit(X_train)
    X_train_scale = scaler.transform(X_train)

    classifier.fit(X_train_scale, y_train)

    for lb in testing_lb_2_Xy_sets:
        for X_test, y_test in testing_lb_2_Xy_sets[lb]:
            X_test_scale = scaler.transform(X_test)
            prediction = clf.predict(X_test_scale)

            correct_cnt = sum([gnd == pred for gnd, pred in zip(y_test, prediction)])
            weight_errors = [abs(gnd - pred) for gnd, pred in zip(y_test, prediction)]
            
            correct_test_reps += correct_cnt
            total_test_reps += len(y_test)
            rep_weight_errors.extend(weight_errors)

            total_test_sets += 1
            correct_test_sets += (decide_set_label(prediction, method='mean') == y_test[0])

    avg_rep_accuracy = correct_test_reps / total_test_reps
    avg_rep_weight_error = numpy.mean(rep_weight_errors)
    avg_set_accuracy = correct_test_sets / total_test_sets

    return (avg_rep_accuracy, correct_test_reps, total_test_reps, avg_rep_weight_error,
            avg_set_accuracy, correct_test_sets, total_test_sets)


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

#person_train = 'renju'
#person_test = 'bo'
#weight_category_train = [[20, 30, 40], [50, 60, 70]]
#weight_category_test = [[20, 30], [40, 50]]
#weight_category_train = [[20, 30], [40, 50], [60, 70]]
#weight_category_test = [[20, 30], [40], [50]]
#weight_category_train = [[20, 30], [40, 50], [60, 70]]
#weight_category_test = [[20], [30, 40], [50]]
#weight_category_train = [[20, 30], [40, 50], [60, 70]]
#weight_category_test = [[20], [30], [40, 50]]
#weight_category_train = [[20], [30], [40, 50], [60, 70]]
#weight_category_test = [[20], [30], [40], [50]]

person_train = 'bo'
person_test = 'renju'
#weight_category_train = [[20, 30], [40, 50]]
#weight_category_test = [[20, 30, 40], [50, 60, 70]]
#weight_category_train = [[20], [30, 40], [50]]
#weight_category_test = [[20, 30], [40, 50], [60, 70]]
weight_category_train = [[20], [30], [40], [50]]
weight_category_test = [[20], [30, 40], [50, 60], [70]]

if len(weight_category_train) != len(weight_category_test):
    raise Exception('The number of weight categories in training and testing data are different')

print('Parse training data (%s):' % person_train)
print('Weight:', weight_category_train)
weight_category_2_Xy_sets_train = get_lb_2_Xy_sets(
        root_folder, dates, workout_type, weight_category_train, person_train)

print('Parse testing data (%s):' % person_test)
print('Weight:', weight_category_test)
weight_category_2_Xy_sets_test = get_lb_2_Xy_sets(
        root_folder, dates, workout_type, weight_category_test, person_test)

training_X = []
training_y = []
for wlabel in weight_category_2_Xy_sets_train:
    for Xy in weight_category_2_Xy_sets_train[wlabel]:
        X, y = Xy
        training_X.extend(X)
        training_y.extend(y)


best_rep_accuracy, best_rep_weight_error, best_rep_params = -1, None, None
best_set_accuracy, best_set_params = -1, None
for p_C in [2**e for e in range(-8, 11)]:
    for p_gamma in [2**e for e in range(-8, 11)]:
        clf = svm.SVC(C=p_C, gamma=p_gamma)

        (avg_rep_accuracy, correct_test_reps, total_test_reps, avg_rep_weight_error,
            avg_set_accuracy, correct_test_sets, total_test_sets) = evaluate_classification(
                    clf, training_X, training_y, weight_category_2_Xy_sets_test)

        #print('C=%12f, gamma=%12f: rep accuracy=%3d/%3d (%f), rep weight error=%4.1f, set accuracy=%3d/%3d (%f)' % (
        #    p_C, p_gamma, correct_test_reps, total_test_reps, avg_rep_accuracy, avg_rep_weight_error,
        #    correct_test_sets, total_test_sets, avg_set_accuracy))

        params_str = 'Clf=SVM.rbf, C=%f, gamma=%f' % (p_C, p_gamma)

        if avg_rep_accuracy > best_rep_accuracy:
            best_rep_accuracy, best_rep_weight_error, best_rep_params = (
                    avg_rep_accuracy, avg_rep_weight_error, params_str)
        if avg_set_accuracy > best_set_accuracy:
            best_set_accuracy, best_set_params = avg_set_accuracy, params_str

print('Final result rep: %s, accuracy=%f, weight error=%.1f' % (
    best_rep_params, best_rep_accuracy, best_rep_weight_error))
print('Final result sep: %s, accuracy=%f' % (
    best_set_params, best_set_accuracy))


best_rep_accuracy, best_rep_weight_error, best_rep_params = -1, None, None
best_set_accuracy, best_set_params = -1, None
for p_e in range(1, 50):
    clf = RandomForestClassifier(n_estimators=p_e)

    (avg_rep_accuracy, correct_test_reps, total_test_reps, avg_rep_weight_error,
        avg_set_accuracy, correct_test_sets, total_test_sets) = evaluate_classification(
                clf, training_X, training_y, weight_category_2_Xy_sets_test)

    #print('trees=%d: rep accuracy=%3d/%3d (%f), rep weight error=%4.1f, set accuracy=%3d/%3d (%f)' % (
    #    p_e, correct_test_reps, total_test_reps, avg_rep_accuracy, avg_rep_weight_error,
    #    correct_test_sets, total_test_sets, avg_set_accuracy))

    params_str = 'Clf=RF, e=%d' % (p_e)

    if avg_rep_accuracy > best_rep_accuracy:
        best_rep_accuracy, best_rep_weight_error, best_rep_params = (
                avg_rep_accuracy, avg_rep_weight_error, params_str)
    if avg_set_accuracy > best_set_accuracy:
        best_set_accuracy, best_set_params = avg_set_accuracy, params_str

print('Final result rep: %s, accuracy=%f, weight error=%.1f' % (
    best_rep_params, best_rep_accuracy, best_rep_weight_error))
print('Final result sep: %s, accuracy=%f' % (
    best_set_params, best_set_accuracy))
