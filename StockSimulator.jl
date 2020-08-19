using JSON
using DelimitedFiles
using Dates
using StatsBase
using JuMP
using Ipopt

path = string("C:\\cygwin64\\home\\dell\\DIVID\\")
sp100 = string(path , "GIT\\tiingo\\SP100.txt")
history_periods_number = 12 # in sample
history_period_length = 1
periods_number = 192
period_length = 1
max_risk = 0.003

if length(ARGS) == 5
    history_periods_number = parse(Int64, ARGS[1])
    history_period_length = parse(Int64, ARGS[2])
    periods_number = parse(Int64, ARGS[3])
    period_length = parse(Int64, ARGS[4])
    max_risk = parse(Float64, ARGS[5])
end

println(history_periods_number)
println(history_period_length)
println(periods_number)
println(period_length)
println(max_risk)

function readHistory(tickers)
    quotes = Dict()
    for t in tickers
        file = string(path, "Tiingo\\", t ,".json")
        cont = String(read(file))
        if length(cont)>3 && !occursin("not found", cont)
            j = JSON.parse(cont)
            quotes[t] = j
            #println(t, j[1]["date"])
        end
    end
    return quotes
end

SP100symbols = readdlm(sp100, '\t', String, '\n')  
data = readHistory(SP100symbols)

function findFirstQuote(dateFrom, symbol)
    pastExists = 0
    for (index, value) in enumerate(data[symbol])
        dt = Date(SubString(value["date"], 1, 10))
        if pastExists == 0 && dt <= dateFrom
            pastExists = 1
        end
        if dt > dateFrom # > means skip "==dateFrom"
            if pastExists == 1
                return index
            end
            return -1
            break
        end
    end   
    return -1
end

function findLastQuote(dateTo, symbol)
    for (index, value) in enumerate(data[symbol])
        dt = Date(SubString(value["date"], 1, 10))
        if dt >= dateTo # (from, to>
            if dt > dateTo
                return index-1
            end
            return index
        end
    end   
    return -1
end

function sumDivid(idxFrom, idxTo, symbol)
    sum = 0.0
    for i = idxFrom:idxTo
        sum += data[symbol][i]["divCash"]
    end
    return sum
end

function findNewPortfolio(startDate, numb_of_periods, numb_of_months, quotes, tickers)
    fromDate = startDate - Dates.Month(numb_of_periods * numb_of_months)
    benef = []
    symbols = []
    for t in tickers
        if !haskey(quotes, t)
            continue
        end
        benefits = Float16[]
        for p = 1:numb_of_periods
            first = findFirstQuote(fromDate + Dates.Month((p-1)*numb_of_months), t)
            last = findLastQuote(fromDate + Dates.Month(p*numb_of_months), t)
            if first <1 || last < 1
                break # this ticker will not be put to solver
            end
            quote1 = quotes[t][first]
            quote2 = quotes[t][last]
            push!(benefits, (quote2["close"] - quote1["close"] + sumDivid(first, last, t)) / quote1["close"]) # add dividends
        end
        if length(benefits) == numb_of_periods # data for this ticker covers all periods
            push!(benef, benefits)
            push!(symbols, t)
        end
    end

    obs = zeros(numb_of_periods, length(benef))
    for k = 1:length(benef)
        for l = 1: numb_of_periods
            obs[l,k] = benef[k][l]
        end
    end
    Mean, C = mean_and_cov(obs)
    
    # see also https://github.com/mateuszbaran/CovarianceEstimation.jl

    
    N = length(benef)
    m = Model(with_optimizer(Ipopt.Optimizer, print_level=0))
    @variable(m, 0 <= x[i=1:N] <= 1)
    @objective(m,Max,sum(x[i] * Mean[i] for i = 1:N))
    @constraint(m, sum(x[j] * sum(x[i] * C[i,j] for i = 1:N) for j = 1:N) <= max_risk) 
    @constraint(m, sum(x[i] for i = 1:N) ==1.0)

    # redirect_stdout((()->optimize!(model)),open("/dev/null", "w")) do
    status = optimize!(m)

    result = []
    for k= 1:N 
        if (getvalue(x[k])>0.01)
            push!(result,(symbols[k],getvalue(x[k])))
        end
    end 

    return result
end

function sellPortfolio(port, date)
    cash = 0.0
    for (index, value) in enumerate(port)
        sym, vol = value
        idx = findLastQuote(date, sym)
        price = data[sym][idx]["close"]
        cash += price * vol
        print("($sym, $vol, $price)")
    end
    println("\nSold for: $(round(cash, digits=2))\n----------------------------------------")
    return cash
end

function sumUpDividendsAndSplits(sym, dateFrom, dateTo)
    i1 = findFirstQuote(dateFrom, sym)
    i2 = findLastQuote(dateTo, sym)
    div = 0.0
    split = 1.0
    for i = i1:i2
        div += data[sym][i]["divCash"]
        split *= data[sym][i]["splitFactor"]
    end
    return div, split
end

function paidDividends(portf, dateFrom, dateTo)
    cash = 0.0
    for (index, value) in enumerate(portf)
        sym, vol = value
        div, split = sumUpDividendsAndSplits(sym, dateFrom, dateTo)
        if split != 1.0
            println(" Split: $sym by $split")
            vol *= split
            portf[index] = (sym, vol)
        end
        cash += div * vol  # TODO: the volume corrected by split might concern dividend paid before split - total will be higher/wrong
        print("($sym, $(div * vol))")
    end
    println("\nDividends paid: $(round(cash, digits=2))")
    return cash, portf
end

function buyPortfolio(shares, budget, date)
    port = []
    left = budget
    for (index, value) in enumerate(shares)
        sym, weight = value
        idx = findLastQuote(date, sym)
        price = data[sym][idx]["close"]
        b = budget * weight
        vol = floor(b / price)
        if vol > 0
            push!(port, (sym, vol))
            left -= vol * price
            print("($sym, $(round(weight*100,digits=1))%, $vol, $price)")
        end
    end
    println("\nBought for: $(round((budget-left), digits=2))")
    return port, left
end

startDate = Date("2000-01-01")
portfolio = [] # empty
wallet = 100000
date = Date("3000-01-01")

for i = 1:periods_number
    global wallet
    global portfolio
    global date # so that it doesn't fail in runtime  with prevDate
    println("========================================================")
    prevDate = date
    date = startDate + Dates.Month((i-1)*period_length)
    println("Rebuild on $date")
    div, portfolio = paidDividends(portfolio, prevDate, date)
    wallet += div
    wallet += sellPortfolio(portfolio, date)
    newStocks = findNewPortfolio(date, history_periods_number, history_period_length, data, SP100symbols)
    portfolio, left = buyPortfolio(newStocks, wallet, date)
    wallet = left
    println("Wallet: $(round(wallet, digits=2))")
end
