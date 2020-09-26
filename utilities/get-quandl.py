import quandl 

# https://www.quandl.com/tools/python
# The Quandl Python module is free. If you would like to make more than 50 calls a day, however, 
# you will need to create a free Quandl account and set your API key:

quandl.ApiConfig.api_key = "moSKBusVJsRSiWccxpJu"

path = 'C:\\cygwin64\\home\\dell\\DIVID\\'
file1 = open(path + 'GIT\\tiingo\\SP100.txt', 'r') 
tickers = file1.read().splitlines()

for t in tickers:
    try:
        data = quandl.get("WIKI/"+t, start_date="1980-01-01", end_date="2020-09-01")
        data.to_csv(path+ 'Quandl2\\' +t+ '.csv')
    except: 
        print ("Exception for:", t)
# Exception for: BNI
# Exception for: BUD
# Exception for: CCU
# Exception for: EP
# Exception for: HET
# Exception for: HNZ
# Exception for: KFT
# Exception for: LEH
# Exception for: MER
# Exception for: NYX
# Exception for: SLE
# Exception for: WB



