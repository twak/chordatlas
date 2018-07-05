package org.twak.viewTrace.franken.style;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import org.twak.tweed.gen.skel.AppStore;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.style.ui.UIVector;
import org.twak.viewTrace.franken.style.ui.UIVector.MeanImageProvider;

public class ConstantStyle implements StyleSource, MeanImageProvider {
	
	double[] mean;
	Class target;
	File meanImage;
	
	public ConstantStyle(Class target) {
		NetInfo ni = NetInfo.index.get(target);
		this.mean = new double[ni.sizeZ];
	}

	@Override
	public StyleSource copy() {
		ConstantStyle out = new ConstantStyle( target );
		out.meanImage = meanImage;
		out.mean = Arrays.copyOf(mean, mean.length);
		return out;
	}

	
	@Override
	public double[] draw( Random random, App app, AppStore ac ) {
		return mean;
	}
	
	@Override
	public JPanel getUI( Runnable update, SelectedApps sa ) {

		JPanel out = new JPanel(new ListDownLayout() );
		
		out.add( new UIVector (mean, this, target, true, update ) );
		
		return out;
	}
	
	public boolean install( SelectedApps next ) {
		return false;
	}
	

	@Override
	public BufferedImage getMeanImage() {
		if ( meanImage != null )
			try {
				return ImageIO.read( meanImage );
			} catch ( IOException e ) {
				e.printStackTrace();
			}
		return null;
	}

	@Override
	public void setMeanImage( File f ) {
		this.meanImage = f;
	}

	@Override
	public void install( App app, AppStore ass ) {
		app.styleSource = new ConstantStyle( app.getClass() );
	}
}
