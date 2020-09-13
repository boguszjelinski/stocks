#/bin/sh
javac -cp ./commons-math3-3.6.1.jar StockSimulator.java
java -cp ./commons-math3-3.6.1.jar;. StockSimulator -risk 0.005 -rebalance_after 3 -estimation_number 12 -estimation_length 3 -strategy MPT