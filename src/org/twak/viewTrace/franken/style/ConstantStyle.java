package org.twak.viewTrace.franken.style;

import java.util.Random;

import javax.swing.JPanel;

import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;

public class ConstantStyle implements StyleSource {
	
	double[] mean;
	App app;
	
	public ConstantStyle(App app) {
		this.mean = new double[app.sizeZ];
		this.app = app;
	}
	
	@Override
	public double[] draw( Random random ) {
		return mean;
	}
	
	@Override
	public JPanel getUI( Runnable update ) {

		JPanel out = new JPanel(new ListDownLayout() );
		
		out.add( new UIVector (mean, app, update ) );
		
		return out;
	}
}
