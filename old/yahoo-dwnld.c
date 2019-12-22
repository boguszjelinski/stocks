#include <stdio.h>
main ()
{ FILE *f=fopen("SP100.txt","r");
  int ret=0;
  char *str=malloc(100);
  char cmd[200];
  system ("rm -rf ../data/*");
  while (1)
  { ret = fscanf (f, "%s", str);
    // s - symbol
    // n - name
    // l1 - last trade
    // d2 - trade date
    // v - volume
    // http://download.finance.yahoo.com/d/quotes.csv?s=IBM&f=snd2l1v
    // http://download.finance.yahoo.com/d/quotes.csv?s=IBM&f=n
    if (ret == EOF) break;
    sprintf (cmd,"wget --quiet -O ../data/%s-div 'http://ichart.finance.yahoo.com/table.csv?s=%s&a=00&b=1&c=2000&d=07&e=18&f=2012&g=v&ignore=.csv'",str, str);
    system (cmd); 
    sprintf (cmd,"wget --quiet -O ../data/%s-prc 'http://ichart.finance.yahoo.com/table.csv?s=%s&a=00&b=1&c=2000&d=07&e=18&f=2012&g=d&ignore=.csv'",str, str);
    system (cmd); 
    sprintf (cmd,"wget --quiet -O ../data/%s-nme 'http://download.finance.yahoo.com/d/quotes.csv?s=%s&f=nr1&ignore=.csv'",str, str);
    system (cmd); 
    //printf ("%s\n", cmd);
  }    
  fclose (f);
}
