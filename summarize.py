import os, shutil, re, sys

def summarize(filename):
    with open(filename) as f:
        content = f.readlines()

    index = {}
    time = {}
    feat = {}
    correct = {}
    oracle = {}

    for line in content:
        keyword = re.findall('Stats for iter=[0-9]\.[a-z]+', line)
        if len(keyword) == 0:
            continue
        type = keyword[0][len('Stats for iter=0.'):]

        index[type] = index.get(type, 0) + 1
        if index[type] > 3:
            continue

        correct[type] = re.findall('correct=[0-9]+\.[0-9]+', line)[0]
        oracle[type] = re.findall('oracle=[0-9]+\.[0-9]+', line)[0]
        time[type] = time.get(type, 0.0) + float(re.findall('parseTime=[0-9]+\.[0-9]+', line)[0][len('parseTime='):])
        feat[type] = feat.get(type, 0.0) + float(re.findall('numOfFeaturizedDerivs=[0-9]+\.[0-9]+', line)[0][len('numOfFeaturizedDerivs='):])

    for type in index:
        print(type + ':')
        print(correct[type])
        print(oracle[type])
        print('parseTime=%f' % (time[type]/3))
        print('numOfFeaturizedDerivs=%f' % (feat[type]/3))
        print('')

def main(args):
    summarize(args[1])

if __name__ == "__main__":
    main(sys.argv)
