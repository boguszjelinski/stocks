import httplib2
path = 'C:\\cygwin64\\home\\dell\\DIVID\\'
file1 = open(path + 'GIT\\tiingo\\SP100.txt', 'r') 
tickers = file1.read().splitlines()

passwdFile = open(path + 'tii-passwd.txt', 'r') 
creds = passwdFile.read().splitlines()
h = httplib2.Http(".cache")
h.add_credentials(creds[0], creds[1]) # Basic authentication
for t in tickers:
    print (t)
    url = 'https://api.tiingo.com/tiingo/daily/' +t+ '/prices?startDate=1955-1-1%20&endDate=2020-1-1'
    outFile = path+ 'Tiingo\\' +t+ '.json'
    print (outFile)
    resp, content = h.request(url, "GET")
    f = open(outFile, "ab")
    f.write(content)
    f.close()
