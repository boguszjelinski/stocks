import csv

sp100 = "C:\\home\\dell\\DIVID\\GIT\\tiingo\\SP100.txt"
tickers = open(sp100).read().split('\n')

quotes = {}
for t in tickers:
    if len(t)<=0:
        continue
    path = 'C:\\home\\dell\\DIVID\data\\'+ t +'-prc'
    with open(path, 'r') as f:
        data = list(csv.reader(f, delimiter=','))
        # cleaning two colums for dividends and split
        for i in range(1,len(data)): # 1: skip headers
            data[i][1] = 0.0 # div
            data[i][2] = 1 # split factor
        quotes[t] = data

divids = {}
for t in tickers:
    if len(t)<=0:
        continue
    path = 'C:\\home\\dell\\DIVID\data\\'+ t +'-div'
    with open(path, 'r') as f:
        divids[t] = list(csv.reader(f, delimiter=','))

for t in tickers:
    if len(t)<=0:
        continue
    div = divids[t]
    qts = quotes[t]
    for j in range (1, len(div)) :
        found = False
        for i in range(1,len(qts)):
            if qts[i][0] == div[j][0]:
                qts[i][1] = div[j][1]
                found = True
                break
        if not found:
            print("Date not found for dividend, sym:", t, " date:", div[j][0])
    quotes[t] = qts

path = 'C:\\home\\dell\\DIVID\data\\splits.txt'
with open(path, 'r') as f:
    splits = list(csv.reader(f, delimiter=','))
    for j in range(0,len(splits)): # 0: no header
        qts = quotes[splits[j][0]]
        # searching for the date
        found = False
        for i in range(1,len(qts)):
            if qts[i][0] == splits[j][1]:
                qts[i][2] = splits[j][2]
                found = True
                break
        if not found:
            print("Date not found for split, sym:", splits[j][0], " date:", splits[j][1])
        else:
            quotes[splits[j][0]] = qts

for t in tickers:
    if len(t)<=0:
        continue
    path = 'C:\\home\\dell\\DIVID\yahoo2tiingo\\'+ t +'.json'
    f = open(path, "w")
    f.write("[")
    qts = quotes[t]
    for i in reversed(range(1,len(qts))):
        #f.write('{"date":"%s","close":"%s","divCash":"%f","splitFactor":"%f"},\n' %
                                #(qts[i][0], qts[i][4], qts[i][1], qts[i][2]))
        f.write('{"date":"%s",' % (qts[i][0]))
        f.write('"close":%s,' % (qts[i][4]))
        f.write('"divCash":%s,' % (qts[i][1]))
        f.write('"splitFactor":%s}' % (qts[i][2]))
        if i > 1:
            f.write(',')
        f.write('\n')

    f.write("]")
    f.close()