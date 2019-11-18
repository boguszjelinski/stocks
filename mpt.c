#include <stdio.h>
#include <time.h>
#include <math.h>
#include "IpStdCInterface.h"
#include <stdlib.h>
#include <assert.h>

#define MAXCO 102  // max number of companies
#define MAXQU 15000  // max number of quotations
#define SECSYEAR 31536000 // number of seconds in a year 60*60*24*365
#define VARPERIOD SECSYEAR  // ??? dlaczego 3
#define FUND 100000.0
#define FALSE 0
#define TRUE 1

#define NUMPERIODS 12

int main_ipopt(float min_ret);

Number c[MAXCO][MAXCO], MoR[MAXCO];
Index n;                          /* number of variables */
 // !! number of stocks for the solver, size of the covariance matrix, and ..
          // .. important count for iterations across the whole programme

int numb_of_stocks=0, stock_idx[MAXCO], prev_stock_idx[MAXCO];  // our IPOPT problem will have less portfolios than S&P100, so we have to have a map to come back
int volume[MAXCO], divcount[MAXCO], quotecount[MAXCO], div_date_int[MAXCO][MAXQU/5], date_int[MAXCO][MAXQU];
short int err[MAXCO];
char symb[MAXCO][10], nme[MAXCO][80], date[MAXCO][MAXQU][12], divdate[MAXCO][MAXQU/5][12];
char comment[1000], divcomment[1000];

float price[MAXCO][MAXQU], price_at_date[MAXCO], divid[MAXCO][MAXQU/5], var,
       first_price[MAXCO], last_price[MAXCO], stock_return[MAXCO][NUMPERIODS];
float Diff[MAXCO][NUMPERIODS], weights[MAXCO], wallet;

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
  else printf ("Error getting date from secs  \n");
  return ret;
}

int read_share_symbols (char * file_name)
{ int counter=0, ret;
  char *str, cmd[400];
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
{ char chr, cmd[400];
  int bytes=0, volume2, ret, j, k;
  float open, high, low, close, adj_close;

  if (strlen(symb[i]) <= 0) return; // 'symb' is read in read_share_symbols, shouldn't have empty items
// reading PRICEs
//
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
//
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
//
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
  /*k=0;
  while ((ret=fgetc(ff))!='"') paydate[i][k++]=ret;
  */
  fclose(ff);
}

// returns total return of the stock within a period
// and error code
short int
count_benefits (int i, int begin, int end, float *reve) // for the stock 'i'
{ int j;
   // div by seconds in the year; previously (the_date-start_date)/SECSYEAR
  // counting the AVERAGE dividend
  float dividend=0, last_prc=-1, first_prc=-1;
    // TODO: check if the period is covered by data.
  if (divcount[i]<1) return -1;

  for (j=0; j<divcount[i]; j++) {
    if (div_date_int[i][j] < begin) break; // we assume the order is "today is in the beginning of the data"
    if (div_date_int[i][j] >= begin && div_date_int[i][j] <= end)
      dividend += divid[i][j];
  }
    // find the last/sale quotation (price) in the period
  for (j=0; j<quotecount[i]; j++)
    if (date_int[i][j] <= end) {
        last_prc = price[i][j]; // the price of sale;
        break;
    }
  if (last_prc>=0)
  {   // go with the index to the beginning
    for ( ; j<quotecount[i]; j++)
        if (date_int[i][j] < begin) {
            first_prc = price[i][j-1];
            break;
        }
    if (last_prc<0) // maybe raise en error - first quotation not in the beginning of the period
        first_prc = price[i][j-1]; // the first quotation was within the period

    *reve = (last_prc - first_prc + dividend) / first_prc;
    return 1;
  }
  return -2;
//  printf ("Stock %s, first date %s, first price: %6.2f, last date %s, last price %6.2f, dividend: %6.2f, return: %6.4f\n",
//    symb[i], date[i][first_idx], first_price[i], date[i][last_idx], last_price[i], totaldiv[i], stock_return[i]);
  //printf (" %s: %6.4f\n", symb[i], stock_return[i]);
}

float dividends_paid (int stock, int start, int end)
{ int j; float value=0.0; char str[400];
  memset (divcomment, 0, sizeof(divcomment));
  for (j=0; j<divcount[stock]; j++)
     if (div_date_int[stock][j]>=start && div_date_int[stock][j]<=end)
     { value += divid[stock][j]*volume[stock];
       sprintf (str, "<font color=black>[<B>%s</B>|%s|%d|%5.2f]</font> ",
           symb[stock], divdate[stock][j], volume[stock], divid[stock][j]);
       strcat (divcomment, str);
     }
  return value;
}

char * gen_date (int year, int month, char * str) // !! January must be ZERO for this to work
{ int years = (int)(month/12);
  int mnth = month + 1 - years*12;
  if (mnth>9)
    sprintf (str, "%d-%d-01", year+years, mnth);
  else
    sprintf (str, "%d-0%d-01", year+years, mnth);
  return str;
}

void simulate (
    int year,
    int sim_periods, // in month
    int period_length,
    int risk_ret_periods, float min_ret)
{ int s,i,j,status;
  float assets=0.0, stock_value, divid_paid, divid_paid_total=0.0;
  char the_date[11]; int the_date_secs;
  char the_prev_date[11]; int the_prev_date_secs;
  time_t sec_start, sec_stop;
  sec_start = time (NULL);
   
  //reset_portfolio();

  FILE *ff = fopen ("../mpt.out", "w");
  fprintf (ff, "Markowitz Portfolio Selection (simulation)\n");
  fprintf (ff, "Number of periods: %d\n", sim_periods);
  fprintf (ff, "Period's length: %d months\n", period_length);  
  fprintf (ff, "Number of periods in risk assessment: %d\n", risk_ret_periods);
  fprintf (ff, "Min expected return: %5.4f\n", min_ret);
  fprintf (ff, "Wallet content: %7.2f\n", wallet);
  
  for (s=0; s<sim_periods; s++)
  {
     // the date when we buy and sell. It has to be after the 'historic' data for portfolio selection, so +risk_ret_periods
    fprintf (ff, "============================================\n");

    gen_date (year, (s+risk_ret_periods)*period_length, the_date);
    the_date_secs = to_seconds(the_date);
    gen_date(year, (s+risk_ret_periods-1)*period_length, the_prev_date); // -1
    the_prev_date_secs = to_seconds(the_prev_date);

    fprintf (ff, "Portfolio rebuild on: %s\nCurrent value before rebuild:\n", the_date);
    // finding the price of stocks on that day; all stocks as it will be used for purchase also
    for (i=0; i<numb_of_stocks; i++)
     for (j=0; j<quotecount[i]; j++)
       if (date_int[i][j] <= the_date_secs) // assuming that date[i][0] is near today. otherwise it should be >=. Depends on how Yahoo generates the CSV files
       { price_at_date[i] = price[i][j];
         break;
       }
    fprintf (ff, " Stock | Vol. |  Price  |  Total   | Paid dividend\n-------+------+---------+----------+--------------\n");
    stock_value = 0.0; divid_paid=0.0;
    for (i=0; i<numb_of_stocks; i++)
     if (volume[i]>0) {
       divid_paid = dividends_paid (i, the_prev_date_secs, the_date_secs);
       divid_paid_total += divid_paid;
       fprintf (ff,"%6s | %4d | %7.2f | %8.2f | %6.2f\n",
         symb[i], volume[i], price_at_date[i], price_at_date[i]*volume[i], divid_paid);
       stock_value += price_at_date[i]*volume[i];
     }
    fprintf (ff, "-------+------+---------+----------+--------------\n");
    fprintf (ff, "                  Total:|%9.2f |  %5.2f\n",stock_value, divid_paid_total);
    fprintf (ff, "                        +----------+--------------\n");
      // let us sell all - assumed there is no transaction cost; it would be stupid to sell and buy the same stock
    wallet += stock_value + divid_paid_total;
    fprintf (ff, "In the wallet after sale: %7.2f\n\n", wallet);
    for (i=0; i<numb_of_stocks; i++) volume[i]=0;
    fprintf (ff, "----------- NEW PORTFOLIO ------------------\n");
      //
    status = find_best_portfolio (ff, year, s*period_length, period_length, risk_ret_periods, min_ret);

    if (status != Solve_Succeeded)
      fprintf (ff, "ERROR: solution not found!\n");
    else 
    { for (i=0; i<numb_of_stocks; i++) volume[i]=0;
      fprintf (ff, " Stock | W[%] | Vol. | Price \n-------+------+------+----------\n");
      // buy stock!
      stock_value=0.0;
      for (i=0; i<n; i++) // iterate thru the solver output
       if (weights[i]>0.0001) {
        volume[stock_idx[i]] = (wallet * weights[i])/price_at_date[stock_idx[i]]; // rounding down to integer
        if (volume[stock_idx[i]]>0) 
        {  fprintf (ff,"%6s | %4.1f | %4d | %7.2f\n",
                 symb[stock_idx[i]], weights[i]*100, volume[stock_idx[i]], price_at_date[stock_idx[i]]);
           stock_value += volume[stock_idx[i]]*price_at_date[stock_idx[i]];
        }
      }
      // what is left in the wallet?
      wallet -= stock_value;
      fprintf (ff, "-------+------+------+----------\n");
      fprintf (ff, "              Total: |%8.2f\n", stock_value);
      fprintf (ff, "In the wallet: %7.2f\n\n", wallet);
    }
  }
  sec_stop = time (NULL);
  int hrs,mins,scs;
  hrs = (sec_stop-sec_start)/3600;
  mins = (sec_stop -sec_start - 3600*hrs)/60;
  scs = sec_stop -sec_start - 3600*hrs - mins*60;
  fprintf (ff, "\n Simulation took %dh %dm %ds\n\n", hrs, mins, scs);
  fclose (ff);
  
}

void reset_solver ()
{ int i;
  memset (Diff, 0, sizeof(Diff)); memset (MoR, 0, sizeof(MoR));
  memset (c, 0, sizeof(c)); memset (weights, 0, sizeof(weights));
  for (i=0; i<numb_of_stocks; i++)
  { stock_idx[i] = -1;
    err[i]=0;
  }
}

// returns values in the global 'weights' vector
int find_best_portfolio (
        FILE * f,
        int start_year,
        int start_month,  // !! January must be ZERO
        int period_length, // in month
        int periods,
        Number min_ret // expected return in the Markowitz model
    )
{ int i, j, k, idx_i, idx_j, status;
  char date[11];
  reset_solver();

  for (i=0; i<numb_of_stocks; i++)
/*    if (strcmp(symb[i],"BAC")==0 || strcmp(symb[i],"F")==0 ||
        strcmp(symb[i],"GE")==0 || strcmp(symb[i],"T")==0)
*/
       for (k=0; k<periods; k++) {
          err[i] = count_benefits
            (i, to_seconds(gen_date(start_year, start_month+k*period_length, date)),
             to_seconds(gen_date(start_year, start_month+(k+1)*period_length, date))-1, // minus one second
             &stock_return[i][k]);
          //if (err[i]<0) {
            //printf ("Error (%d) counting benefits for %s\n", err[i], symb[i]);
            //exit(0);
          //}
          //printf ("Stock: %s Start: %s  Ret:%f \n",
            //symb[i], gen_date(start_year, start_month+k*period_length, date), stock_return[i][k]);
      }
    // counting mean values
  for (i=0, idx_i=0; i<numb_of_stocks; i++)
    if (err[i]>0) {
      MoR[idx_i]=0;
      for (k=0;k<periods; k++) MoR[idx_i]+= stock_return[i][k];
      MoR[idx_i] /= periods;
      //fprintf (f, "Stock: %s; MoR[%d]=%f\n", symb[i], idx_i, MoR[idx_i] );
      stock_idx[idx_i] = i;
      idx_i++;
    }
  n = idx_i; // numer of stocks in portfolio for IPOPT; this is a global variable
  fprintf (f, "(Number of stocks for the solver: %d)\n", (int) n);
  //TODO: change 'n' to something more visable

    // calculating covariance matrix, first diffs
    // TODO - it is a symmetric matrix, do not calculate half of it
  for (i=0; i<numb_of_stocks; i++)
    if (err[i]>0)
      for (k=0; k<periods; k++)
         Diff[i][k] = stock_return[i][k]-MoR[i];

  for (i=0, idx_i=0; i<numb_of_stocks; i++)
   if (err[i]>0) {
    idx_j=0;
    for (j=0; j<numb_of_stocks; j++)
       if (err[j]>0) {
         for (k=0; k<periods; k++)
            c[idx_i][idx_j] += Diff[i][k]*Diff[j][k];
         c[idx_i][idx_j] /= (periods-1);
         //fprintf (f, "c[%d][%d]=%6.4f ",idx_i,idx_j,c[idx_i][idx_j]);
         idx_j++;
       }
    idx_i++;
   }

  // calculation of portfolio weights - find out the optimal share of each stock
  status = main_ipopt(min_ret);
  
  if (status == Solve_Succeeded)
  { /* for (i=0; i<n; i++)
     if (weights[i]>0.0001)
        fprintf (f, "%10s: %5.2f\n", symb[stock_idx[i]], weights[i]*100);
    printf ("Exclusions: ");
    for (i=0; i<n; i++)
      if (err[i]<0)
        fprintf (f, "%s(%d), ", symb[stock_idx[i]], err[i]);
    */
  }
  return status;
}

main (int argc, char* argv[])
{ int i;

  for (i=0; i<numb_of_stocks; i++) {
    volume[i]=0; err[i]=-1;
  }
  wallet = FUND;
  reset_solver();
  numb_of_stocks = read_share_symbols ("SP100.txt");
  for (i=0; i<numb_of_stocks; i++) read_history(i);
  
  simulate (2000, 40, 3, 12, 0.035);
  
  //i = main_ipopt(0.035);  
  exit(0);
}


/* Function Declarations */
Bool eval_f(Index n, Number* x, Bool new_x,
            Number* obj_value, UserDataPtr user_data);

Bool eval_grad_f(Index n, Number* x, Bool new_x,
                 Number* grad_f, UserDataPtr user_data);

Bool eval_g(Index n, Number* x, Bool new_x,
            Index m, Number* g, UserDataPtr user_data);

Bool eval_jac_g(Index n, Number *x, Bool new_x,
                Index m, Index nele_jac,
                Index *iRow, Index *jCol, Number *values,
                UserDataPtr user_data);

Bool eval_h(Index n, Number *x, Bool new_x, Number obj_factor,
            Index m, Number *lambda, Bool new_lambda,
            Index nele_hess, Index *iRow, Index *jCol,
            Number *values, UserDataPtr user_data);

Bool intermediate_cb(Index alg_mod, Index iter_count, Number obj_value,
                     Number inf_pr, Number inf_du, Number mu, Number d_norm,
                     Number regularization_size, Number alpha_du,
                     Number alpha_pr, Index ls_trials, UserDataPtr user_data);


/* This is an example how user_data can be used. */
struct MyUserData
{
  Number g_offset[2]; /* This is an offset for the constraints.  */
};

/* Main Program */
int main_ipopt(float min_ret)
{ 

  Index m=-1;                          /* number of constraints */
  Number* x_L = NULL;                  /* lower bounds on x */
  Number* x_U = NULL;                  /* upper bounds on x */
  Number* g_L = NULL;                  /* lower bounds on g */
  Number* g_U = NULL;                  /* upper bounds on g */
  IpoptProblem nlp = NULL;             /* IpoptProblem */
  enum ApplicationReturnStatus status; /* Solve return code */
  Number* x = NULL;                    /* starting point and solution vector */
  Number* mult_g = NULL;               /* constraint multipliers
             at the solution */
  Number* mult_x_L = NULL;             /* lower bound multipliers
             at the solution */
  Number* mult_x_U = NULL;             /* upper bound multipliers
             at the solution */
  Number obj;                          /* objective value */
  Index i;                             /* generic counter */

  /* set the number of variables and allocate space for the bounds */
//  n=20;

  /* Number of nonzeros in the Jacobian of the constraints */
  Index nele_jac = 2*n;
  /* Number of nonzeros in the Hessian of the Lagrangian (lower or
     upper triangual part only) */
  Index nele_hess = n*(n+1)/2;
  /* indexing style for matrices */
  Index index_style = 0; /* C-style; start counting of rows and column
             indices at 0 */

  /* our user data for the function evalutions. */
  struct MyUserData user_data;

  x_L = (Number*)malloc(sizeof(Number)*n);
  x_U = (Number*)malloc(sizeof(Number)*n);
  /* set the values for the variable bounds */
  for (i=0; i<n; i++) {
    x_L[i] = 0.0;
    x_U[i] = 1.0;
  }

  /* set the number of constraints and allocate space for the bounds */
  m=2;
  g_L = (Number*)malloc(sizeof(Number)*m);
  g_U = (Number*)malloc(sizeof(Number)*m);
  /* set the values of the constraint bounds */
  g_L[0] = min_ret;
  g_U[0] = 1e19;
  g_L[1] = 1;
  g_U[1] = 1;

  /* create the IpoptProblem */
  nlp = CreateIpoptProblem(n, x_L, x_U, m, g_L, g_U, nele_jac, nele_hess,
                           index_style, &eval_f, &eval_g, &eval_grad_f,
                           &eval_jac_g, &eval_h);

  /* We can free the memory now - the values for the bounds have been
     copied internally in CreateIpoptProblem */
  free(x_L);
  free(x_U);
  free(g_L);
  free(g_U);

  /* Set some options.  Note the following ones are only examples,
     they might not be suitable for your problem. */
  AddIpoptNumOption(nlp, "tol", 1e-7);
  AddIpoptStrOption(nlp, "mu_strategy", "adaptive");
  AddIpoptStrOption(nlp, "output_file", "ipopt.out");

  /* allocate space for the initial point and set the values */
  x = (Number*)malloc(sizeof(Number)*n);
  x[0] = 1.0;
  x[1] = 5.0;
  x[2] = 5.0;
  x[3] = 1.0;

  /* allocate space to store the bound multipliers at the solution */
  mult_g = (Number*)malloc(sizeof(Number)*m);
  mult_x_L = (Number*)malloc(sizeof(Number)*n);
  mult_x_U = (Number*)malloc(sizeof(Number)*n);

  /* Initialize the user data */
  user_data.g_offset[0] = 0.;
  user_data.g_offset[1] = 0.;

  /* Set the callback method for intermediate user-control.  This is
   * not required, just gives you some intermediate control in case
   * you need it. */
  /* SetIntermediateCallback(nlp, intermediate_cb); */

  /* solve the problem */
  status = IpoptSolve(nlp, x, NULL, &obj, mult_g, mult_x_L, mult_x_U, &user_data);

  if (status == Solve_Succeeded) {
    for (i=0; i<n; i++) weights[i] = x[i];
/*    printf("\n\nSolution of the primal variables, x\n");
    for (i=0; i<n; i++) {
      printf("x[%d] = %e\n", i, x[i]);
    }
    printf("\n\nObjective value\n");
    printf("f(x*) = %e\n", obj);
*/
  }
  else {
    printf("\n\nERROR OCCURRED DURING IPOPT OPTIMIZATION.\n");
  }

  /* free allocated memory */
  FreeIpoptProblem(nlp);
  free(x);
  free(mult_g);
  free(mult_x_L);
  free(mult_x_U);

  return (int)status;
}


/* Function Implementations */
Bool eval_f(Index n, Number* x, Bool new_x,
            Number* obj_value, UserDataPtr user_data)
{
  Number sum;
  int i,j;
  *obj_value=0.0;
  for (i=0; i<n; i++)
  { sum=0.0;
    for (j=0; j<n; j++)
     sum += x[j] * c[j][i];
    *obj_value += x[i]*sum;
  }
  return TRUE;
}

Bool eval_grad_f(Index n, Number* x, Bool new_x,
                 Number* grad_f, UserDataPtr user_data)
{
  int i,j;
  for (i=0; i<n; i++)
  { grad_f[i] =0;
    for (j=0; j<n; j++)
       grad_f[i] += x[j]*c[j][i];
    grad_f[i] = grad_f[i] * 2;
  }
  return TRUE;
}

Bool eval_g(Index n, Number* x, Bool new_x,
            Index m, Number* g, UserDataPtr user_data)
{
  struct MyUserData* my_data = user_data;
  assert(m == 2);
  int i;
  
  g[0]=0.0;
  for (i=0; i<n; i++) g[0] += x[i]*MoR[i];
  g[0] += my_data->g_offset[0];
  
  g[1]=0;
  for (i=0; i<n; i++) g[1] += x[i];
  g[1] += my_data->g_offset[1];

  return TRUE;
}

Bool eval_jac_g(Index n, Number *x, Bool new_x,
                Index m, Index nele_jac,
                Index *iRow, Index *jCol, Number *values,
                UserDataPtr user_data)
{ int i=0;
  if (values == NULL) {
    /* return the structure of the jacobian */
    /* this particular jacobian is dense */
    for (i=0; i<n; i++) { 
      iRow[i] = 0;
      jCol[i] = i;
    }
    for (i=0; i<n; i++) { 
      iRow[n+i] = 1;
      jCol[n+i] = i;
    }
  }
  else {
    /* return the values of the jacobian of the constraints */
    for (i=0; i<n; i++) 
       values[i] = MoR[i]; 
    for ( ; i<n+n; i++) 
       values[i] = 1; 
  }

  return TRUE;
}

Bool eval_h(Index n, Number *x, Bool new_x, Number obj_factor,
            Index m, Number *lambda, Bool new_lambda,
            Index nele_hess, Index *iRow, Index *jCol,
            Number *values, UserDataPtr user_data)
{
  Index idx = 0; /* nonzero element counter */
  Index row = 0; /* row counter for loop */
  Index col = 0; /* col counter for loop */
  if (values == NULL) {
    /* return the structure. This is a symmetric matrix, fill the lower left
     * triangle only. */
    /* the hessian for this problem is actually dense */
    idx=0;
    for (row = 0; row < n; row++) {
      for (col = 0; col <= row; col++) {
        iRow[idx] = row;
        jCol[idx] = col;
        idx++;
      }
    }
    assert(idx == nele_hess);
  }
  else {
    /* return the values. This is a symmetric matrix, fill the lower left
     * triangle only */
    idx=0;
    for (row = 0; row < n; row++) {
      for (col = 0; col <= row; col++) {
        values[idx] = obj_factor * (2*c[row][col]);
        idx++;
      }
    }
  }

  return TRUE;
}

Bool intermediate_cb(Index alg_mod, Index iter_count, Number obj_value,
                     Number inf_pr, Number inf_du, Number mu, Number d_norm,
                     Number regularization_size, Number alpha_du,
                     Number alpha_pr, Index ls_trials, UserDataPtr user_data)
{
  printf("Testing intermediate callback in iteration %d\n", iter_count);
  if (inf_pr < 1e-4) return FALSE;

  return TRUE;
}

