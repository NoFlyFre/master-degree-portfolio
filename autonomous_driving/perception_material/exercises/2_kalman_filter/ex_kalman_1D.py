from math import *
import matplotlib.pyplot as plt
import numpy as np
import sys

#blue update
#orange prediction

def f(mu, sigma2, x):
    coefficient = 1.0 / sqrt(2.0 * pi *sigma2)
    exponential = exp(-0.5 * (x-mu) ** 2 / sigma2)
    return coefficient * exponential

def plot(mu_update,sig_update,mu_predict,sig_predict):

    # define a range of x values
    x_axis = np.arange(-5, 25, 0.1)

    # create a corresponding list of gaussian values
    g_update = []
    for x in x_axis:
      g_update.append(f(mu_update, sig_update, x))

    g_predict = []
    for x in x_axis:
      g_predict.append(f(mu_predict, sig_predict, x))

    # plot the result 
    plt.plot(x_axis, g_update)
    plt.plot(x_axis, g_predict)
    plt.show()

def update(mean1, var1, mean2, var2):
	new_mean = (var2*mean1 + var1*mean2) / (var1+var2)
	new_var  = 1/(1/var1 + 1/var2)
	return [new_mean,new_var]

#print update(10.,4.,12.,4.)

def predict(mean1, var1, mean2, var2,U):
	new_mean = mean1+mean2+U
	new_var  = var1+var2
	return [new_mean,new_var]

#print predict(10.,4.,12.,4.)
# We start measuring the object in the position 3...
measurements = [3.,4.,6.,7.,8.]
motion = [1., 2., 1., 1., 1.]
measurement_sigma = 4.  #measurement uncertainty
motion_sigma = 2.	    #motion uncertainty
mu = 0				    #initial position estimate (and mu later represents the position)
position_sigma = 10000. #position certainty
U=0

for n in range(len(measurements)):
	[mu,position_sigma] = update(mu,position_sigma,measurements[n],measurement_sigma)
	print ('update [mean,var]: ',[mu,position_sigma])

	#just to plot
	mu_u=mu
	position_sigma_u=position_sigma

	[mu,position_sigma] =predict(mu,position_sigma,motion[n],motion_sigma,U)
	print ('predict [mean,var]: ',[mu,position_sigma])
    
	plot(mu_u,position_sigma_u,mu,position_sigma)

#for n in range(len(measurements)):
	#[mu,position_sigma] =predict(mu,position_sigma,motion[n],motion_sigma,U)
	#plot(mu_u,position_sigma_u,mu,position_sigma)

#[mu,position_sigma] = update(mu,position_sigma,15,measurement_sigma)
#plot(mu_u,position_sigma_u,mu,position_sigma)


#the first estimate is dominated by the measurement

#plot(mu,position_sigma)
#update:  [2.998800479808077, 3.9984006397441023] , x position. Uncertainties (the smaller the better)
