package org.twak.viewTrace.franken.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JPanel;

import org.twak.tweed.gen.skel.AppStore;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.style.ui.MultiModalEditor;



public class MultiModal implements StyleSource {

	public static class Mode {
		public GaussStyle ss;
		public double prob = 0.5;
		public double accumProb;
		
		public Mode copy() {
			Mode out = new Mode();
			out.ss = (GaussStyle) ss.copy();
			out.prob = prob;
			out.accumProb = accumProb;
			return out;
		}
	}
	
	public List<Mode> modes = new ArrayList<>();
	
	public double totalProb;
	
	NetInfo exemplar;
	
	public MultiModal(NetInfo ex) {
		this.exemplar = ex;
	}


	@Override
	public StyleSource copy() {
		MultiModal out = new MultiModal( exemplar );
		
		for (Mode m : modes) 
			out.modes.add(m.copy());
		
		out.totalProb = totalProb;
		
		return out;
	}
	public void updateModes() {
		totalProb = 0;
		
		for ( Mode w : modes ) {
			totalProb += w.prob;
			w.accumProb = totalProb;
		}
	}
	
	@Override
	public double[] draw( Random random, App app, AppStore ac ) {
		
		double d = random.nextDouble() * totalProb;
		
		for (Mode w : modes) 
			if (d < w.accumProb)
				return w.ss.draw( random, app, ac );

		return new double[exemplar.sizeZ];
	}

	@Override
	public JPanel getUI( Runnable update, SelectedApps sa ) {
		JPanel out = new JPanel();
		
		JButton but = new JButton( "edit multimodal" );
		but.addActionListener( e -> new MultiModalEditor( this, exemplar, update, sa.ass ).openFrame() );
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
