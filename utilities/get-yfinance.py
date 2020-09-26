import yfinance as yf

path = 'C:\\cygwin64\\home\\dell\\DIVID\\'
file1 = open(path + 'GIT\\tiingo\\SP100.txt', 'r') 
tickers = file1.read().splitlines()

for t in tickers:
    try:
        data = yf.download(t, start='1985-1-1', end='2020-9-1', progress=False) # Yfinance2
        #tickerData = yf.Ticker(t)
        #data = tickerData.history(period='1d', start='1985-1-1', end='2020-9-1')
        data.to_csv(path+ 'Yfinance2\\' +t+ '.csv')
    except: 
        print ("Exception for:", t)

# - BNI: No data found for this date range, symbol may be delisted
# - KFT: No data found for this date range, symbol may be delisted
# - LEH: No data found for this date range, symbol may be delisted
# - TYC: No data found for this date range, symbol may be delisted
