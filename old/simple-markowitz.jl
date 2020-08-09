using JuMP
using Ipopt
N=4
r_min=0.035
m = Model(with_optimizer(Ipopt.Optimizer))
C = [0.076900 0.039968 0.018111 -0.000288 ; 0.039968 0.050244 0.019033 -0.000060 ; 0.018111 0.019033 0.021381 0.007511 ; -0.000288 -0.000060 0.007511 0.008542]
Mean = [0.0073 0.0346 0.0444 0.0271]
@variable(m, 0 <= x[i=1:N] <= 1)
@objective(m,Min,sum(x[j]*sum(x[i]*C[i,j] for i=1:N) for j=1:N))
@constraint(m, sum(x[i]*Mean[i] for i=1:N) >=r_min)
@constraint(m, sum(x[i] for i=1:N) ==1.0)
status = optimize!(m)
for i= 1:N
println(getvalue(x[i]))
end