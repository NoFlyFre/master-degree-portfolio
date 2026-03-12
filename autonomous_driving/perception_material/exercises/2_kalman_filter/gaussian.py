from math import *
import matplotlib.pyplot as plt
import numpy as np
import sys

def f(mu,sigma2,x):
	return 1/sqrt(2.*pi*sigma2)*exp(-.5 * (x-mu)**2 / sigma2)



def plot(mu,sig):

    # define a range of x values
    x_axis = np.arange(-20, 30, 0.1)

    # create a corresponding list of gaussian values
    g = []
    for x in x_axis:
      g.append(f(mu, sig, x))

    # plot the result 
    plt.plot(x_axis, g)
    plt.show()

plot(8,4)
plot(18,10)
