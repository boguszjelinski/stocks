# Stock investment backtester (simulator)
This repo contains trading simulators that I use to backtest investing strategies, to go back to the past and see what shares should be bought and how wealthy an investor would become - see [my article](https://github.com/boguszjelinski/stocks/blob/master/dividend-portfolio.pdf). **If you can define your strategy and want me to help you test your strategy just contact me** - you will find my email in the [commit log](https://api.github.com/users/boguszjelinski/events/public). I would be interesting to participate in a greater effort of giving all the wounderful trading theories (risk measures) a try, how they work in real life, not on paper. And have a reference/benchmark in form of a GitHub repository for reproducibility. Let me be onest - my effort is just a draft of a beginning. By the way - there are many backtesters on GitHub, but I wanted to have full control and overview over data sources, flow and output (see 'simulations' directory).

There are three simulators written by me not only to help you get rid of the programing language barrier (Java, Julia, Python; an old one in C) but also to verify results. Before any optimization the Java one is the fastest (well, it calls Python and Julia to make use of a solver), Julia seems to be very resource-hungry but popularity of the language grows. You will find out how to run them in chapters below. But first you need to download
 historical data. Java runs only on Yahoo file format. Python runs only on Tiingo format, but here we have simple converters from Quandl and Yahoo. All simulators have some hardcoded paths to input data and hardcoded defaults, you (or me :-) ) will have to adjust them by hand. 

One important warning based on my own results - small differences in historical data (and there are vital ones) or simulator implementation can lead to significant differences in the terminal wealth of a long-term strategy. We need better historical data. 

## Tiingo
You need to claim your credentials before you can download data: https://api.tiingo.com/documentation/end-of-day#Software

Then you can get the data with: 
https://api.tiingo.com/tiingo/daily/IBM/prices?startDate=1955-1-1%20&endDate=2020-1-1

get-tiingo.py script uses basic authentication header, user and password need to be stored in a file in two lines. You can get JSON from Tiingo:
```json
{"date":"2016-11-01T00:00:00.000Z","close":23.0,"high":23.55,"low":21.78,"open":22.1,"volume":32216510,"adjClose":22.9218573046,"adjHigh":23.469988675,"adjLow":21.706002265,"adjOpen":22.0249150623,"adjVolume":32216510,"divCash":0.0,"splitFactor":1.0}
```

## Yahoo
I downloaded price and dividend data directly from Yahoo website in 2016 when they were available. I have created dataset with splits separately. These were CSV files, with prices:

    Date,Open,High,Low,Close,Volume,Adj Close
    2016-06-24,92.910004,94.660004,92.650002,93.400002,72894000,93.400002
and dividends:

    Date,Dividends
    2016-05-04,0.030000
In order to see how to download share prices today have a look at get-yahoo.py script. It uses *panda_datareader*. yahoo2tiingo.py has converted my files into Tiingo format. 

## Quandl
Just see here: https://www.quandl.com/tools/python
You will need a API key:

    Date,Open,High,Low,Close,Volume,Ex-Dividend,Split Ratio,Adj. Open,Adj. High,Adj. Low,Adj. Close,Adj. Volume
    1980-12-12,28.75,28.87,28.75,28.75,2093900.0,0.0,1.0,0.42270591588018,0.42447025361603,0.42270591588018,0.42270591588018,117258400.0
 Then you can see in get-quandl.py and quandl2tiingo.py. Lots of hardcodes there, need to be adjusted. 

## Python
There are short shell scripts to run the Python simulator, helpful when you want to spool many threads in the background, it can take an hour to run. To run it by hand just type:
<pre><code>python StockSimulator.py -period_length 12 -max_risk 0.005 -strategy MPT
</code></pre>

This backtester simulates behaviour of an investor, so we need to provide some parameters:
Parameter | Explanation | Default value
----------|-------------|--------------
periods_number | how many times we will change our portfolio | 64
period_length | how many months we will wait before rebalance |  3
history_periods_number | number of periods in sample |  12 
history_period_length | length of a sample period |  period_length
max_risk | maximum risk we accept | 0.001
strategy | a key to an implemented algorithm. Currently three are available - MPT (classic), DIV (four best scoring assets) & DIVOPT (return based solely on dividend yield but model sent to a solver) | MPT
startDate | hardcoded parameter - when we start investing | 2000-01-01

## Java
Java is many times faster in handling internal data structures than Python. Usage has been documented in [manual](https://github.com/boguszjelinski/stocks/blob/master/manual.pdf). The only change since then was migration to Maven: 
<pre><code>mvn exec:java -D"exec.mainClass"="no.bogusz.portfolio.StockSimulator"
</code></pre>
## Julia
You can run the Julia version without any parameter, you can see the default values of six parameters below: 
<pre><code>julia StockSimulator.jl 12 3 64 3 0.003 DIV
</code></pre>
These parameters are: history_periods_number, history_period_length, periods_number, period_length, max_risk, strategy. The start date is hardcoded - 2000-01-01.
