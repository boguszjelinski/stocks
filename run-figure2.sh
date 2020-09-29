python StockSimulator.py -period_length 3 -max_risk 0.001 -strategy MPT > sims-py/temp1.txt &
python StockSimulator.py -period_length 3 -max_risk 0.150 -strategy MPT > sims-py/temp2.txt &
python StockSimulator.py -period_length 3 -max_risk 0.001 -strategy DIV > sims-py/temp3.txt &
python StockSimulator.py -period_length 3 -max_risk 0.150 -strategy DIV > sims-py/temp4.txt &