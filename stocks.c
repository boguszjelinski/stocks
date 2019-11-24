#include <stdio.h>
#include <time.h>
#include <math.h>

#define MAXCO 102  // max number of companies
#define MAXQU 5000  // max number of quotations
#define MAXST 4   // max number of stocks in portfolio
#define SECSYEAR 31536000 // number of seconds in a year 60*60*24*365
#define SECSPERDAY 86400 // seconds daily
#define VARPERIOD 3*SECSYEAR  // ??? dlaczego 3
#define FUND 1000.0
#define MINDIVCOUNT 12

const int year_count = VARPERIOD/SECSYEAR;
int j,k,l,m, ret, color;
char *str, *str2, date2[11], *kolor;

int volume2, dateint, numb_of_stocks=0; 
int min_numb_of_divid=MINDIVCOUNT; // ASSUMPTION: minimum number of dividend payments - kind of risk how a company treats investors
char cmd[MAXCO], symb[MAXCO][10], nme[MAXCO][80], date[MAXCO][MAXQU][12], date_for_price[MAXCO][12], nextdivdate[MAXCO][12],
    divdate[MAXCO][MAXQU/5][12], paydate[MAXCO][10];

 
  float price[MAXCO][MAXQU], price_at_date[MAXCO], divid[MAXCO][MAXQU/5], 
     averagediv[MAXCO], yearlydiv[MAXCO], variance[MAXCO], var, nextdiv[MAXCO];
  int divcount[MAXCO], quotecount[MAXCO], div_in_range[MAXCO], daystilldiv[MAXCO], div_date_int[MAXCO][MAXQU/5], date_int[MAXCO][MAXQU];
  // portfolio description
  int found, maxyieldindex[MAXST]={-1,-1,-1,-1}, prev_maxyieldindex[MAXST]={-1,-1,-1,-1}, 
      volume[MAXST],  // number of stock purchased
      prev_volume[MAXST]; // number of stock to be sold to get the money for a better stock
  float maxyields[MAXST]={0.0,0.0,0.0,0.0};

//int old_count=0; // how many stock do not change in the portfolio
char comment[1000], divcomment[1000], buycomment[1000];
float wallet, invested, sold;

FILE *flog;

time_t to_seconds(const char *date)
 { struct tm storage={0,0,0,0,0,0,0,0,0};
   char *p=NULL;
   time_t retval=0;
   p=(char *)strptime(date,"%Y-%m-%d",&storage);
   if(p==NULL) { retval=0; }
   else { retval=mktime(&storage); }
   return retval;
 }                                                                                                                         

char * datefromsecs (time_t secs, char * ret)
{ //time_t secs ;
  struct tm * tmdiv;
  if (ret==NULL) ret = malloc (12);
  tmdiv = localtime(&secs);
  if (tmdiv!=NULL) 
  { sprintf (ret, "%d-", 1900+tmdiv->tm_year);
    if (tmdiv->tm_mon+1<10) sprintf (ret, "%s0%d-", ret, tmdiv->tm_mon+1);
    else sprintf (ret, "%s%d-", ret, tmdiv->tm_mon+1);
    if (tmdiv->tm_mday<10) sprintf (ret, "%s0%d", ret, tmdiv->tm_mday);
    else sprintf (ret, "%s%d", ret, tmdiv->tm_mday);
  }
  else fprintf (flog, "Error getting date from secs  \n");
  return ret;
}  

int read_share_symbols (char * file_name)
{ int counter=0, ret;
  char *str;
  FILE *f = fopen (file_name, "r");
  str = malloc(100);
  counter = 0; 
  while (1)
  { ret = fscanf (f, "%s", str);
    if (ret == EOF) break;
    strcpy (symb[counter], str); // symb: global
    counter++;
  }    
  fclose (f);
  return counter;
}

void read_history (int i) // i = asset/company index
{ char chr; int bytes=0;
  float open, high, low, close, adj_close;

  if (strlen(symb[i]) <= 0) return; // 'symb' is read in read_share_symbols, shouldn't have empty items
  
  sprintf (cmd, "../data/%s-prc", symb[i]);
  //printf ("\n%s:", symb[i]);
  FILE *ff = fopen (cmd,"r");
  //read the first line with column titles
  bytes=0;
  quotecount[i]=0;
  while ((ret=fgetc(ff))!='\n' && ret!=EOF) bytes++;
  //read the rest - the close price matters
  if (bytes>3) // a hardcode, the first line is longer, should be enough
  { for (j=0; j<MAXQU; j++)
    { ret = fscanf (ff,"%10s%c%f%c%f%c%f%c%f%c%d%c%f", date[i][j], &chr, &open, &chr, 
       &high, &chr, &low, &chr, &price[i][j], &chr, &volume2, &chr, &adj_close);
      if (ret == EOF) break;
      date_int[i][j] = to_seconds(date[i][j]); // TBD: !!! check if the last row is read with the EOF signal at a time
    }
    quotecount[i]=j; // used in: count_risks and generate_history_HTMLs
  }
  fclose(ff);
      // reading the DIVIDEND tables
  sprintf (cmd,"../data/%s-div",symb[i]);
  ff=fopen(cmd,"r");
  //read the first line with column names
  bytes=0;
  divcount[i]=0;
  while ((ret=fgetc(ff))!='\n' && ret!=EOF) bytes++;
  //read the rest
  if (bytes>3) // a hardcode
  { for (j=0; j<MAXQU; j++)
    { ret = fscanf (ff,"%10s%c%f", divdate[i][j], &chr, &divid[i][j]);
      if (ret == EOF) break;
      div_date_int[i][j] = to_seconds(divdate[i][j]);
    }
    divcount[i]=j;
  }
  fclose(ff);        
          // reading the company NAME & dividend pay date
  sprintf (cmd,"../data/%s-nme",symb[i]);
  ff = fopen(cmd,"r");
  // reading the preceeding "
  ret=fgetc(ff);
  // reading name
  k=0;
  while ((ret=fgetc(ff))!='"') nme[i][k++]=ret;
  nme[i][k]=0;
  ret=fgetc(ff); ret=fgetc(ff); // reading next 2 characters ,"
  // reading dividend pay date
  k=0;
  while ((ret=fgetc(ff))!='"') paydate[i][k++]=ret;
  fclose(ff);        
}

void generate_history_HTMLs(void)
{ FILE *ff; int i;
  for (i=0; i<numb_of_stocks; i++) 
  { // PRICE
    sprintf (cmd,"../html/%s-prc.html", symb[i]);
    ff = fopen (cmd,"w");
    fprintf (ff, "<html><body><center><B>Prices for stock %s (%s)</b><table border=1>", symb[i], nme[i]);
    fprintf (ff, "<th bgcolor=grey><font color=white>Date</th><th bgcolor=grey><font color=white>Price</th>\n");
    color=0;
    for (j=0; j<quotecount[i]; j++)
     //if (price[i][j]>0.0)
	{ if (color) kolor="silver"; else kolor="white";
	  fprintf (ff,"<tr><td bgcolor=%s align=right>%s</td><td bgcolor=%s align=right>%6.2f</td></tr>\n",
		 kolor, date[i][j], kolor, price[i][j]);
	  if (color) color =0; else color=1;
	}
    fprintf (ff, "</table></center></body></html>\n");
    fclose (ff);
    // DIVID
    sprintf (cmd,"../html/%s-div.html",symb[i]);
    ff = fopen(cmd,"w");
    fprintf (ff, "<html><body><center><B>Dividends for stock %s (%s)</b><table border=1>", symb[i], nme[i]);
    fprintf (ff, "<th bgcolor=grey><font color=white>Date</th><th bgcolor=grey><font color=white>Value</th>");
    color=0;
    for (j=0; j<divcount[i]; j++)
     if (divid[i][j]>0.0)
     { if (color) kolor="silver"; else kolor="white";
       fprintf (ff,"<tr><td bgcolor=%s align=right>%s</td><td bgcolor=%s align=right>%6.2f</td></tr>", 
         kolor, divdate[i][j], kolor, divid[i][j]);
       if (color) color =0; else color=1;
     }
    fprintf (ff, "</table></center></body></html>\n");
    fclose (ff);
  }
}

float generate_report(FILE *ff, char * till, int is_last, float div_paid)
{ float total_portfolio_value; 
  fprintf (ff, "<B>Best stocks on %s</B>\n", till); 
  fprintf (ff, "<table border=1><th bgcolor=grey><font color=white>Symbol   </th><th bgcolor=grey><font color=white>Name</th>");
  fprintf (ff, "<th bgcolor=grey><font color=white>Price<br>at that<br>date </th><th bgcolor=grey><font color=white>Divid.<br>count</th>");
  fprintf (ff, "<th bgcolor=grey><font color=white>Average<br>yearly<br>dividend</th><th bgcolor=grey><font color=white>Variance</th>");
  fprintf (ff, "<th bgcolor=grey><font color=white>Dividend<br>pay<br>date  </th><th bgcolor=grey><font color=white>Next<br>pay<br>date</th>");
  fprintf (ff, "<th bgcolor=grey><font color=white>Next<br>pay<br>value     </th><th bgcolor=grey><font color=white>Days<br>till<br>payment</th>");
  fprintf (ff, "<th bgcolor=grey><font color=white>Yield <br>[%]            </th><th bgcolor=grey><font color=white>Yield/days</th>");
  fprintf (ff, "<th bgcolor=grey><font color=white>Volume                   </th><th bgcolor=grey><font color=white>Value</th>\n");
  for (k=0; k<MAXST; k++)
   if (maxyieldindex[k]!=-1)
   { if (color) kolor="silver"; else kolor="white";
     fprintf (ff, "<tr><td bgcolor=%s align=center>%s</td><td bgcolor=%s align=center>%s</td><td bgcolor=%s align=right><A HREF=%s-prc.html>%6.2f</a></td>\n",
        kolor, symb[maxyieldindex[k]], kolor, nme[maxyieldindex[k]], kolor, symb[maxyieldindex[k]], price_at_date[maxyieldindex[k]]);
     fprintf (ff, "<td bgcolor=%s align=right><A HREF=%s-div-%s.html>%d</a></td>\n", 
       kolor, symb[maxyieldindex[k]], till, div_in_range[maxyieldindex[k]]);
     fprintf (ff, "<td bgcolor=%s align=right>%6.2f</td><td bgcolor=%s align=right>%7.5f</td><td bgcolor=%s align=right>%s</td>\n", 
       kolor, yearlydiv[maxyieldindex[k]], kolor, variance[maxyieldindex[k]], kolor, paydate[maxyieldindex[k]]);
     fprintf (ff, "<td bgcolor=%s align=right><A HREF=%s-div.html>%s</a></td><td bgcolor=%s align=right>%5.2f</td>\n",
        kolor, symb[maxyieldindex[k]], nextdivdate[maxyieldindex[k]], kolor, nextdiv[maxyieldindex[k]]);
     fprintf (ff, "<td bgcolor=%s align=right>%d</td><td bgcolor=%s align=right>%5.2f</td>\n",    
         kolor, daystilldiv[maxyieldindex[k]], kolor, nextdiv[maxyieldindex[k]]/price_at_date[maxyieldindex[k]]*100.0);
     fprintf (ff, "<td bgcolor=%s align=right>%7.4f</td><td bgcolor=%s align=right>%d</td>\n\n", 
       kolor, maxyields[k], kolor, volume[k]);
     fprintf (ff, "<td bgcolor=%s align=right>%7.2f</td></TR>\n\n", 
         kolor, price_at_date[maxyieldindex[k]]*volume[k]);
     if (color) color =0; else color=1;
     total_portfolio_value += volume[k] * price_at_date[maxyieldindex[k]];
   }
   fprintf (ff, "</table><P><table border=1>\n");
   fprintf (ff, "<tr><td bgcolor=grey><font color=white>Dividends paid</td><td align=right>%7.2f</td><td><font size=-1>(%s)</font></td></tr>\n", div_paid, divcomment);
   fprintf (ff, "<tr><td bgcolor=grey><font color=white>Sold</td><td align=right>%7.2f</td><td><font size=-1>(%s)</font></td></tr>\n", sold, comment);
   fprintf (ff, "<tr><td bgcolor=grey><font color=white>Purchased</td><td align=right>%7.2f</td><td><font size=-1>(%s)</font></td></tr>\n", invested, buycomment);
   fprintf (ff, "<tr><td bgcolor=grey><font color=white>Stock</td><td align=right>%7.2f</td></tr>\n", total_portfolio_value);
   fprintf (ff, "<tr><td bgcolor=grey><font color=white>Wallet</td><td align=right>%7.2f</td></tr>\n", wallet);
   if (is_last)
    fprintf (ff, "<tr><td bgcolor=grey><font color=white>Total assets</td><td align=right><font color=red>%7.2f</b></td></tr></table><HR><P>\n", total_portfolio_value+wallet);
   else fprintf (ff, "<tr><td bgcolor=grey><font color=white>Total assets</td><td align=right>%7.2f</td></tr></table><HR><P>\n", total_portfolio_value+wallet);
//printf ("%s %8.2f\n", till, total_portfolio_value + wallet);
   return total_portfolio_value+wallet;
}
         
void count_risks_and_benefits (int i, int date_from, int date_to) // for the stock 'i'
{ FILE *ff; int next_div_date_int;
   // div by seconds in the year; previously (date_to-start_date)/SECSYEAR
  // counting the AVERAGE dividend
  yearlydiv[i]=0; variance[i]=0; 
  price_at_date[i]=0.0; nextdiv[i]=0.0;

  memset (date_for_price[i], 0, sizeof(date_for_price[i]));
  memset (nextdivdate[i], 0, sizeof(nextdivdate[i]));
  
  div_in_range[i]=0; 
  averagediv[i]=0;
  for (j=0; j<divcount[i]; j++) 
   if (div_date_int[i][j] >= date_from && div_date_int[i][j] <= date_to) // date_from = date_to-VARPERIOD+SECSPERDAY
     // ale dlaczego dla 3 lat wstecz? bo VARPERIOD to 3 lata
    { div_in_range[i]++;
      averagediv[i] += divid[i][j];
    } 
    // knowing the order of dates you could break the loop when eg. dateint<start_date
  yearlydiv[i] = averagediv[i] / year_count; // year_count is a constant
  if (divcount[i]) averagediv[i] /= div_in_range[i]; // TBD error to log

  // counting the VARIANCE 
  color=0;
  sprintf (cmd,"../html/%s-div-%s.html",symb[i], datefromsecs (date_to, NULL) );
  ff = fopen(cmd,"w"); 
  fprintf (ff, "<html><body><center><b>List of dividend payments</B><br>Stock symbol: %s", symb[i]);
  fprintf (ff, "<table border=1><th bgcolor=gray><font color=white>Date</th><th bgcolor=gray><font color=white>Ammount</th>\n");
  variance[i]=0.0;
  for (j=0; j<divcount[i]; j++)
  { 
    if (div_date_int[i][j] >= date_from && div_date_int[i][j] <=date_to)
    { var = powf((divid[i][j]-averagediv[i]), 2.0);
      variance[i] += var;
      if (color) kolor="silver"; else kolor="white";
      fprintf (ff, "<tr><td bgcolor=%s align=center>%s</td><td bgcolor=%s align=right>%6.2f</td></tr>\n", 
           kolor, divdate[i][j], kolor, divid[i][j]);
    } 
    if (color) color =0; else color=1;
     // knowing the order of dates you could break the loop when eg. dateint<start_date
  }
  if (div_in_range[i]-1!=0) 
    variance[i] /= (div_in_range[i]-1); // -1 from ex post definition
  else 
    variance[i] = 1000.0; // TBD - error log
  
  fprintf (ff,"</table><table border=1><tr><td align=right>Yearly average</td><td align=right>%6.2f</td></tr><tr><td align=right>Average</td><td align=right>%6.2f</td></tr><tr><td align=right>Variance</td><td align=right>%7.4f</td></tr></table></center></body></html>\n", 
                yearlydiv[i], averagediv[i], variance[i]);
  fclose (ff);

   // finding the PRICE for calculation of yield (it will not necessarily be the same as the end of the period
   // this price will also be used for stock purchase and sell of the previous portfolio
  for (j=0; j<quotecount[i]; j++)
    if (date_int[i][j] <= date_to) // assuming that date[i][0] is near today. otherwise it should be >=. Depends on how Yahoo generates the CSV files
     { price_at_date[i] = price[i][j]; 
       strcpy (date_for_price[i], date[i][j]);
       break; 
     }
     
   // FORECASTing the next dividend - date as in the year before, value as the last payment (not an average)
   // find the DATE of the next dividend
  found=0;

  for (j=0; j<divcount[i]; j++) // it was j=1, why?
    if (div_date_int[i][j] <= date_to)
     { found=1;
       nextdiv[i] = divid[i][j]; // ASSUMPTION: the value of last dividend - this will be NEXT PAYMENT value
       // the next dividend date will be the +time-span between the found one and the previuos one (if exists)
       if (j+1 < divcount[i] && div_date_int[i][j+1] > 0) // the earlier dividend date exists
         next_div_date_int = div_date_int[i][j] + div_date_int[i][j] - div_date_int[i][j+1] ; 
       else // just add 3 months TBD - just check how many cases there are
         next_div_date_int = div_date_int[i][j]+SECSPERDAY*90; // just in 3 months
       datefromsecs (next_div_date_int, nextdivdate[i]); 
       break; // found the dividend more than a year ago
     }
  if (found)
  { // now how many DAYS is it to the nearest dividend
    if (strlen(nextdivdate[i])>0)
      daystilldiv[i] = (next_div_date_int - date_to) / (SECSPERDAY);
    else daystilldiv[i] = -1;
  }
  else 
    fprintf (flog, "Error - next dividend day not found for day %s for stock:%s (divcount: %d; quotecount: %d) \n", 
        datefromsecs(date_to, NULL), symb[i], divcount[i], quotecount[i]);
}

// find a price for a date or a date earlier 
/*
float find_price_4_date (int i, int date2)
{ int j;
  for (j=0; j<quotecount[i]; j++)
    if (date_int[i][j]<=date2) // assuming that date[i][0] is near today. otherwise it should be >=. Depends on how Yahoo generates the CSV files
       break; 
  return price[i][j];
}
*/
  
void find_best_stocks (float max_var, int mindivcount)  // find 'MAXST' best stocks
{ int i, m, k, found;
  for (k=0; k<MAXST; k++)
  { maxyields[k]=0.0;
    for (i=0; i<numb_of_stocks; i++)
      if (divcount[i]>0 && quotecount[i]>0 && variance[i]<=max_var && div_in_range[i] >= mindivcount 
          && (nextdiv[i] / price_at_date[i]) / daystilldiv[i] > maxyields[k])  // previously yearlydiv[i]/price_at_date[i]
            // nextdiv[i]/price_at_date[i])/daystilldiv[i]
      { // check if already put into the portfolio
        found=0;
        for (m=0; m<k; m++)
         if (maxyieldindex[m]==i)
         { found=1;
           break;
         }
        if (!found)
        { maxyields[k] = (nextdiv[i] / price_at_date[i]) / daystilldiv[i]; // previously yearlydiv[i]/price_at_date[i]
        																// two different approaches
          maxyieldindex[k]=i;
        }
      }
  }
}

void backup_previous_portfolio(void)
{ int k;
  for (k=0; k<MAXST; k++)
  { prev_maxyieldindex[k] = maxyieldindex[k];
    prev_volume[k] = volume[k]; 
  }
}

float sell_previous_portfolio (void)
{ int k,m,found;
  float value=0.0, price;
  char str[200];
  memset(comment,0,sizeof(comment));
  sold=0.0;
  if (prev_maxyieldindex[0]==-1) return 0.0; // first period
  for (k=0; k<MAXST; k++)
  {
     //price = find_price_4_date (prev_maxyieldindex[k], date_secs);
      value += price_at_date[prev_maxyieldindex[k]] * prev_volume[k];
      sprintf (str, "<font color=black>[<B>%s</B>|%d|%5.2f|%5.2f]</font> ", 
        symb[prev_maxyieldindex[k]], prev_volume[k], price_at_date[prev_maxyieldindex[k]], 
        price_at_date[prev_maxyieldindex[k]] * prev_volume[k]);
      strcat (comment, str); 
      //prev_maxyieldindex[k]=-1;
  }
  sold = value;
  return value;
}

float dividends_paid (int start, int end)
{ int k,j; float value=0.0;
  memset (divcomment, 0, sizeof(divcomment));
  for (k=0; k<MAXST; k++)
   for (j=0; j<divcount[prev_maxyieldindex[k]]; j++)
     if (div_date_int[prev_maxyieldindex[k]][j]>=start && div_date_int[prev_maxyieldindex[k]][j]<=end)
     { value += divid[prev_maxyieldindex[k]][j]*prev_volume[k];
       sprintf (str, "<font color=black>[<B>%s</B>|%s|%d|%5.2f]</font> ", 
           symb[prev_maxyieldindex[k]], divdate[prev_maxyieldindex[k]][j], 
           prev_volume[k], divid[prev_maxyieldindex[k]][j]);
       strcat (divcomment, str); 
     }
  return value;
}

void buy_new_stock (void)
{ int k,m,found, desired_vol;
  float spent=0.0, to_invest=0.0; // that is a rough assumption - does not take into considaration the number of stock that will stay in the portfolio
  invested=0.0;
  memset(buycomment,0,sizeof(buycomment));
  to_invest = wallet / MAXST; // just an assumption - it is split equally (sounds stupid)
  //to_invest = wallet / (MAXST-old_count);
  // now buy the new stock
  for (k=0; k<MAXST; k++)
  { // check if it was in the previous portfolio
    spent=0.0;
    if (price_at_date[maxyieldindex[k]]<0.000001) {
    	  printf("price at date is wrong\n");
    	  exit(0);
    }
    volume[k] = (int) (to_invest/price_at_date[maxyieldindex[k]]);
    if (wallet >= price_at_date[maxyieldindex[k]] * volume[k]) // new stock to be bought
    { spent = price_at_date[maxyieldindex[k]] * volume[k];
      invested += spent;
      wallet -= spent;
      sprintf (str, "[<B>%s</B>|%d|%5.2f|%5.2f] ", symb[maxyieldindex[k]], volume[k], 
          price_at_date[maxyieldindex[k]], spent);
      strcat (buycomment, str); 
    }
    else {
    	// TODO:
    }
  }
}

void reset_portfolio (void)
{ int i;
  for (i=0; i<MAXST;i++)
  { maxyieldindex[i]=-1;
    prev_maxyieldindex[i]=-1;
    volume[i]=0; 
    prev_volume[i]=0;
    maxyields[i]=0.0;
  }
  wallet=FUND;
}

float simulate (FILE *f, FILE *fpl, char * colour, int simidx, int numb_of_periods, 
        int start, int length, float variance, int mindividnumber)
{ int s,i, is_last;   char filename[20];
  float assets=0.0, divid_paid;

  reset_portfolio();
/*  fprintf (f, "<font color=blue><B><a href= sim-%d.html>Simulation</a> - date: %s, number of periods: %d, length of period (days): %d</font><BR>\n", 
      simidx, datefromsecs(start,NULL), numb_of_periods, length/SECSPERDAY);
*/
  sprintf (filename, "../html/sim-%d.html", simidx);
  FILE *ff = fopen (filename, "w");
  fprintf (ff, "<html><body><center>");

  for (s=0; s<numb_of_periods+1; s++, start += length ) // simulation phases; n+1 as eg. for 1 period there 2 phases - buy and sell
  { divid_paid=0.0;
    for (i=0; i<numb_of_stocks; i++) // numb_of_stocks = number of rows in SP100.txt
      if (divcount[i]>MINDIVCOUNT && quotecount[i]>0)
        count_risks_and_benefits (i, start - VARPERIOD+SECSPERDAY, start); // VARPERIOD = 3 years
    backup_previous_portfolio();
    find_best_stocks (variance, mindividnumber);
    wallet += sell_previous_portfolio();
    divid_paid = dividends_paid (start - length + SECSPERDAY, start );
    wallet += divid_paid;
    if (s==numb_of_periods) is_last=1; else is_last=0;
    //if (!is_last) buy_new_stock(); else invested=0;
    buy_new_stock();
    
    assets = generate_report(ff, datefromsecs(start, NULL), is_last, divid_paid);
    if (is_last)
    { printf ("%5.3f | ", assets/FUND);
      fprintf (f, "<td bgcolor=%s align=right><A HREF=sim-%d.html>%5.2f</a></td>",
         colour, simidx, 100*(assets/FUND-1));
      fprintf (fpl, "%5.2f ",  100*(assets/FUND-1));
    }
  }
  fprintf (ff, "</center></body></html>");
  fclose (ff);
  return assets;
}

main (int argc, char* argv[])
{ /*float arg_v1=0.00001, arg_v2=0.5;
  int arg_p1=1, arg_p2=12, arg_v3=5, arg_y1=11, arg_y2=11;
  */
  float arg_v1=0.00001, arg_v2=0.00002;
  int arg_p1=12, arg_p2=12, arg_v3=5, arg_y1=10, arg_y2=10;
  int i, s, p, simindx, col=0,y;
  
  float avg_assets; int sim_count; // for average asset increase calculation

  float v;
  char starting_date[20], *colour;
  int starting_date_secs=0, initial_date;
  FILE *f, *fplot;

  ret=0; color=1;
  str=malloc(100); str2=malloc(100);

  wallet=FUND;
  flog = fopen ("../stocks_log.txt","w");
  
  // parsing command line arguments
  if (argc < 15) { // Check the value of argc. If not enough parameters have been passed, inform the user and exit.
    printf ("Usage is:\n -y1 <first year>\n -y2 <last year>\n -v1 <lowest variance>\n");
    printf (" -v2 <biggest variance>\n -v3 <multiplication of variance>\n -p1 <min number of periods>\n -p2 <max number of periods>\n");
    exit(0);
  } else  
  for (i = 1; i < argc; i+=2) 
        if (strcmp(argv[i],"-y1")==0) arg_y1 = atoi (argv[i + 1]);
        else if (strcmp(argv[i],"-y2")==0) arg_y2 = atoi (argv[i + 1]);
        else if (strcmp(argv[i],"-v1")==0) sscanf (argv[i + 1],"%f", &arg_v1);
        else if (strcmp(argv[i],"-v2")==0) sscanf (argv[i + 1],"%f", &arg_v2);
        else if (strcmp(argv[i],"-v3")==0) arg_v3 = atoi (argv[i + 1]);
        else if (strcmp(argv[i],"-p1")==0) arg_p1 = atoi (argv[i + 1]);
        else if (strcmp(argv[i],"-p2")==0) arg_p2 = atoi (argv[i + 1]);
        
  reset_portfolio(); // for (k=0; k<MAXST; k++) prev_maxyieldindex[k]=-1;
  system ("rm -rf html; mkdir html");
  // READING  
  // reading the symbol table
  numb_of_stocks = read_share_symbols ("SP100.txt");
  
  // reading the PRICE and DIVIDEND tables
  for (i=0; i<numb_of_stocks; i++) read_history(i);

 // generate_history_HTMLs();
  
  color=0;
  f = fopen("../html/index.html","w");
  fplot = fopen("../plot-input.txt","w");
  fprintf (f, "<html><body><center>");
  simindx = 0 ; // only for html files indexing
  for (y=arg_y1; y<=arg_y2; y++)
  { if (y<10) sprintf (starting_date, "200%d-01-01", y);
    else sprintf (starting_date, "20%d-01-01", y);
    starting_date_secs = to_seconds (starting_date);
    initial_date = starting_date_secs;
    fprintf (f, "Starting date: %s<table border=1>", datefromsecs(initial_date,NULL) );
    sim_count=0;
    avg_assets=0.0; // let us count the average asset increase for the whole simulation table
    
    printf ("Variance |");
    for (p=arg_p2; p>=arg_p1; p--) printf ("  %2d   |", p); // numer of periods
    printf ("\n");
    
    fprintf (f, "<tr><td bgcolor=gray align=center><font color=white>Variance</font></td>");
    for (p=arg_p2; p>=arg_p1; p--) 
      fprintf (f, "<td bgcolor=gray align=center><font color=white>%d</font></td>", p);
    fprintf (f, "</tr>");  
    
    for (v=arg_v1; v<arg_v2; v *= arg_v3) // see multiplication by v3 !
    { printf (" %7.5f | ", v);
      fprintf (fplot, "%7.5f ", v);
      if (col) colour="silver"; else colour="white";
      fprintf (f, "<tr><td bgcolor=%s align=center>%7.5f</td>", colour, v);
      //for (p=arg_p2; p>=arg_p1; p--)
      //for (p=1; p<=4; p*=2) // year, half year, quorter
      {
    	/*avg_assets += simulate (f, fplot, colour, simindx,
    			10*p,  // numb_of_periods, should total to 10 years with 'length' beneath
				initial_date,
				SECSYEAR/p, //length,
				v, // variance
				min_numb_of_divid);
				*/
    	avg_assets += simulate (f, fplot, colour, simindx, 10, initial_date, SECSYEAR, v, min_numb_of_divid);
        simindx++;
        sim_count++;
      }
      if (col) col =0; else col=1;
      fprintf (f, "</tr>");
      printf ("\n");
      fprintf (fplot, "\n");
    }
    fprintf (f, "</table>");
    fprintf (f, "Average yield: %5.1f%%<HR>", (avg_assets/sim_count)/10-100);
  }
  fprintf (f, "</center></body></html>\n");
  fclose (f);
  fclose (fplot);
  fclose (flog);
}

