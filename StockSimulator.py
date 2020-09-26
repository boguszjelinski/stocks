import json
import os 
import sys
from datetime import date, datetime, timedelta
import dateutil.relativedelta
from cvxpy import *
import numpy as np
import pyscipopt.scip as scip

history_periods_number = 12 # in sample
history_period_length = 3
periods_number = 64
period_length = 3
max_risk = 0.001
strategy = 'MPT'

if len(sys.argv) == 7: 
    for i, arg in enumerate(sys.argv):
        if arg == "-period_length":
            period_length = int(sys.argv[i+1])
            history_period_length = period_length
            periods_number = int(16*12 / period_length)
        if arg == "-max_risk":
            max_risk = float(sys.argv[i+1])
        if arg == "-strategy":
            strategy = sys.argv[i+1]

print("history_periods_number: ", history_periods_number)
print("history_period_length:", history_period_length)
print("periods_number", periods_number)
print("period_length:", period_length)
print("max_risk: ", max_risk)

print("START: ", datetime.now())
root = "C:\\home\\dell\\DIVID\\History\\"
history_dir = 'yahoo2tiingo'
summary_file = 'summary.txt'
sp100 = root + "SP100.txt"
tickers = open(sp100).read().split('\n')

def readHistory():
    quotes = {}
    for t in tickers:
        if len(t)<=0:
            continue
        path = root + history_dir + '\\'+ t +'.json'
        try:
            if os.path.getsize(path)>100:
                input_file = open(path)
                quotes[t] = json.load(input_file)
        except FileNotFoundError:
            quotes[t]=[]
    return quotes

def findFirstQuote(dateFrom, symbol): # first after the date: (a,b>
    if symbol not in data:
        return -1
    pastExists = False
    for row in data[symbol]:
        dt = date.fromisoformat(row['date'][0:10])
        if not pastExists and dt <= dateFrom:
            pastExists = True
        if dt > dateFrom: # > means skip "==dateFrom":
            if pastExists == True:
                return data[symbol].index(row)
            return -1
            break
    return -1

def findLastQuote(dateTo, symbol):
    if symbol not in data:
        return -1
    for row in data[symbol]:
        dato = row['date'][0:10]
        dt = date.fromisoformat(dato)
        if dt >= dateTo: # (from, to>
            idx = data[symbol].index(row)
            if dt > dateTo:
                return idx-1
            return idx
    return -1

def sumDivid(idxFrom, idxTo, symbol):
    sum = 0.0
    for i in range (idxFrom, idxTo):
        sum += data[symbol][i]["divCash"]
    return sum

def solve(rets):
    try:
        benf = np.array(rets)
        mu = benf.mean(axis=1) # to take the mean of each row
        x = Variable(len(mu))
        covar = np.cov(rets)
        risk = quad_form(x, covar)
        ret = mu.T@x
        constraints = [sum(x) <= 1.0, x>=0, risk<=max_risk]
        Problem(Maximize(ret), constraints).solve(solver=SCS)
        weights = [0] * len(mu)
        for i in range(len(mu)):
            weights[i] = x.value[i]
        return weights
    except:
        print("Solver error")
        return []

def solveDiv(benf):
    means = {}
    # what was the mean value of dividend ratio troughout history
    for k in benf.keys():
        if np.var(benf[k]) > max_risk:
            continue
        means[k] = np.average(benf[k])
    # now get the best ones
    srtd = {k: v for k, v in sorted(means.items(), key=lambda item: item[1], reverse=True)}
    # print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    # for k in benf.keys():
    #     print(k,benf[k])
    #     print(k,np.var(benf[k]))
    # print("**************************************")
    # print(srtd)
    # print("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
    exit()
    stocks_in_portfolio = 4
    x = [1/stocks_in_portfolio] * stocks_in_portfolio
    syms = []
    i=0
    for k in srtd.keys():
        if i == stocks_in_portfolio:
            break
        syms.append(k)
        i = i + 1
    return syms, x

def findNewPortfolio(startDate, numb_of_periods, numb_of_months, method):
    fromDate = startDate - dateutil.relativedelta.relativedelta(months=numb_of_periods * numb_of_months)
    benef = {}
    for t in tickers:
        benefits = [0]*numb_of_periods
        complete = True
        for p in range (0, numb_of_periods):
            first = findFirstQuote(fromDate + dateutil.relativedelta.relativedelta(months= p*numb_of_months), t)
            last  = findLastQuote(fromDate + dateutil.relativedelta.relativedelta(months= (p+1)*numb_of_months), t)
            if first <0 or last < 0:
                complete = False
                break # this ticker will not be put to solver
            quote1 = data[t][first]
            quote2 = data[t][last]
            if method == "MPT":
                benefits[p] = (quote2["close"] - quote1["close"] + sumDivid(first, last, t)) / quote1["close"]
            else:    # any DIV
                benefits[p] = sumDivid(first, last, t) / quote1["close"]
        if complete: # data for this ticker covers all periods
            benef[t] = benefits
    symbols = benef.keys()
    b = [None] * len(symbols)
    i = 0
    for k in symbols:
        b[i] = benef[k]
        i=i+1

    if method == 'MPT' or method == 'DIVOPT':
        return symbols, solve(b)
    else: # DIV
        return solveDiv(benef)

def sellPortfolio(portf, date):
    cash = 0.0
    for sym in portf.keys():
        vol = portf[sym]
        idx = findLastQuote(date, sym)
        price = data[sym][idx]["close"]
        cash += price * vol
        print('(',sym,',',(int)(vol),',', price,')')
    print("Sold for: ", round(cash, 2), "\n----------------------------------------")
    return cash

def sumUpDividendsAndSplits(sym, dateFrom, dateTo):
    i1 = findFirstQuote(dateFrom, sym)
    i2 = findLastQuote(dateTo, sym)
    div = 0.0
    split = 1.0
    div_before = False # dividend paid before split means that dividend will be counted wrongly (here we sum up "by share")
    err = 0
    for i in range(i1,i2+1): # including i2
        if (not div_before and # do we have to check more?
                            div > 0.0 and # the sum of previous rows is positive - the dividend was paid before
                            data[sym][i]["splitFactor"] != 1.0): # and now there is a split
            div_before = True # which means you shold not multiply volume (what we do outside this func)
            print("<dividend paid before split: ", sym, "dateFrom: ", dateFrom, ">")
        div += data[sym][i]["divCash"]
        split *= data[sym][i]["splitFactor"]
    if div_before:
        div /= split # we have to correct the value (it will be wrongly multiplied by volume later)
    return div, split

def paidDividends(portf, dateFrom, dateTo):
    cash = 0.0
    for sym in portf.keys():
        vol = portf[sym]
        div, split = sumUpDividendsAndSplits(sym, dateFrom, dateTo)
        if split != 1.0:
            print(" Split: ", sym, " by ", split)
            vol *= split
            portf[sym] = vol
        cash += div * vol  # TODO: the volume corrected by split might concern dividend paid before split - total will be higher/wrong
        print('(', sym, ',', round(div * vol, 2), ')')
    print("Dividends paid: ", round(cash, 2))
    return cash, portf

def buyPortfolio(symbols, weights, budget, date):
    portf = {}
    left = budget
    i = 0
    for sym in symbols:
        weight = weights[i]
        i=i+1
        if weight < 0.01: # ignore shares with percentage/participation below 1%
            continue
        if weight > 1.0: # solver somtimes gives more that 100% ;)
            weight = 1.0
        idx = findLastQuote(date, sym)
        price = data[sym][idx]["close"]
        b = budget * weight
        vol = b // price # floor
        if vol > 0:
            portf[sym] = vol
            left -= vol * price
            print('(', sym, ',', round(weight*100, 1), '%', ',',(int)(vol), ',', price, ')')
    print("\nBought for: ", round((budget-left), 2))
    return portf, left

data = readHistory()
startDate = date.fromisoformat('2000-01-01')
portfolio = {} # empty
wallet = 100000
date = date.fromisoformat('2030-01-01')     # to skip some calculations at start
assets = 0 # for final summary

for i in range (0, periods_number+1): 
    print("========================================================")
    prevDate = date
    date = startDate + dateutil.relativedelta.relativedelta(months= i*period_length)
    print("Rebuild on ", date)
    if len(portfolio)>0:
        div, portfolio = paidDividends(portfolio, prevDate, date)
        wallet += div
        wallet += sellPortfolio(portfolio, date)
    portfolio = {} # in case solver fails
    assets = wallet
    newStocks, newWeights = findNewPortfolio(date, history_periods_number, history_period_length, strategy)
    #print(newStocks, newWeights)
    if len(newWeights) > 0: # no solver error
        portfolio, left = buyPortfolio(newStocks, newWeights, wallet, date)
        wallet = left
    # else: just skip rebuild, we will keep this portfolio
    print("Wallet after purchase: ", round(wallet, 2))

print("STOP: ", datetime.now())

f = open(root + summary_file, "a")
f.write('%s, %s, %d, %5.3f, %13.2f\n' % (history_dir, strategy, period_length, max_risk, assets))
f.close()

# ben = [[0.1383,-0.2029,-0.0649,0.0038,-0.0599,-0.1795,-0.4472,0.0072,0.6517,-0.1539,0.0981,0.2969],
#     [0.2228,-0.2019,0.158,0.3695,-0.1357,-0.0904,-0.3103,0.1483,0.1258,-0.2361,0.0554,0.3092],
#     [0.1845,-0.2079,0.1593,0.1265,0.1045,-0.0654,-0.1995,0.2308,0.1024,0.0522,0.1166,-0.0715],
#     [-0.0812,-0.0574, 0.1923,0.0344,0.0462,0.0398,-0.0862,0.0891,0.0425,0.1479,0.0536,-0.0954]]
#s, b = findNewPortfolio(date.fromisoformat('2010-01-01'), 12, 3, tickers, "MPT")
#print(s, b)
#print(b.keys())
#print(b['AAPL'])
# print(data['AA'][0]['date'])
# print(findFirstQuote(date.fromisoformat('2016-11-15'),'AA'))
# print(findLastQuote(date.fromisoformat('2016-11-17'),'AA'))
#solve(ben)
