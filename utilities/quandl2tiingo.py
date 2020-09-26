import csv
import json

root = "C:\\home\\dell\\DIVID\\History\\"
sp100 = root + "SP100.txt"
tickers = open(sp100).read().split('\n')

def readCsv(catalog, close, divid, split):
    quotes = {}
    for t in tickers:
        if len(t)<=0:
            continue
        path = root + catalog + '\\'+ t + '.csv' 
        try:
            with open(path, 'r') as f:
                q = list(csv.reader(f, delimiter=','))
                table = []
                for row in q:
                    if row[0]=='timestamp' or row[0]=='Date':
                        continue
                    r = {}
                    r['date'] = row[0]
                    r['close'] = float(row[close])
                    r['divCash'] = float(row[divid])
                    r['splitFactor'] = float(row[split])
                    table.append(r)
                quotes[t] = table

        except FileNotFoundError:
            print("No file", t)
    return quotes

quotes = readCsv('Quandl',4,6,7)

for t in tickers:
    if len(t)<=0:
        continue
    try:
        qts = quotes[t]
    except KeyError:
        continue
    path = root + 'quandl2tiingo\\'+ t +'.json'
    with open(path, 'w') as f:  
        json.dump(quotes[t], f)
