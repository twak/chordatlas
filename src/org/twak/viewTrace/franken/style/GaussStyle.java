package org.twak.viewTrace.franken.style;

import java.awt.BorderLayout;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.style.ui.UIVector;

public class GaussStyle implements StyleSource {
	
	public double[] mean;
	double std;
	App app;
	
	public GaussStyle(App app) {
		this.mean = new double[app.sizeZ];
		this.std = 0;
		this.app = app;
	}
	
	@Override
	public double[] draw( Random random ) {
		
		double[] out = new double[mean.length];
		
		for (int i = 0; i < out.length; i++)
			out[i] = random.nextGaussian() * std + mean[i];
		
		return out;
	}

	@Override
	public JPanel getUI( Runnable update ) {

		JPanel out = new JPanel(new ListDownLayout() );
		
		JPanel line = new JPanel(new BorderLayout() );
		
		line.add( new JLabel("std:"), BorderLayout.WEST );
		
		JSlider deviation = new JSlider(0, 1000, 0);
		
		line.add( deviation, BorderLayout.CENTER );
		
		out.add( line );
		deviation.addChangeListener( new ChangeListener() {
			@Override
			public void stateChanged( ChangeEvent e ) {
				if (!deviation.getValueIsAdjusting()) {
					std = deviation.getValue() / 500.;
					update.run();
				}
			}
		} );
		
//		JButton go = new JButton("resample");
//		go.addActionListener( e -> update.run() );
//		out.add( go );
		
		out.add( new UIVector (mean, app, false, update ) );
		
		return out;
	}
}
