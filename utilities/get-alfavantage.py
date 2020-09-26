import httplib2
import time
path = 'C:\\cygwin64\\home\\dell\\DIVID\\'
file1 = open(path + 'alfarest.txt', 'r') 
tickers = file1.read().splitlines()

h = httplib2.Http(".cache")
i=0
for t in tickers:
    print (t)
    url = 'https://www.alphavantage.co/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol=' +t+ '&outputsize=full&apikey=Y1E6XFJHV5YFV6JN&datatype=csv'
    outFile = path+ 'AlfaVantage\\' +t+ '.json'
    print (outFile)
    resp, content = h.request(url, "GET")
    f = open(outFile, "ab")
    f.write(content)
    f.close()
    i = i +1 
    if i == 5:
        i=0
        time.sleep(90) # 90secs API allows for 5 calls/minute
    
