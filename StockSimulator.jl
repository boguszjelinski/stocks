using JSON
using DelimitedFiles
using Dates

using StatsBase
using JuMP
using Ipopt

path = string("C:\\cygwin64\\home\\dell\\DIVID\\")
sp100 = string(path , "GIT\\tiingo\\SP100.txt")
history_periods_number = 12 # in sample
history_period_length = 3
periods_number = 4 
period_length = 3
budget = 1000.0

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
        dt = SubString(value["date"], 1, 10)
        if Date(dt) >= dateTo # (from, to>
            return index
            break
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

    max_risk = 0.003
    N = length(benef)
    m = Model(with_optimizer(Ipopt.Optimizer))
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

startDate = Date("2000-01-01")
for i = 1:periods_number
    date = startDate + Dates.Month((i-1)*period_length)
    print(findNewPortfolio(date, history_periods_number, history_period_length, data, SP100symbols))
end

