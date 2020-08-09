using JSON
using DelimitedFiles
using Dates
#using CovarianceEstimation
using StatsBase
using JuMP
using Ipopt

function findFirstQuote(dateFrom, data)
    for (index, value) in enumerate(data)
        dt = SubString(value["date"], 1, 10)
        if Date(dt) > dateFrom
            return index
            break
        end
    end   
    return -1
end

function findLastQuote(dateTo, data)
    for (index, value) in enumerate(data)
        dt = SubString(value["date"], 1, 10)
        if Date(dt) > dateTo
            return index -1 # <= so we have to find a date after the period (from, to>
            break
        end
    end   
    return -1
end

function sumDivid(idxFrom, idxTo, data)
    sum = 0.0
    for i = idxFrom:idxTo
        sum += data[i]["divCash"]
    end
    return sum
end

# see also https://github.com/mateuszbaran/CovarianceEstimation.jl

periods_number = 2
period_length = 3

quotes = Dict()
path = string("C:\\cygwin64\\home\\dell\\DIVID\\")
sp100 = string(path , "GIT\\tiingo\\SP100.txt")
tickers = readdlm(sp100, '\t', String, '\n')  
for t in tickers
    file = string(path, "Tiingo\\", t ,".json")
    cont = String(read(file))
    if length(cont)>3 && !occursin("not found", cont)
        j = JSON.parse(cont)
        quotes[t] = j
        #println(t, j[1]["date"])
    end
end

fromDate = Date("2000-01-01")
toDate = Date("2000-03-31")
benef = []
for t in tickers
    if !haskey(quotes, t)
        continue
    end
    benefits = Float16[]
    for i = 1:periods_number
        first = findFirstQuote(fromDate + Dates.Month((i-1)*period_length), quotes[t])
        last = findLastQuote(toDate + Dates.Month((i-1)*period_length), quotes[t])
        if first <1 || last < 1
            break # this ticker will not be put to solver
        end
        quote1 = quotes[t][first]
        quote2 = quotes[t][last]
        push!(benefits, (quote2["close"] - quote1["close"] + sumDivid(first, last, quotes[t])) / quote1["close"]) # add dividends
    end
    if length(benefits) == periods_number # data for this ticker covers all periods
        push!(benef, benefits)
    end
end

X = zeros(periods_number, length(benef))
for i = 1:length(benef)
    for j = 1: periods_number
        X[j,i] = benef[i][j]
    end
end
Mean, C = mean_and_cov(X)

#LSE = LinearShrinkage # - Ledoit-Wolf target + shrinkage
#method = LSE(ConstantCorrelation())
#S_ledoitwolf = cov(method, X)

min_reve = 0.035
N = length(benef)
m = Model(with_optimizer(Ipopt.Optimizer))
@variable(m, 0 <= x[i=1:N] <= 1)
@objective(m,Max,sum(x[i] * Mean[i] for i = 1:N))
@constraint(m, sum(x[j] * sum(x[i] * C[i,j] for i = 1:N) for j = 1:N) <= min_reve) 
@constraint(m, sum(x[i] for i = 1:N) ==1.0)
status = optimize!(m)

for i= 1:N 
    println(getvalue(x[i]))
end 


