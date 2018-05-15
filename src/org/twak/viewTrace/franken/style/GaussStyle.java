package org.twak.viewTrace.franken.style;

import java.util.Random;

public class GaussStyle implements StyleSource {
	
	double[] mean;
	double std;
	
	public GaussStyle(double[] mean, double dev) {
		this.mean = mean;
		this.std = dev;
	}
	
	@Override
	public double[] draw( Random random ) {
		
		double[] out = new double[mean.length];
		
		for (int i = 0; i < out.length; i++)
			out[i] = random.nextGaussian() * std + mean[i];
		
		return out;
	}
}
