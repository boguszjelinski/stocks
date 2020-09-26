import requests 
import pandas as pd 

#def get_eod_data(symbol=”AAPL.US“, api_token=”OeAFFmMliFG5orCUuwAKQ8l4WWFQ67YX”, session=None): 
path = 'C:\\cygwin64\\home\\dell\\DIVID\\'
symbol='AA'
session = requests.Session() 
url = "https://eodhistoricaldata.com/api/eod/%s" % symbol 
params = { 
    "api_token": "5f691001b3e7b4.94969703"
} 
r = session.get(url, params=params) 
f = open(path + "Eod\\" + symbol + ".csv", "a")
f.write(r.text)
f.close()