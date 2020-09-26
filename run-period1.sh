date > sims-py/$2-$1-001.txt
python StockSimulator.py -period_length $1 -max_risk 0.001 -strategy $2 >> sims-py/$2-$1-001.txt &
python StockSimulator.py -period_length $1 -max_risk 0.003 -strategy $2 > sims-py/$2-$1-003.txt &
python StockSimulator.py -period_length $1 -max_risk 0.005 -strategy $2 > sims-py/$2-$1-005.txt &
python StockSimulator.py -period_length $1 -max_risk 0.015 -strategy $2 > sims-py/$2-$1-015.txt &
python StockSimulator.py -period_length $1 -max_risk 0.075 -strategy $2 > sims-py/$2-$1-075.txt &
python StockSimulator.py -period_length $1 -max_risk 0.150 -strategy $2 > sims-py/$2-$1-150.txt &
python StockSimulator.py -period_length $1 -max_risk 0.375 -strategy $2 > sims-py/$2-$1-375.txt &
date >> sims-py/$2-$1-375.txt