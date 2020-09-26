import os 
import csv
import json
from datetime import datetime

root = "C:\\home\\dell\\DIVID\\History\\"
tickers = open(root + "SP100.txt").read().split('\n')

def readHistory(catalog):
    quotes = {}
    for t in tickers:
        if len(t)<=0:
            continue
        path = root + catalog + '\\'+ t +'.json' 
        if os.path.getsize(path)>100:
            input_file = open(path)
            quotes[t] = json.load(input_file)
    return quotes

def readCsv(catalog, close, divid, split):
    quotes = {}
    for t in tickers:
        if len(t)<=0:
            continue
        ext = '.json'
        if catalog == 'Quandl' or catalog == 'Yfinance' :
            ext = '.csv'
        path = root + catalog + '\\'+ t + ext # in fact it is a CSV
        try:
            with open(path, 'r') as f:
                q = list(csv.reader(f, delimiter=','))
                table = []
                for row in q:
                    if row[0]=='timestamp' or row[0]=='Date':
                        continue
                    r = {}
                    try:
                        r['date'] = row[0]
                        r['close'] = float(row[close])
                        r['divCash'] = float(row[divid])
                        r['splitFactor'] = float(row[split])
                        # Yfinance only 
                        if r['splitFactor'] == 0.0:
                            r['splitFactor'] == 1.0
                        table.append(r)
                    except ValueError: # Yfinance
                        print("ValueError", t, r)
                quotes[t] = table

        except FileNotFoundError:
            print("No file", t)
    return quotes

def compare(one, two):
    price_good_count=0
    price_bad_count =0
    divid_good_count=0
    divid_bad_count =0
    split_good_count=0
    split_bad_count =0

    for s in tickers:
        #print(s, len(one[s]), len(two[s]))
        if len(s)<=0 or s not in one or s not in two:
            continue
        for o in one[s]:
            # find that quote in the other set
            price_is_bad = False
            price_is_good= False
            divid_is_bad = False
            divid_is_good= False
            split_is_bad = False
            split_is_good= False
            
            for t in two[s]:
                if o['date'][0:10]==t['date'][0:10]: 
                    if abs(o['close'] - t['close']) > 0.05:
                        price_is_bad = True
                        #print("Sym:",s," Date:", o['date'][0:10], " different 'close':",o['close'],"<>",t['close'])
                    else:
                        price_is_good = True
                    if abs(o['divCash'] - t['divCash']) > 0.1:
                        #print("Sym:",s," Date:", o['date'][0:10], " different 'divCash':",o['divCash'],"<>",t['divCash'])
                        divid_is_bad = True
                    elif o['divCash'] != 0.0 or t['divCash'] != 0.0: # don't count zeros
                        divid_is_good = True # just to have an indication that a date was found
                    if abs (o['splitFactor'] - t['splitFactor']) > 0.1:
                        #print("Sym:",s," Date:", o['date'][0:10], " different 'splitFactor':",o['splitFactor'],"<>",t['splitFactor'])
                        split_is_bad= True
                    elif o['splitFactor'] != 1.0 or t['splitFactor'] != 1.0: # don't count "no split"
                        split_is_good = True
                    break
            if price_is_bad:
                price_bad_count = price_bad_count+1
            if price_is_good:
                price_good_count = price_good_count +1
            if divid_is_bad:
                divid_bad_count = divid_bad_count+1
            if divid_is_good:
                divid_good_count = divid_good_count +1
            if split_is_bad:
                split_bad_count = split_bad_count+1
            if split_is_good:
                split_good_count = split_good_count +1
    print ("Price good count", price_good_count)
    print ("Price bad count", price_bad_count)
    print ("Divid good count", divid_good_count)
    print ("Divid bad count", divid_bad_count)
    print ("Split good count", split_good_count)
    print ("Split bad count", split_bad_count)
print("START: ", datetime.now())
print("Loading yahoo2tiingo")
yahoo = readHistory('yahoo2tiingo')
print("Loading tiingo")
tiingo = readHistory('tiingo')
#vantage = readCsv('AlfaVantage',4,7,8)
print("Loading Quandl")
quandl = readCsv('Quandl',4,6,7)
#print("Loading Yfinance")
#yfinance = readCsv('Yfinance',4,6,7)
#print(tiingo['AA'])
#print(len(quandl['AA']))
#print(len(quandl))


print('Check tiingo against yahoo:')
compare(tiingo, yahoo)
print('Check quandl against yahoo:')
compare(quandl, yahoo)

#print('Check yahoo against tiingo:')
#compare(yahoo, tiingo)

#print('Check tiingo against vantage:')
#compare(tiingo, vantage)
#print('Check vantage against tiingo:')
#compare(vantage, tiingo)

#print('Check tiingo against quandl:')
#compare(tiingo, quandl)
#print('Check tiingo against yfinance:')
#compare(tiingo, yfinance)

print("STOP: ", datetime.now())