package org.twak.viewTrace.franken.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JPanel;


public class MixStyle implements StyleSource {

	class Wrapper{
		StyleSource ss;
		double prob;
		double accumProb;
	}
	
	List<Wrapper> styles = new ArrayList<>();
	
	public double totalProb;
	
	public MixStyle() {}
	
	public void updateStyles() {
		totalProb = 0;
		
		for ( Wrapper w : styles ) {
			totalProb += w.prob;
			w.accumProb = totalProb;
		}
	}
	
	@Override
	public double[] draw( Random random ) {
		
		double d = random.nextDouble() * totalProb;
		
		for (Wrapper w : styles) 
			if (d < w.accumProb)
				return w.ss.draw( random );

		throw new Error();
	}

	@Override
	public JPanel getUI( Runnable update ) {
		return new JPanel();
	}

}
