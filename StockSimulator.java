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
import java.util.Date;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.correlation.Covariance;

public class StockSimulator {

	private static final String ROOT_DIR = "/home/dell/DIVID/";
	private static final String SHARE_NAMES = ROOT_DIR + "GIT/SP100.txt";
	private static final String PYTHON_CODE = ROOT_DIR + "mpt.py";
	private static final String SOLVER_OUTPUT = ROOT_DIR + "solver.out";
	private static final String JULIA_CODE = ROOT_DIR + "mpt.jl";
	private static final String JULIA_CMD = "C:\\Users\\dell\\AppData\\Local\\Julia-0.5.2\\bin\\julia";
	private static final String SIMUL_OUTPUT = ROOT_DIR + "simul.html";
	private static final double MIN_SHARE = 0.0001; // minimal share of a stock we accept in a portfolio  
	private static final double MIN_REVE = 0.035; // minimal revenue
	private static final double MAX_RISK = 0.015; // minimal revenue
	private static final int    MAX_STOCKS = 4; // how many stocks we will have in portfolio in "dividend arbitrage" (without solver)
	private static final int 	MINIMIZE = 0;
	private static final int 	MAXIMIZE = 1;
	
	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
	
	private static List<String> tickers;
	private static Map<String,List<Close>> prices;
	private static Map<String,List<Close>> dividends; // .price will be the dividend paid on a day
	private static Map<String,List<Stock>> stocks;
	
	private static class Close {  // quote
		public Close(String date, String price) {
			this.date = LocalDate.parse(date, formatter);
			this.price = new BigDecimal(price);
		}
		public LocalDate date;
		public BigDecimal price;
	}
	
	private static class Stock {  // quote
		public Stock(String date, String name) {
			try {
				if (date.contains("N/A"))
					this.dividendDate = null;
				else this.dividendDate = sdf.parse(date.replace("\"",""));
			} catch (ParseException e) {
				// TODO Auto-generated catch block
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
		public PortfolioWithWallet(double wallet, List<StockInPortfolio> portfolio) {
			this.wallet = wallet;
			this.portfolio = portfolio;
		}
		public double wallet; // TODO: this should be BigDecimal
		public List<StockInPortfolio> portfolio;
	}
	
	public static void main(String[] args) throws IOException {

		tickers = readShareSymbols(SHARE_NAMES);
		prices = new HashMap<String,List<Close>>();
		dividends = new HashMap<String,List<Close>>();
		stocks = new HashMap<String,List<Stock>>();
		
		for (String symbol: tickers) {
			prices.put(symbol, readHistory(symbol, "-prc", 4));
			dividends.put(symbol, readHistory(symbol, "-div", 1));
			stocks.put(symbol, readStockName(symbol, "-nme"));
		}
		simulate(2000, 40, 3, 12, 0.035, new BigDecimal(100000)); // year, sim_periods in months, period_length, risk_ret_periods, min_ret
	}
	
	private static void simulate (int year, int sim_periods, // in month
		    					int period_length, int risk_ret_periods, 
		    					double min_ret, BigDecimal wallet) throws IOException {
		FileWriter fr = new FileWriter(new File(SIMUL_OUTPUT));
		BufferedWriter bw = new BufferedWriter(fr);
		bw.write("Markowitz Portfolio Selection (simulation)<BR>\n");
		bw.write("Number of periods: " + sim_periods + "<BR>\n");
		bw.write("Period's length: " + period_length + " months<BR>\n");  
		bw.write("Number of periods in risk assessment: " + risk_ret_periods + "<BR>\n");
		bw.write("Min expected return: " + min_ret + "<BR>\n");
		bw.write("Wallet content: " + wallet.toString());
		
		List<StockInPortfolio> portfolio = new ArrayList<StockInPortfolio>();
		
		for (int s=0; s<sim_periods; s++) {
			System.out.println(s);
			LocalDate dateTo = LocalDate.parse(year+"-01-01", formatter)
 										.plusMonths((s+risk_ret_periods)*period_length);
			LocalDate dateFrom = dateTo.minusMonths(period_length);
			bw.write("<BR>---------------------------------------------------------------------------<BR>");
		
			BigDecimal stockWithDividend = showPortfolio(portfolio, dateFrom, dateTo, bw);
		
			// here we sell the stock virtually
			wallet = wallet.add(stockWithDividend);
			
			PortfolioWithWallet stockAndWallet = findBestPortfolioWith_MPT(MAXIMIZE,
													dateTo.minusMonths(risk_ret_periods*period_length),
													period_length, risk_ret_periods, wallet, MAX_RISK, bw);
												/*findBestPortfolioWith_MPT(MINIMIZE,
														dateTo.minusMonths(risk_ret_periods*period_length),
														period_length, risk_ret_periods, wallet, MIN_REVE, bw);
												findAndBuyBestPortfolioWith_DA1(
													dateTo.minusMonths(risk_ret_periods*period_length),
													period_length, risk_ret_periods, wallet, MAX_RISK, MAX_STOCKS, bw);*/
			portfolio = stockAndWallet.portfolio;
			wallet = new BigDecimal(stockAndWallet.wallet);
		}
		bw.close();
	}
	
	private static PortfolioWithWallet findBestPortfolioWith_MPT(int type, LocalDate startDate, 
													int period_length, int periods_number,
													BigDecimal budget, double min_r, BufferedWriter bw) throws IOException {
		double[][] benefits = new double[tickers.size()][periods_number]; 
		String[] goodTickers = new String[tickers.size()];
		Close[] lastPrice = new Close[tickers.size()];
		
		int idx =0;
		for (String symbol: tickers) {
			List<Close> historicPrices = prices.get(symbol);
			List<Close> historicDividends = dividends.get(symbol);
			Close last = null;
			int i=0;
			for (; i<periods_number; i++) {
				LocalDate fromDate = startDate.plusMonths(i * period_length);
				LocalDate toDate   = startDate.plusMonths((i+1) * period_length);

				List<Close> historicPricesTemp = historicPrices.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && p.date.isBefore(toDate)) || p.date.isEqual(toDate)) // stopDate belongs to the next period
						.collect(Collectors.toList()); // maybe sorting to secure against different order in source files ?
				
				List<Close> historicDividendsTemp = historicDividends.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && p.date.isBefore(toDate)) || p.date.isEqual(toDate))
						.collect(Collectors.toList());
				// checking price difference
				if (historicPricesTemp == null || historicPricesTemp.size() == 0) break; // do not take this stock into consideration
				
				last = historicPricesTemp.get(0); 
				Close first = historicPricesTemp.get(historicPricesTemp.size() - 1);
				if (first == null || lastPrice == null || first.price == null || first.price.equals(0)) break; // error
				// summing dividends
				BigDecimal sum = new BigDecimal(0);
				if (historicDividendsTemp != null && historicDividendsTemp.size()>0)
					for (Close div: historicDividendsTemp)
						if (div.price != null)
							sum = sum.add(div.price);
				
				benefits[idx][i] = last.price.subtract(first.price).add(sum).doubleValue();
				benefits[idx][i] /= first.price.doubleValue();
			}
			if (i == periods_number) { // no error, it will go to solver
				goodTickers[idx] = symbol;
				lastPrice[idx] = last; // we assume that the portfolio gets sold a day, or two, before the "portfolio rebuild on" 
				idx++; 
			}
		}
		double[][] benef = new double[idx][periods_number]; // number of stocks sent to solver maybe smaller than S&P100; due to missing data 
		for (int j=0; j<idx; j++)
			for (int i=0; i<periods_number; i++)
				benef[j][i] = benefits[j][i];
		// counting mean values 
		double[] mean = new double[idx];  
		
		for (int i=0; i<idx; i++) {
	      mean[i] = 0;
	      for (int k=0; k<periods_number; k++) mean[i] += benef[i][k];
	      mean[i] /= periods_number;
		}
		double[][] cov = myCov(idx, periods_number, benef, mean); // TODO: double[][] cov = new Covariance(benef).getCovarianceMatrix().getData();
		if (type == MINIMIZE) {
			constructPythonTask(cov, mean, min_r);
			runOsCmd("python " + PYTHON_CODE);
		}
		else { // MAXIMIZE
			constructJuliaTask(cov, mean, min_r);
			runJulia(JULIA_CMD + " "+ JULIA_CODE + " > julia.out 2>nul");
		}
		return createPortfolio(budget.doubleValue(), idx, goodTickers, lastPrice, bw);
	}
	
	private static void constructPythonTask(double[][] cov, double[] mean, double min_reve) throws IOException {
		FileWriter fw = new FileWriter(PYTHON_CODE);    
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
        fw.write("with open(\""+SOLVER_OUTPUT+"\", \"w\") as f:\n");
        fw.write("    for item in sol['x']:\n");
        fw.write("        f.write(\"%s\\n\" % item)\n");
        fw.close();
	}
	
	private static void constructJuliaTask(double[][] cov, double[] mean, double min_reve) throws IOException {
		FileWriter fw = new FileWriter(JULIA_CODE);    
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
        fw.write("open(\""+ SOLVER_OUTPUT + "\", \"w\") do f \n");
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
	private static PortfolioWithWallet findAndBuyBestPortfolioWith_DA1(
							LocalDate startDate, int period_length, int periods_number, BigDecimal budget, 
							double max_risk, int max_shares, BufferedWriter bw) throws IOException {
		double[] benefits = new double[tickers.size()]; 
		String[] goodTickers = new String[tickers.size()];
		Close[] lastClose = new Close[tickers.size()];
		bw.write("<BR>New portfolio:\n<table border=1>");
		bw.write("<TR><TH>Stock</TH><TH>W[%]</TH><TH> Vol. </TH><TH> Price</TH><TH> Total</TH></TR>\n"); 
		int idx=0;
		for (String symbol: tickers) {	
			List<Close> historicPrices = prices.get(symbol);
			List<Close> historicDividends = dividends.get(symbol);
			Close lastPrice = null;
			double[] dividends = new double[periods_number];
			int i=0;
			for (; i<periods_number; i++) {
				LocalDate fromDate = startDate.plusMonths(i * period_length);
				LocalDate toDate   = startDate.plusMonths((i+1) * period_length);

				List<Close> historicPricesTemp = historicPrices.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && p.date.isBefore(toDate)) || p.date.isEqual(toDate)) // stopDate belongs to the next period
						.collect(Collectors.toList()); // maybe sorting to secure against different order in source files ?
				
				if (historicPricesTemp == null || historicPricesTemp.size() == 0) break; // do not take this stock into consideration
				// summing 
				lastPrice = historicPricesTemp.get(0); // this is called many times, but we are interested in the last one 
				dividends[i] = historicDividends.stream() // limiting to the "risk assessment" period
						.filter(p -> (p.date.isAfter(fromDate) && p.date.isBefore(toDate)) || p.date.isEqual(toDate))
						.collect(Collectors.summingDouble(d -> d.price.doubleValue()));
			}
			if (i == periods_number) { // = we found share prices in all periods 
				if (lastPrice == null || lastPrice.price == null || lastPrice.price.equals(0.0)) continue; // don't consider this share
				if (StatUtils.variance(dividends) > max_risk) continue; // a too risky stock
				benefits[idx] = StatUtils.mean(dividends) / lastPrice.price.doubleValue();
				goodTickers[idx] = symbol;
				lastClose[idx] = lastPrice;
				idx++;
			}
		}
		// now choosing the best ones
		List<StockInPortfolio> portfolio = new ArrayList<StockInPortfolio>();
		BigDecimal total = new BigDecimal(0.0);
		double share = (1.0/(double)max_shares);
		for (int s=0; s<max_shares; s++) {
			// find the best 
			List<Double> list = Arrays.stream(benefits).boxed().collect(Collectors.toList());
			idx = list.indexOf(Collections.max(list));
			benefits[idx] = 0.0; // don't consider this share any longer in this loop
			// shares will be split equally
			int volume = (int)(budget.doubleValue() * share / lastClose[idx].price.doubleValue()); 
			portfolio.add(new StockInPortfolio(goodTickers[idx], volume));
			bw.write("<TR><TD>"+ goodTickers[idx] +"</TD><TD>"+ String.format("%.1f",share*100.0) +"</TD><TD>"
					   	+ volume +"</TD><TD>"+ lastClose[idx].price 
					   	+ "</TD><TD>" + lastClose[idx].price.multiply(new BigDecimal(volume))
					   	+ "</TD></TR>\n");
			total = total.add(lastClose[idx].price.multiply(new BigDecimal(volume)));
		}
		bw.write("</table>\n");
		bw.write("Portfolio value: " + total + "<BR>");
		bw.write("In the wallet after stock purchase: " + String.format("%.3f",budget.subtract(total).doubleValue()) + "<BR>");
		return new PortfolioWithWallet(budget.subtract(total).doubleValue(), portfolio);
	}
	
	private static PortfolioWithWallet createPortfolio(double budget, int size, String[] goodTickers, 
														Close[] prices, BufferedWriter bw) throws IOException {
		bw.write("<BR>New portfolio:\n<table border=1>");
		bw.write("<TR><TH>Stock</TH><TH>W[%]</TH><TH> Vol. </TH><TH> Price</TH><TH> Total</TH></TR>\n"); 
		List<StockInPortfolio> portfolio = new ArrayList<StockInPortfolio>();
		double[] weights = new double[size];
		BigDecimal total = new BigDecimal(0.0);
		int i = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(SOLVER_OUTPUT))) {
			while (br.ready()) {
			   String line = br.readLine();
			   weights[i] = Double.parseDouble(line); 
			   if (weights[i]>MIN_SHARE)  {
				   int volume = (int)(budget * weights[i] / prices[i].price.doubleValue()); 
				   
				   if (volume > 0) {
					   portfolio.add(new StockInPortfolio(goodTickers[i], volume));
					   
					   bw.write("<TR><TD>"+ goodTickers[i] +"</TD><TD>"+ String.format("%.1f",weights[i]*100) +"</TD><TD>"
							   	+ volume +"</TD><TD>"+ prices[i].price 
							   	+ "</TD><TD>" + prices[i].price.multiply(new BigDecimal(volume))
							   	+ "</TD></TR>\n");
					   total = total.add(prices[i].price.multiply(new BigDecimal(volume)));
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
		bw.write("</table>\n");
		bw.write("Portfolio value: " + total + "<BR>");
		bw.write("In the wallet after stock purchase: " + String.format("%.3f",budget-total.doubleValue()) + "<BR>");
		return new PortfolioWithWallet(budget - total.doubleValue(), portfolio);
	}
	
	// this function serves only as a check of Apache Commons
	private static double[][] myCov(int numb_of_stocks, int periods, 
													double[][] stock_return, double[] MoR) {
		double [][] Diff = new double[numb_of_stocks][periods];
		double [][] c = new double[numb_of_stocks][numb_of_stocks];
		for (int i=0; i<numb_of_stocks; i++)
			 for (int k=0; k<periods; k++)
			     Diff[i][k] = stock_return[i][k]-MoR[i];
	    for (int i=0; i<numb_of_stocks; i++) 
		    for (int j=0; j<numb_of_stocks; j++) {
		    	for (int k=0; k<periods; k++)
			            c[i][j] += Diff[i][k]*Diff[j][k];
			    c[i][j] /= (periods-1);
		    }
		return c;
	}
	
	private static BigDecimal showPortfolio(List<StockInPortfolio> portfolio, LocalDate dateFrom, 
											LocalDate dateTo, BufferedWriter bw) throws IOException {
		bw.write("Portfolio rebuild on: "+ dateTo +"<BR>\nCurrent value before rebuild:<table border=1>");
		bw.write("<TR><TH>Stock</TH><TH> Vol. </TH><TH> Price </TH><TH> Total </TH><TH> Paid dividend</TH></TR>\n");
//		BigDecimal divid_paid_total= new BigDecimal(0);
		BigDecimal total_price = new BigDecimal(0);
		BigDecimal total_divid = new BigDecimal(0);
		for (StockInPortfolio stock: portfolio) {
			// finding the price for today or "yesterday"
			BigDecimal price = findPriceForDate(stock, dateTo);
			DividentWithComment dividends = findDividendsPaid(stock, dateFrom, dateTo);
			if (price == null) 
				System.out.println("Error: a price for date not found");
//			divid_paid_total = divid_paid_total.add(dividends.dividend);
			
		    bw.write("<TR><td>"+ stock.ticker +"</td><td>" + stock.number + "</td><td>" + price + "</td><td>" 
		    		+ price.multiply(new BigDecimal(stock.number)) + "</td><td>" +dividends.dividend + "</td></TR>\n");
		    total_price = total_price.add(price.multiply(new BigDecimal(stock.number)));
		    total_divid = total_divid.add(dividends.dividend); 
		}
		bw.write("</table>");
		bw.write("Value of stock sold: " + total_price + "<BR>\n");
		bw.write("Value of dividend paid: " + total_divid + "<BR>\n");
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
	
	private static DividentWithComment findDividendsPaid(StockInPortfolio stock, LocalDate dateFrom, LocalDate dateTo) {
		List<Close> historicDividends = dividends.get(stock.ticker);
		BigDecimal total = new BigDecimal(0);
		BigDecimal number = new BigDecimal(stock.number);
		String comment="<font color=black>[<B>"+ stock.ticker +"</B>|" + stock.number + "|";
		
		for (Close div: historicDividends) {
			if ((div.date.isBefore(dateTo) && div.date.isAfter(dateFrom)) 
					|| div.date.isEqual(dateFrom) ) { // div.date.isEqual(dateTo) belongs to the next iteration  
				total = total.add(div.price.multiply(number));
				comment += div.date + "|" + div.price +"|"; 
			}
		}
		comment += "</font>";
		return new DividentWithComment(total, comment);
	}
	
	private static List<Close> readHistory(String ticker, String suffix, int idx) {
		List<Close> hist = new ArrayList<Close>();
		try (BufferedReader br = new BufferedReader(new FileReader(ROOT_DIR + "data/"+ ticker + suffix))) {
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
		return hist;
	}
	
	private static List<Stock> readStockName(String ticker, String suffix) {
		List<Stock> hist = new ArrayList<Stock>();
		try (BufferedReader br = new BufferedReader(new FileReader(ROOT_DIR + "data/"+ ticker + suffix))) {
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
