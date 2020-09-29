python StockSimulator.py -period_length 12 -max_risk 0.005 -strategy $2 -history_period_length $1 -history_periods_number 6 > sims-py/table-$2-$1-6.txt
python StockSimulator.py -period_length 12 -max_risk 0.005 -strategy $2 -history_period_length $1 -history_periods_number 12 > sims-py/table-$2-$1-12.txt
python StockSimulator.py -period_length 12 -max_risk 0.005 -strategy $2 -history_period_length $1 -history_periods_number 18 > sims-py/table-$2-$1-18.txt
python StockSimulator.py -period_length 12 -max_risk 0.005 -strategy $2 -history_period_length $1 -history_periods_number 24 > sims-py/table-$2-$1-24.txt
