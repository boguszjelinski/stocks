import pandas_datareader as pdr
import datetime 

path = 'C:\\cygwin64\\home\\dell\\DIVID\\'
file1 = open(path + 'GIT\\tiingo\\SP100.txt', 'r') 
tickers = file1.read().splitlines()

for t in tickers:
    try:
        # https://www.datacamp.com/community/tutorials/finance-python-trading?utm_source=adwords_ppc&utm_campaignid=898687156&utm_adgroupid=48947256715&utm_device=c&utm_keyword=&utm_matchtype=b&utm_network=g&utm_adpostion=&utm_creative=332602034343&utm_targetid=aud-299261629574:dsa-473406585355&utm_loc_interest_ms=&utm_loc_physical_ms=1010996&gclid=CjwKCAjw2Jb7BRBHEiwAXTR4jTzmP9ZUcbTC8_WiZvFCUy3aBDG31iUTphNJdVVdATwKI95y_VSn1RoCu_8QAvD_BwE
        data = pdr.get_data_yahoo(t, start=datetime.datetime(1980, 12, 12), end=datetime.datetime(2020, 9, 1))
        data.to_csv(path+ 'Yahoo\\' +t+ '.csv')
    except: 
        print ("Exception for:", t)
# Exception for: BNI
# Exception for: KFT
# Exception for: LEH
# Exception for: TYC