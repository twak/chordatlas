package org.twak.viewTrace.franken.style;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.SelectedApps;
import org.twak.viewTrace.franken.style.ui.UIVector;
import org.twak.viewTrace.franken.style.ui.UIVector.MeanImageProvider;

public class GaussStyle implements StyleSource, MeanImageProvider {

	public double[] mean;
	double std;
	NetInfo ni;
	File meanImage;

	public GaussStyle( NetInfo ni ) {
		this.mean = new double[ni.sizeZ];
		this.std = 0;
		this.ni = ni;
	}

	
	@Override
	public StyleSource copy() {
		GaussStyle out = new GaussStyle( ni );
		out.mean = Arrays.copyOf( mean, mean.length );
		out.std = std;
		out.meanImage = meanImage;
		return out;
	}
	@Override
	public double[] draw( Random random, App app ) {

		double[] out = new double[mean.length];

		for ( int i = 0; i < out.length; i++ )
			out[ i ] = random.nextGaussian() * std + mean[ i ];

		return out;
	}

	@Override
	public JPanel getUI( Runnable update ) {

		JPanel out = new JPanel( new ListDownLayout() );

		JPanel line = new JPanel( new BorderLayout() );

		line.add( new JLabel( "σ:" ), BorderLayout.WEST );

		JSlider deviation = new JSlider( 0, 1000, (int) ( std * 500 ) );

		line.add( deviation, BorderLayout.CENTER );

		out.add( line );
		deviation.addChangeListener( new ChangeListener() {
			@Override
			public void stateChanged( ChangeEvent e ) {
				if ( !deviation.getValueIsAdjusting() ) {
					std = deviation.getValue() / 500.;
					update.run();
				}
			}
		} );

		//		JButton go = new JButton("resample");
		//		go.addActionListener( e -> update.run() );
		//		out.add( go );

		out.add( new UIVector( mean, this, ni, false, update ) );

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
}
