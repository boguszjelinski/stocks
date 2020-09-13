import json
import os 
from datetime import date, timedelta
import dateutil.relativedelta
from cvxpy import *
import numpy as np
import pyscipopt.scip as scip

sp100 = "C:\\home\\dell\\DIVID\\GIT\\tiingo\\SP100.txt"
text_file = open(sp100, "r")
tickers = open(sp100).read().split('\n')

def readHistory(tkrs):
    quotes = {}
    for t in tkrs:
        if len(t)<=0:
            continue
        path = 'C:\\home\\dell\\DIVID\\Tiingo\\'+ t +'.json'
        if os.path.getsize(path)>100:
            input_file = open(path)
            quotes[t] = json.load(input_file)
    return quotes

data = readHistory(tickers)

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
    benf = np.array(rets)
    mu = benf.mean(axis=1) # to take the mean of each row
    x = Variable(len(mu))
    covar = np.cov(rets)
    risk = quad_form(x, covar)
    ret = mu.T@x
    constraints = [sum(x) <= 1.0, x>=0, risk<=0.015]
    Problem(Maximize(ret), constraints).solve(solver=SCS)
    for i in range(len(mu)):
        print(x.value[i])

def findNewPortfolio(startDate, numb_of_periods, numb_of_months, tickers, method):
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

    if method == 'MPT':
        return symbols, solve(b)
    # elif method == "DIVOPT":
    #     return solve(numb_of_periods, benef, symbols)
    # else:
    #     return solveDiv(benef, symbols)
    return benef


# ben = [[0.1383,-0.2029,-0.0649,0.0038,-0.0599,-0.1795,-0.4472,0.0072,0.6517,-0.1539,0.0981,0.2969],
#     [0.2228,-0.2019,0.158,0.3695,-0.1357,-0.0904,-0.3103,0.1483,0.1258,-0.2361,0.0554,0.3092],
#     [0.1845,-0.2079,0.1593,0.1265,0.1045,-0.0654,-0.1995,0.2308,0.1024,0.0522,0.1166,-0.0715],
#     [-0.0812,-0.0574, 0.1923,0.0344,0.0462,0.0398,-0.0862,0.0891,0.0425,0.1479,0.0536,-0.0954]]
s, b = findNewPortfolio(date.fromisoformat('2010-01-01'), 12, 3, tickers, "MPT")
print(s, b)
#print(b.keys())
#print(b['AAPL'])
# print(data['AA'][0]['date'])
# print(findFirstQuote(date.fromisoformat('2016-11-15'),'AA'))
# print(findLastQuote(date.fromisoformat('2016-11-17'),'AA'))
#solve(ben)
