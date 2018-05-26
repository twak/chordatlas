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

	public static class Mode {
		public GaussStyle ss;
		public double prob = 0.5;
		public double accumProb;
	}
	
	public List<Mode> modes = new ArrayList<>();
	
	public double totalProb;
	
	NetInfo exemplar;
	
	public MultiModal(NetInfo ex) {
		this.exemplar = ex;
	}
	
	public void updateModes() {
		totalProb = 0;
		
		for ( Mode w : modes ) {
			totalProb += w.prob;
			w.accumProb = totalProb;
		}
	}
	
	@Override
	public double[] draw( Random random, App app ) {
		
		double d = random.nextDouble() * totalProb;
		
		for (Mode w : modes) 
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

	public Mode newMode() {
		
		Mode out = new Mode();
		out.ss = new GaussStyle( exemplar );
		modes.add( out );
		
		updateModes();
		
		return out;
	}
	
	public boolean install( SelectedApps next ) {
		return false;
	}
}
