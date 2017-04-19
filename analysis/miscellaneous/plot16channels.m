%path = 'data/0328/barbell_bicep/bo/20lbs/log_2017-03-28T23-14-52Z_My-Myo2.txt';
%path = 'data/0328/barbell_bicep/bo/30lbs/log_2017-03-28T23-13-59Z_My-Myo2.txt';
%path = 'data/0328/barbell_bicep/bo/40lbs/log_2017-03-28T23-12-55Z_My-Myo2.txt';

%path = 'data/0401/barbell_hold/bo/30lbs/log_2017-04-01T23-52-12Z_My-Myo1.txt';
%path = 'data/0401/barbell_hold/bo/40lbs/log_2017-04-01T23-46-50Z_My-Myo1.txt';
%path = 'data/0401/barbell_hold/bo/50lbs/log_2017-04-01T23-45-46Z_My-Myo1.txt';

%paths = {
%    'data/0401/barbell_hold/bo/30lbs/log_2017-04-01T23-52-12Z_My-Myo1.txt'
%    'data/0401/barbell_hold/bo/40lbs/log_2017-04-01T23-46-50Z_My-Myo1.txt'
%    'data/0401/barbell_hold/bo/50lbs/log_2017-04-01T23-45-46Z_My-Myo1.txt'
%};

paths = {
    'data/0401/barbell_hold/renju/30lbs/log_2017-04-01T23-39-36Z_My-Myo1.txt'
    'data/0401/barbell_hold/renju/40lbs/log_2017-04-01T23-43-30Z_My-Myo1.txt'
    'data/0401/barbell_hold/renju/50lbs/log_2017-04-01T23-48-58Z_My-Myo1.txt'
};



for pidx = 1:numel(paths)
    data = csvread(paths{pidx});
    subplot(3, 1, pidx)
    for i = 1:1
        plot(data(:, i+2));
    end
end

return

%%

for pidx = 1:numel(paths)
    data = csvread(paths{pidx});
    cnt = zeros(1, 256);
    for i = 1:1
        for j = 1:size(data, 1)
            t = data(j, i+2) + 129;
        	cnt(t) = cnt(t) + 1;
        end
    end
    subplot(3, 1, pidx)
    bar(cnt)
end

return

%%

num_batch = floor(size(data, 1) / B);
channel = 4;
for i = 1:num_batch
    s = (i-1) * B + 1;
    e = i * B;
    plot(abs(fft(data(s:e, channel+1))), 'Color', hsv2rgb(i/num_batch, 0.8, 0.8))
    hold on
end

%% plot figures

cfigure(14, 8)

markers = {
    'b--'
    ''
    'r-'
};

for pidx = [1 3]
    data = csvread(paths{pidx});
    cnt = zeros(1, 129);
    for i = 1:1
        for j = 1:size(data, 1)
            t = ceil(data(j, i+2) / 2 + 65);
        	cnt(t) = cnt(t) + 1;
        end
    end
    cnt = cnt / size(data, 1);
    plot(-128:2:128, cnt, markers{pidx}, 'LineWidth', 2)
    hold on
end

grid on
xlabel('Sensor value', 'FontSize', 14)
ylabel('PDF', 'FontSize', 14)

legend({'30lbs', '50lbs'}, 'Location', 'northeast', 'FontSize', 14)



return
