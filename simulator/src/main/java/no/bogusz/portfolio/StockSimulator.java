package no.bogusz.portfolio;

/* 
 *	Author: Bogusz Jelinski
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
//import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.Covariance;

public class StockSimulator {

	private static final String ROOT_DIR = "/home/dell/DIVID/";
	private static final String OUT_DIR = ROOT_DIR + "GIT/simulations/";
	private static final String HIST_DIR = ROOT_DIR + "History/data/";
	private static final String SHARE_NAMES = ROOT_DIR + "History/SP100.txt";
	private static final String JULIA_CMD = "C:\\Users\\dell\\AppData\\Local\\Julia-0.5.2\\bin\\julia";
	private static final double MIN_SHARE = 0.0001; // minimal share of a stock we accept in a portfolio  
	private static final double BUDGET = 100000.0; 
	private static final int    MAX_STOCKS = 4; // how many stocks we will have in portfolio in "dividend arbitrage" (without solver)
	private static final int 	MINIMIZE = 0;
	private static final int 	MAXIMIZE = 1;
	private static final int 	MONTHS_IN_YEAR = 12;
	
	private static final String	STRATEGY_MPT = "MPT";
	private static final String	STRATEGY_DIV = "DIV";
	
	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
	
	private static List<String> tickers;
	private static Map<String,List<Close>> prices;
	private static Map<String,List<Close>> dividends; // .price will be the dividend paid on a day
	private static Map<String,List<Stock>> stocks;
	private static List<Split> splits;
	
	private static final String style = "<style>" + 
			"table {\n  border-collapse: collapse;\n}\n" + 
			" th {\n  background: #ccc;\n}\n\n" + 
			"th, td {\r  border: 1px solid #ccc;\n  padding: 8px;\n}\n\n" + 
			"tr:nth-child(even) {\n  background: #efefef;\n}\n\n" + 
			"tr:hover {\n  background: #d1d1d1;\n}\n" + 
			"</style>\n"; 
	
	private static class Close  implements Comparable<Close>  {  // quote
		public Close(String date, String price) {
			this.date = LocalDate.parse(date, formatter);
			this.price = new BigDecimal(price);
		}
		public LocalDate date;
		public BigDecimal price;
		
		@Override
		public int compareTo(Close c) {
		    return this.date.compareTo(c.date) * -1; // -1 as data in Yahoo started with 'today', not some date in the past
		}
	}
	
	private static class Split {  
		public Split(String symbol, String date, String ratio) {
			this.symbol = symbol;
			this.date = LocalDate.parse(date, formatter);
			this.ratio = new BigDecimal(ratio);
		}
		public String symbol;
		public LocalDate date;
		public BigDecimal ratio;
	}
	
	private static class Stock {  // quote
		public Stock(String date, String name) {
			try {
				if (date.contains("N/A"))
					this.dividendDate = null;
				else this.dividendDate = sdf.parse(date.replace("\"",""));
			} catch (ParseException e) {
				System.out.println("parsing date failed, date: " + date + ", name: " + name);
				this.dividendDate = null;
			}
			this.name = name.replace("\"","");
		}
		public Date dividendDate;
		public String name;
	}
	
	private static class StockInPortfolio {
		public StockInPortfolio(String ticker, int number) {
			this.ticker = ticker;
			this.number = number;
		}
		public String ticker;
		public int number;
	}
	
	private static class DividentWithComment {
		public DividentWithComment(BigDecimal dividend, String comment) {
			this.comment = comment;
			this.dividend = dividend;
		}
		public String comment;
		public BigDecimal dividend; 
	}
	
	private static class PortfolioWithWallet {
		public PortfolioWithWallet(BigDecimal wallet, List<StockInPortfolio> portfolio, BigDecimal portfolio_value) {
			this.wallet = wallet;
			this.portfolio = portfolio;
			this.portfolio_value = portfolio_value;
		}
		
		public BigDecimal wallet; 
		public List<StockInPortfolio> portfolio;
		public BigDecimal portfolio_value;
	}
	
	// mvn exec:java -D"exec.mainClass"="no.bogusz.portfolio.StockSimulator"
	public static void main(String[] args) throws IOException {

		tickers = readShareSymbols(SHARE_NAMES);
		prices = new HashMap<String,List<Close>>();
		splits = new ArrayList<Split>();
		dividends = new HashMap<String,List<Close>>();
		//stocks = new HashMap<String,List<Stock>>();
		
		for (String symbol: tickers) {
			prices.put(symbol, readHistory(symbol, "-prc", 4));
			dividends.put(symbol, readHistory(symbol, "-div", 1));
			//stocks.put(symbol, readStockName(symbol, "-nme"));
		}
		readSplits("splits.txt");
		
		double[] risks = new double[]{0.001, 0.003, 0.005, 0.015, 0.075, 0.150, 0.375}; // , 0.005, 0.015, 0.075, 0.375
		int[] period_length = new int[]{1,2,3,4,6,12};
		int[] benef_period_length = new int[]{3}; //,6,9,12};
		int[] benef_periods = new int[]{12}; //{6,12,18,24};
		String strat = STRATEGY_DIV;
		// overwriting defaults with command line arguments
		for (int s =0 ; s<args.length; s++) {
	       switch (args[s]) {
	       case "-risk": 
	    	   risks = new double[1];
	    	   risks[0] = Double.parseDouble(args[s+1]);
	    	   break;
	       case "-rebalance_after": 
	    	   period_length = new int[1];
	    	   period_length[0] = Integer.parseInt(args[s+1]); 
	    	   break;
	       case "-estimation_number":
	    	   benef_periods = new int[1];
	    	   benef_periods[0] = Integer.parseInt(args[s+1]);
	    	   break;
	       case "-estimation_length": 
	    	   benef_period_length = new int[1];
	    	   benef_period_length[0] = Integer.parseInt(args[s+1]);
	    	   break;
	       case "-strategy":
	    	   strat = args[s+1];
	    	   break; 
	       }
	    }
		
		System.out.print("\n");
		ExecutorService executor= Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		for (int r = 0; r < risks.length; r ++) { 
			for (int i=0; i<period_length.length; i++) 
			   for (int per = 0; per < benef_periods.length; per++)
				  for (int per_len = 0; per_len < benef_period_length.length; per_len++)
			{
				final int len = period_length[i];
				final int benef_per = benef_periods[per]; // 12

				final int benef_len = len; //benef_period_length[per_len]; // len; in the article I use 'len' - the estimation period should be as long as the rebalance period
				final double risk = risks[r];
				final String strategy = strat;
				
				Runnable thread = new Runnable() {
					public void run() {
						try {
							BigDecimal assets = simulate(strategy, 2000, (MONTHS_IN_YEAR*16)/len, len, 
															benef_per, benef_len, risk, new BigDecimal(BUDGET));
									// year, sim_periods in months, period_length, risk_ret_periods, risk_ret_period_len, min_ret
							System.out.print("(" + strategy + "," + risk + "," + len + ","+ benef_per + ","+ benef_len + "," + assets + ")\n");
							
						} catch (IOException e) {
							e.printStackTrace();
						} 
					}
				};
				executor.execute(thread);
			}
			
		}
		executor.shutdown();
	}
	
	private static BigDecimal simulate (String strategy, int year, int sim_periods, // in month
		    					int period_length, int benef_ret_periods, int benef_ret_period_len,
		    					double return_or_risk, BigDecimal wallet) throws IOException {
		FileWriter fr = new FileWriter(new File(OUT_DIR + strategy + "-" + period_length+"-"
		    	+(return_or_risk+"").replace('.', '_')+"-" + benef_ret_periods +"-"+ benef_ret_period_len +".html"));
		BufferedWriter bw = new BufferedWriter(fr);
		bw.write("<table>" + style + "<BODY>");
		bw.write("Number of periods: " + sim_periods + "<BR>\n");
		bw.write("Period's length: " + period_length + " months<BR>\n");  
		bw.write("Number of periods in risk assessment: " + benef_ret_periods + "<BR>\n");
		bw.write("Min expected return or max risk : " + return_or_risk + "<BR>\n");
		bw.write("Wallet content: " + wallet.toString());
		
		List<StockInPortfolio> portfolio = new ArrayList<StockInPortfolio>();
		PortfolioWithWallet stockAndWallet = null;
		for (int s=0; s<sim_periods; s++) {
			//System.out.println(s);
			// portfolio rebuild on that date means summing up dividends from the previous period
			LocalDate dateTo = LocalDate.parse(year+"-01-01", formatter).plusMonths(s*period_length);
			LocalDate dateFrom = dateTo.minusMonths(period_length);
			bw.write("<BR>---------------------------------------------------------------------------<BR>");
 		
			BigDecimal stockWithDividend = showPortfolio(portfolio, dateFrom, dateTo, bw);
		
			// here we sell the stock virtually
			wallet = wallet.add(stockWithDividend);
// Figure 1.			  
//System.out.println(wallet);
			switch (strategy) {
			case STRATEGY_MPT: 
				stockAndWallet = findBestPortfolioWith_MPT(MAXIMIZE,
									dateTo.minusMonths(benef_ret_periods*benef_ret_period_len),
									benef_ret_period_len, benef_ret_periods, wallet, period_length, 
									return_or_risk, bw);
				break;
			case STRATEGY_DIV:
				stockAndWallet = findAndBuyBestPortfolioWith_DIV(true, // make it simple, do not send to solver?
									dateTo.minusMonths(benef_ret_periods*benef_ret_period_len),
									benef_ret_period_len, benef_ret_periods, wallet, period_length, 
									return_or_risk, MAX_STOCKS, bw);
				break;
							/*	findBestPortfolioWith_MPT(MINIMIZE,
									dateTo.minusMonths(risk_ret_periods*period_length),
									period_length, risk_ret_periods, wallet, MIN_REVE, bw);
							*/	
			}
			portfolio = stockAndWallet.portfolio;
			wallet = stockAndWallet.wallet;
			
		}
		bw.write("</BODY></HTML>");
		bw.close();
		return wallet.add(stockAndWallet.portfolio_value);
	}
	
	private static PortfolioWithWallet findBestPortfolioWith_MPT(int type, LocalDate startDate, 
													int period_length, int periods_number,
													BigDecimal budget, int per_len, double min_r, BufferedWriter bw) throws IOException {
		double[][] benefits = new double[tickers.size()][periods_number]; 
		String[] goodTickers = new String[tickers.size()];
		Close[] lastPrice = new Close[tickers.size()];
		
		int idx =0;
		// this loop creates an array with benefits
		for (String symbol: tickers) {
			List<Close> historicPrices = prices.get(symbol);
			List<Close> historicDividends = dividends.get(symbol);
			Close last = null;
			int i=0;
			for (; i<periods_number; i++) {
				LocalDate fromDate = startDate.plusMonths(i * period_length);
				LocalDate toDate   = startDate.plusMonths((i+1) * period_length);

				List<Close> historicPricesTemp = historicPrices.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && (p.date.isBefore(toDate) || p.date.isEqual(toDate)))) // stopDate belongs to the next period
						.collect(Collectors.toList()); // maybe sorting to secure against different order in source files ?
				
				List<Close> historicDividendsTemp = historicDividends.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && (p.date.isBefore(toDate) || p.date.isEqual(toDate))))
						.collect(Collectors.toList());
				// checking price difference
				if (historicPricesTemp == null || historicPricesTemp.size() == 0) break; // do not take this stock into consideration
				
				last = historicPricesTemp.get(0); 				// TODO: do not ignore splits !!! 
				Close first = historicPricesTemp.get(historicPricesTemp.size() - 1);
				if (first == null || lastPrice == null || first.price == null || first.price.equals(0)) break; // error
				// summing dividends
				BigDecimal divSum = new BigDecimal(0);
				if (historicDividendsTemp != null && historicDividendsTemp.size()>0)
					for (Close div: historicDividendsTemp)
						if (div.price != null)
							divSum = divSum.add(div.price);
				
				benefits[idx][i] = last.price.subtract(first.price).add(divSum).doubleValue();
				benefits[idx][i] /= first.price.doubleValue();
			}
			if (i == periods_number) { // no error, it will go to solver
				goodTickers[idx] = symbol;
				lastPrice[idx] = last; // we assume that the portfolio gets sold a day, or two, before the "portfolio rebuild on" 
				idx++; 
			}
			
		}
		// having an array with benefits we will estimate mean and covariance
		double[][] benef = crympData(benefits,  idx, periods_number);
		double[] mean = countMean (idx, benef, periods_number);
		double[][] cov = Covar(benef); //= myCov(idx, periods_number, benef, mean); // TODO: double[][] cov = new Covariance(benef).getCovarianceMatrix().getData();
		
		String ident = per_len +"-"+ (min_r+"").replace('.', '_')+"-" + periods_number +"-"+ period_length;
		String outFile = OUT_DIR + "solver-"+ ident +".out";
		if (type == MINIMIZE) {
			String cmd = OUT_DIR + "solver-"+ ident +".py";
			constructPythonTask(cov, mean, min_r, cmd, outFile);
			runOsCmd("python " + cmd);
		}
		else { // MAXIMIZE
			/*String cmd = OUT_DIR + "solver-"+ ident +".jl";
			constructJuliaTask(cov, mean, min_r, cmd, outFile);
			runJulia(JULIA_CMD + " "+ cmd + " >> julia.out 2>>nul");
			*/
			String cmd = OUT_DIR + "solver-"+ ident +".py";
			constructPythonTaskMax(cov, mean, min_r, cmd, outFile);
			runOsCmd("python " + cmd);
		}
		return createPortfolioFromSolver(budget, idx, goodTickers, lastPrice, bw, outFile);
	}
	
	private static double[][] Covar (double[][] benef) {
		Covariance covar = new Covariance(MatrixUtils.createRealMatrix(benef).transpose().getData());
		RealMatrix S = covar.getCovarianceMatrix();
		return S.getData();
	}
	
	private static double[] countMean(int share_count, double[][] data, int periods_number) {
		double[] mean = new double[share_count];  
		for (int i=0; i<share_count; i++) {
	      mean[i] = 0;
	      for (int k=0; k<periods_number; k++) mean[i] += data[i][k];
	      mean[i] /= periods_number;
		}
		return mean;
	}
	
	private static double[][] crympData (double[][] data, int share_count, int periods_number) {
		double[][] benef = new double[share_count][periods_number]; // number of stocks sent to solver maybe smaller than S&P100; due to missing data 
		for (int j=0; j<share_count; j++)
			for (int i=0; i<periods_number; i++)
				benef[j][i] = data[j][i];
		return benef;
	}
	
	// this is the MIN model - minimizing risk with assumed level of minimum earning
	private static void constructPythonTask(double[][] cov, double[] mean, double min_reve, String cmd, String out) throws IOException {
		FileWriter fw = new FileWriter(cmd);    
        fw.write("from cvxopt import matrix\n");
        fw.write("from cvxopt.solvers import qp\n");
        fw.write("import numpy as np\n");
        fw.write("n = "+ cov.length +"\n");
        fw.write("Cov = matrix([");
        for (int i =0; i<cov.length; i++) {
        	fw.write("[");
        	for (int j =0; j<cov.length; j++) {
        		fw.write(""+cov[i][j]);
        		if (j<cov.length-1) fw.write(",");
        	}
        	fw.write("]");
        	if (i<cov.length-1) fw.write(",");
        	fw.write("\n");
        }
        fw.write("])\n");
        fw.write("Mean = matrix([");
        for (int i=0; i<mean.length; i++) {
        	fw.write(""+mean[i]);
        	if (i<mean.length-1) fw.write(",");
        }
        fw.write("])\n");
        fw.write("r_min = "+ min_reve + "\n"); //0.035
        fw.write("G = matrix(np.concatenate((-np.transpose(Mean), -np.identity(n)), 0))\n");
        fw.write("h = matrix(np.concatenate((-np.ones((1,1))*r_min, np.zeros((n,1))), 0))\n");
        fw.write("A = matrix(1.0, (1,n))\n");
        fw.write("b = matrix(1.0)\n");
        fw.write("q = matrix(np.zeros((n, 1)))\n");
        fw.write("sol = qp(Cov, q, G, h, A, b)\n");
        fw.write("with open(\""+ out +"\", \"w\") as f:\n");
        fw.write("    for item in sol['x']:\n");
        fw.write("        f.write(\"%s\\n\" % item)\n");
        fw.close();
	}

	private static void constructPythonTaskMax(double[][] cov, double[] mean, double max_risk, String cmd, String out) throws IOException {
		FileWriter fw = new FileWriter(cmd);
		fw.write("from cvxpy import *\n");
		fw.write("import pyscipopt.scip as scip\n");
		fw.write("import numpy as np\n");
		fw.write("x = Variable("+ cov.length +")\n");
		fw.write("Cov = [");
		for (int i =0; i<cov.length; i++) {
			fw.write("[");
			for (int j =0; j<cov.length; j++) {
				fw.write(""+cov[i][j]);
				if (j<cov.length-1) fw.write(",");
			}
			fw.write("]");
			if (i<cov.length-1) fw.write(",");
			fw.write("\n");
		}
		fw.write("]\n");
		fw.write("Mean = [");
		for (int i=0; i<mean.length; i++) {
			fw.write(""+mean[i]);
			if (i<mean.length-1) fw.write(",");
		}
		fw.write("]\n");
		fw.write("risk = quad_form(x, Cov)\n");
		fw.write("mu = np.asarray(Mean)\n");
		fw.write("ret = mu.T@x\n");
		fw.write("constraints = [sum(x) <= 1.0, x>=0, risk<="+ max_risk +"]\n");
		fw.write("Problem(Maximize(ret), constraints).solve(solver=SCS)\n");

		fw.write("with open(\""+ out +"\", \"w\") as f:\n");
		fw.write("    for i in range("+ cov.length +"):\n");
		fw.write("        f.write(\"%s\\n\" % x.value[i])\n");
		fw.close();
	}

	private static void constructJuliaTask(double[][] cov, double[] mean, double min_reve, 
											String fileName, String outFile) throws IOException {
		FileWriter fw = new FileWriter(fileName);   
	
        fw.write("using JuMP \n");
        fw.write("using Ipopt \n");
        fw.write("N="+ cov.length + " \n");
        fw.write("m = Model(solver=IpoptSolver()) \n");
        fw.write("C = [");
        for (int i = 0; i < cov.length; i++) {
        	for (int j =0; j<cov.length; j++) fw.write(cov[i][j]+ " ");
        	if (i < cov.length - 1) fw.write("; ");
        	fw.write("\n");
        }        		
        fw.write("]\n");
        fw.write("Mean = [");
        for (int i=0; i<mean.length; i++) fw.write(mean[i]+" ");
        fw.write("]\n");
        fw.write("@variable(m, 0 <= x[i=1:N] <= 1) \n");
        fw.write("@objective(m,Max,sum(x[i] * Mean[i] for i = 1:N)) \n");
        fw.write("@constraint(m, sum(x[j] * sum(x[i] * C[i,j] for i = 1:N) for j = 1:N) <= "+ min_reve +") \n");
        fw.write("@constraint(m, sum(x[i] for i = 1:N) ==1.0) \n");
        fw.write("status = solve(m) \n");
        fw.write("open(\""+ outFile + "\", \"w\") do f \n");
        fw.write("        for i= 1:N \n");
        fw.write("            n1 = getvalue(x[i]) \n");
        fw.write("            write(f, \"$n1\\n\") \n");
        fw.write("        end \n");
        fw.write("    end \n");
        fw.write("exit() \n");
        fw.close();
	}
	
	private static void runOsCmd(String cmd) throws IOException {
		 Process p = Runtime.getRuntime().exec(cmd);
		 try {
			p.waitFor();
		 } catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(0);
		 } 
	}
	
	private static void runJulia(String cmd) throws IOException {
		 try {
			Runtime rt = Runtime.getRuntime();
            Process proc = rt.exec(cmd);
            InputStream stdin = proc.getInputStream();
            InputStreamReader isr = new InputStreamReader(stdin);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            while ( (line = br.readLine()) != null);
            int exitVal = proc.waitFor();            
		 } catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(0);
		 } 
		 catch (Throwable t)
         {
           t.printStackTrace();
         }
	}
	
	// this will find a few outperforming stocks (max_shares) which have risks associated not greater than 'max_risk'
	private static PortfolioWithWallet findAndBuyBestPortfolioWith_DIV(boolean make_it_simple, 
							LocalDate startDate, int period_length, int periods_number, BigDecimal budget, 
							int per_len, double max_risk, int max_shares, BufferedWriter bw) throws IOException {
		double[] benefits = new double[tickers.size()]; // for simple calculation
		double[][] benefitsSolv = new double[tickers.size()][periods_number]; // for solver -> covariance
		
		String[] goodTickers = new String[tickers.size()];
		Close[] lastClose = new Close[tickers.size()];
		bw.write("<BR>New portfolio:\n<table border=1>");
		bw.write("<TR><TH>Stock</TH><TH>W[%]</TH><TH> Vol. </TH><TH> Price</TH><TH> Total</TH></TR>\n"); 
		int idx=0;
		for (String symbol: tickers) {	
			List<Close> historicPrices = prices.get(symbol);
			List<Close> historicDividends = dividends.get(symbol);
			Close lastPrice = null;
			Close veryFirstPrice = null;
			double[] dividends = new double[periods_number];
			int i=0;
			for (; i<periods_number; i++) {
				LocalDate fromDate = startDate.plusMonths(i * period_length);
				LocalDate toDate   = startDate.plusMonths((i+1) * period_length);

				List<Close> historicPricesTemp = historicPrices.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && (p.date.isBefore(toDate) || p.date.isEqual(toDate)))) // stopDate belongs to the next period
						.collect(Collectors.toList()); // maybe sorting to secure against different order in source files ?
				
				if (historicPricesTemp == null || historicPricesTemp.size() == 0) break; // do not take this stock into consideration
				// summing 
				lastPrice = historicPricesTemp.get(0); // this is called many times, but we are interested in the last one
				// TODO: do not ignore splits !!!
				Close first = historicPricesTemp.get(historicPricesTemp.size() - 1);
				if (i==0) veryFirstPrice = first;
				if (first == null || lastPrice == null || first.price == null || first.price.equals(0)) break; // error
				dividends[i] = historicDividends.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && (p.date.isBefore(toDate) || p.date.isEqual(toDate))))
						.collect(Collectors.summingDouble(d -> d.price.doubleValue()));
				benefitsSolv[idx][i] = dividends[i]/lastPrice.price.doubleValue();  // first.price.doubleValue();
			}
			if (i == periods_number) { // = we found share prices in all periods 
				if (lastPrice == null || lastPrice.price == null || lastPrice.price.equals(0.0)
						|| veryFirstPrice == null || veryFirstPrice.price.equals(0.0)) continue; // don't consider this share
				if (StatUtils.variance(benefitsSolv[idx]) > max_risk) { // earlier 'dividends'
					continue; // a too risky stock
				}
				/*
				if (!div_was_paid_year_ago(startDate, period_length, periods_number, historicDividends)) // in_analogous_period
					continue; // we are looking for shares which are probable to give dividend in that period (month?)
				*/

				// one of alternative strategies - applying the distance to the next dividend payment
				// finding the date
				/*
				int months = periods_number * period_length;
				LocalDate fromDate   = startDate.plusMonths(months); // starting at the end of period
				LocalDate toDate   = startDate.plusMonths(months + period_length*3); // 
				
				List<Close> historicDividendsTemp = historicDividends.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && (p.date.isBefore(toDate) || p.date.isEqual(toDate))))
						.collect(Collectors.toList());
				Close first = null;
				if (historicDividendsTemp != null && historicDividendsTemp.size()>0) {
					first = historicDividendsTemp.get(historicDividendsTemp.size() - 1);
				}
				long days_to_payment = period_length * 30; // starting with kind of a MAX
				// now calculating distance between last date in this iteration and the date of dividend payment
				if (first != null) {
					days_to_payment = ChronoUnit.DAYS.between(lastPrice.date, first.date);
					if (days_to_payment<=0) {
						System.out.println ("days_to_payment <= 0");
						days_to_payment = period_length * 30;
					}
				}
				if (days_to_payment < period_length * 30)
					benefits[idx] = StatUtils.mean(dividends) / lastPrice.price.doubleValue() ;
				else benefits[idx] = 0.0;
				//benefits[idx] = StatUtils.mean(dividends) / lastPrice.price.doubleValue() / days_to_payment; // veryFirstPrice.price.doubleValue();
				*/
				benefits[idx] = StatUtils.mean(dividends) / lastPrice.price.doubleValue() ;
				goodTickers[idx] = symbol;
				lastClose[idx] = lastPrice; // used to sell & buy shares
				idx++;
			}
		}
		// now choosing the best ones
		if (make_it_simple)
			return createSimplePortfolio(max_shares, benefits, budget, goodTickers, lastClose, bw);
		else
		{ 	String ident = periods_number +"-"+ period_length+"-" +per_len +"-"+ (max_risk+"").replace('.', '_') ;
			String outFile = OUT_DIR + "solver-"+ ident +".out";
			String cmd = OUT_DIR + "solver-"+ ident +".jl";
			double[][] benef = crympData(benefitsSolv,  idx, periods_number);
			double[] mean = countMean (idx, benef, periods_number);
			double[][] cov = Covar(benef);// myCov(idx, periods_number, benef, mean); // TODO: double[][] cov = new Covariance(benef).getCovarianceMatrix().getData();
			constructJuliaTask(cov, mean, max_risk, cmd, outFile);
			runJulia(JULIA_CMD + " "+ cmd + " > julia.out 2>nul");
			return createPortfolioFromSolver(budget, idx, goodTickers, lastClose, bw, outFile);
		}
	}

	private static boolean div_was_paid_year_ago(LocalDate startDateOfRiskAssessment, int period_length,
												 int periods_number, List<Close> historicDividends) {
		int months = periods_number * period_length;
		LocalDate fromDate   = startDateOfRiskAssessment.plusMonths(months - 12); // year ago
		LocalDate toDate   = startDateOfRiskAssessment.plusMonths(months - 12 + period_length);

		List<Close> historicDividendsTemp = historicDividends.stream()
				.filter(p -> (p.date.isAfter(fromDate) && (p.date.isBefore(toDate) || p.date.isEqual(toDate))))
				.collect(Collectors.toList());

		if (historicDividendsTemp != null && historicDividendsTemp.size()>0) {
			return true;
		}
		return false;
	}

	// create a 1/N portfolio based on dividend yields
	private static PortfolioWithWallet createSimplePortfolio(int max_shares, double[] benefits, BigDecimal budget, 
					String[] goodTickers, Close[] prices, BufferedWriter bw) throws IOException {
		List<StockInPortfolio> portfolio = new ArrayList<StockInPortfolio>();
		BigDecimal total = new BigDecimal(0.0);
		double share = (1.0/(double)max_shares);
		double avg = 0.0; // for one of charts in article about dividend yields over time
		for (int s=0; s<max_shares; s++) {
			// find the best 
			List<Double> list = Arrays.stream(benefits).boxed().collect(Collectors.toList());
			int idx = list.indexOf(Collections.max(list));
			avg += benefits[idx];
			benefits[idx] = 0.0; // don't consider this share any longer in this loop
			// shares will be split equally
			int volume = (int)(budget.doubleValue() * share / prices[idx].price.doubleValue()); 
			portfolio.add(new StockInPortfolio(goodTickers[idx], volume));
			bw.write("<TR><TD>"+ goodTickers[idx] +"</TD><TD align=right>"+ String.format("%.1f",share*100.0) +"</TD><TD align=right>"
					   	+ volume +"</TD><TD align=right>"+ String.format("%.2f",prices[idx].price) 
					   	+ "</TD><TD align=right>" + String.format("%.2f", prices[idx].price.multiply(new BigDecimal(volume)))
					   	+ "</TD></TR>\n");
			total = total.add(prices[idx].price.multiply(new BigDecimal(volume)));
		}
		avg /= max_shares;
// Figure 3 & 4
//System.out.println(avg);		
		bw.write("</table>\n");
		bw.write("Portfolio value: " + String.format("%.2f", total) + "<BR>");
		bw.write("Cash after stock purchase: " 
				+ String.format("%.2f", budget.subtract(total).doubleValue()) + "<BR>");
		bw.flush();
		return new PortfolioWithWallet(budget.subtract(total), portfolio, total);
	}
	
	private static PortfolioWithWallet createPortfolioFromSolver(BigDecimal budget, int size, String[] goodTickers, 
														Close[] prices, BufferedWriter bw, String outFile) throws IOException {
		bw.write("<BR>New portfolio:\n<table border=1>");
		bw.write("<TR><TH>Stock</TH><TH>W[%]</TH><TH> Vol. </TH><TH> Price</TH><TH> Total</TH></TR>\n"); 
		List<StockInPortfolio> portfolio = new ArrayList<StockInPortfolio>();
		double[] weights = new double[size];
		BigDecimal total = new BigDecimal(0.0);
		int i = 0;
		int asset_count=0;
		try (BufferedReader br = new BufferedReader(new FileReader(outFile))) {
			while (br.ready()) {
			   String line = br.readLine();
			    
			   if (i >= size) {
				   if (line == null || line.trim().length()<1) break; // last line
				   System.out.println("solver returned more lines than expected number of variables");
				   System.exit(0);
			   }
			   if (line == null || line.trim().length()<1) {
				   System.out.println("solver returned an empty line");
				   System.exit(0);
			   }
			   weights[i] = Double.parseDouble(line); 
			   if (weights[i]>MIN_SHARE)  {
				   int volume = (int)(budget.doubleValue() * weights[i] / prices[i].price.doubleValue()); 
				   
				   if (volume > 0) {
					   portfolio.add(new StockInPortfolio(goodTickers[i], volume));
					   
					   bw.write("<TR><TD>"+ goodTickers[i] +"</TD><TD align=right>"+ String.format("%.1f",weights[i]*100) +"</TD><TD align=right>"
							   	+ volume +"</TD><TD align=right>"+ String.format("%.2f", prices[i].price) 
							   	+ "</TD><TD align=right>" + String.format("%.2f", prices[i].price.multiply(new BigDecimal(volume)))
							   	+ "</TD></TR>\n");
					   total = total.add(prices[i].price.multiply(new BigDecimal(volume)));
					   asset_count++;
				   }
			   }
			   i++;
			}
		}
		catch (IOException ioe) {
			System.out.println("read python output failed");
			System.exit(0);
			return null;
		}
// Table 3.
//System.out.println(asset_count); // number of assets in portfolio
		bw.write("</table>\n");
		bw.write("Portfolio value: " + String.format("%.2f", total) + "<BR>");
		bw.write("Cash after stock purchase: " + String.format("%.2f", budget.subtract(total)) + "<BR>");
		bw.flush();
		return new PortfolioWithWallet(budget.subtract(total), portfolio, total);
	}
	
	private static BigDecimal showPortfolio(List<StockInPortfolio> portfolio, LocalDate dateFrom, 
											LocalDate dateTo, BufferedWriter bw) throws IOException {
		bw.write("Portfolio rebuild on: "); /*dateTo*/ 
		bw.write(portfolio == null || portfolio.size() == 0 ? ""+dateTo : ""+findDateForDate(portfolio.get(0), dateTo) 
											+"<BR>\nPortfolio before rebuild:<table border=1>");
		bw.write("<TR><TH>Stock</TH><TH> Vol. </TH><TH> Price </TH><TH> Total </TH><TH>Dividend</TH><TH> Some more details </TH></TR>\n");
//		BigDecimal divid_paid_total= new BigDecimal(0);
		BigDecimal total_price = new BigDecimal(0);
		BigDecimal total_divid = new BigDecimal(0);
		double avg=0; // for one of charts in the article - actual dividend yield achieved, not forecasted by a mean
		for (StockInPortfolio stock: portfolio) {
			// finding the price for today or "yesterday"
			BigDecimal price = findPriceForDate(stock, dateTo);
			double firstPrice = findPriceForDate(stock, dateFrom).doubleValue(); // for dividend yield, se above
			if (price == null) 
				System.out.println("Error: a price for date not found");
			DividentWithComment dividends = findDividendsPaid(stock, dateFrom, dateTo);
			BigDecimal splitRatio = checkSplits(stock.ticker, dateFrom, dateTo);
			if (splitRatio.doubleValue() != 1.0 && dividends.dividend.doubleValue()>0.0) {
				bw.write("<SPLIT_DIV_ERR, stock: " + stock.ticker + ", dateFrom: " + dateFrom.toString() + ">");
			}
			stock.number *= splitRatio.doubleValue();
//			divid_paid_total = divid_paid_total.add(dividends.dividend);
			
		    bw.write("<TR><td>"+ stock.ticker +"</td><td align=right>" + stock.number + "</td><td align=right>" + String.format("%.2f", price) 
		    		+ "</td><td align=right>" + String.format("%.2f", price.multiply(new BigDecimal(stock.number))) 
		    		+ "</td><td align=right>" + String.format("%.2f", dividends.dividend) + "</td><TD>"+ dividends.comment 
		    		+ (splitRatio.doubleValue() != 1.0 ? "SPLIT(s)" : "") + "</td></TR>\n");
		    total_price = total_price.add(price.multiply(new BigDecimal(stock.number)));
		    total_divid = total_divid.add(dividends.dividend); 
		    avg += dividends.dividend.doubleValue() / (firstPrice*stock.number);
		}
		avg /= portfolio.size();
//System.out.println(Math.pow(1.0+avg, 4)-1.0);
// if you would like to know the total of paid dividends
//System.out.println(total_divid); // dateTo + ": "+ 
		bw.write("</table>");
		bw.write("Value of stock sold: " + String.format("%.2f", total_price) + "<BR>\n");
		bw.write("Total dividends paid: " + String.format("%.2f", total_divid) + "<BR>\n");
		return total_price.add(total_divid);
	}
	
	private static BigDecimal findPriceForDate(StockInPortfolio stock, LocalDate dateTo) {
		List<Close> historicPrices = prices.get(stock.ticker);
		for (Close quote: historicPrices) {
			if (quote.date.isBefore(dateTo) || quote.date.isEqual(dateTo)) 
				return quote.price;
		}
		return null;
	}
	
	private static LocalDate findDateForDate(StockInPortfolio stock, LocalDate dateTo) {
		List<Close> historicPrices = prices.get(stock.ticker);
		for (Close quote: historicPrices) {
			if (quote.date.isBefore(dateTo) || quote.date.isEqual(dateTo)) 
				return quote.date;
		}
		return null;
	}
	
	private static DividentWithComment findDividendsPaid(StockInPortfolio stock, LocalDate dateFrom, LocalDate dateTo) {
		List<Close> historicDividends = dividends.get(stock.ticker);
		BigDecimal total = new BigDecimal(0);
		BigDecimal number = new BigDecimal(stock.number);
		String comment="";
		
		for (Close div: historicDividends) {
			if ((div.date.isBefore(dateTo) && (div.date.isAfter(dateFrom)
					|| div.date.isEqual(dateFrom))) ) { // div.date.isEqual(dateTo) belongs to the next iteration  
				total = total.add(div.price.multiply(number));
				comment += div.date + ", " + div.price +"; "; 
			}
		}
		return new DividentWithComment(total, comment);
	}
	
	private static BigDecimal checkSplits(String ticker, LocalDate dateFrom, LocalDate dateTo) {
		List<Split> splitsTemp = splits.stream() 
				.filter(p -> (p.symbol.equals(ticker) && p.date.isAfter(dateFrom) && (p.date.isBefore(dateTo) || p.date.isEqual(dateTo)))) // stopDate belongs to the next period
				.collect(Collectors.toList()); // maybe sorting to secure against different order in source files ?
		BigDecimal ratio = new BigDecimal(1.0);
		for (Split s : splitsTemp) {
			ratio = ratio.multiply(s.ratio); // there might be more splits in one period 
		}
		return ratio;
	}
	
	private static List<Close> readHistory(String ticker, String suffix, int idx) {
		List<Close> hist = new ArrayList<Close>();
		try (BufferedReader br = new BufferedReader(new FileReader(HIST_DIR + ticker + suffix))) {
			br.readLine(); // ignoring the header line
			while (br.ready()) {
				String[] values = br.readLine().split(",");
				hist.add(new Close(values[0], values[idx]));
			}
		}
		catch (IOException ioe) {
			System.out.println("readHistory failed for ticker: " + ticker + ", suffix: " + suffix);
			System.exit(0);
			return null;
		}
		// we don't know the order of history in the input file
		Close[] arr = hist.toArray(new Close[hist.size()]); 
		Arrays.sort(arr);
		return Arrays.asList(arr);
	}
	
	private static void readSplits(String fileName) {
		String file = HIST_DIR + fileName;
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			while (br.ready()) {
				String[] values = br.readLine().split(",");
				splits.add(new Split(values[0], values[1], values[2]));
			}
		}
		catch (IOException ioe) {
			System.out.println("readSplits failed: " + file);
			System.exit(0);
		}
	}
	
	private static List<Stock> readStockName(String ticker, String suffix) {
		List<Stock> hist = new ArrayList<Stock>();
		try (BufferedReader br = new BufferedReader(new FileReader(HIST_DIR + "data/"+ ticker + suffix))) {
			while (br.ready()) {
				String line = br.readLine();
				line = line.replace("\",\"", "\"|\""); // there might be commas in values & line.split("|") does not work
				line = line.replace(',',' '); 
				line = line.replace('|',',');
				String[] values = line.split(",");
				hist.add(new Stock(values[1], values[0]));
			}
		}
		catch (IOException ioe) {
			System.out.println("readStockName failed for ticker: " + ticker + ", suffix: " + suffix);
			System.exit(0);
			return null;
		}
		return hist;
	}
	
	private static List<String> readShareSymbols(String fileName) {
		List<String> stockNames = new ArrayList<String>();
		try (BufferedReader br = new BufferedReader(new FileReader(fileName))) { 
			while (br.ready()) {
			   stockNames.add(br.readLine());
			}
		    return stockNames;
		}
		catch (IOException ioe) {
			System.out.println("readShareSymbols failed");
			System.exit(0);
			return null;
		}
	}
}
