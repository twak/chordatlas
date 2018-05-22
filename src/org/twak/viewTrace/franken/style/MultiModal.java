package org.twak.viewTrace.franken.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.style.ui.MultiModalEditor;



public class MultiModal implements StyleSource {

	public static class Wrapper{
		public GaussStyle ss;
		public double prob = 0.5;
		public double accumProb;
	}
	
	public List<Wrapper> styles = new ArrayList<>();
	
	public double totalProb;
	
	NetInfo exemplar;
	
	public MultiModal(NetInfo ex) {
		this.exemplar = ex;
	}
	
	public void updateStyles() {
		totalProb = 0;
		
		for ( Wrapper w : styles ) {
			totalProb += w.prob;
			w.accumProb = totalProb;
		}
	}
	
	@Override
	public double[] draw( Random random, App app ) {
		
		double d = random.nextDouble() * totalProb;
		
		for (Wrapper w : styles) 
			if (d < w.accumProb)
				return w.ss.draw( random, app );

		return new double[exemplar.sizeZ];
	}

	@Override
	public JPanel getUI( Runnable update ) {
		JPanel out = new JPanel();
		
		JButton but = new JButton( "edit multimodal" );
		but.addActionListener( e -> new MultiModalEditor( this, exemplar, update ).openFrame() );
		out.add( but );
		
		return out;
	}

	public Wrapper newWrapper() {
		
		Wrapper out = new Wrapper();
		out.ss = new GaussStyle( exemplar );
		styles.add( out );
		
		updateStyles();
		
		return out;
	}
	
	public boolean install( SelectedApps next ) {
		return false;
	}
}
