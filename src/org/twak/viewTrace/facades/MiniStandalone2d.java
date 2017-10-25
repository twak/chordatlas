package org.twak.viewTrace.facades;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;

import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Plot;
import org.twak.viewTrace.facades.MiniFacade.Feature;

public class MiniStandalone2d extends JPanel {
	
	Plot plot = new Plot();
	String folder;
	List<ImageFeatures> features;
	JSlider mfSlider;
	JCheckBox showOrig;
	JSlider ks, animate;
	
	public MiniStandalone2d( String folder ) {
		
		super (new BorderLayout());
		
		this.folder = folder;
		
		add(plot, BorderLayout.CENTER);
		
		MegaFeatures mf = new MegaFeatures( new File (folder  ) );
		
		for (ImageFeatures i : mf.features)
			for (MiniFacade m : i.miniFacades )
				plot.add( m );
		
		JPanel controls = new JPanel(new ListDownLayout());
		add(controls, BorderLayout.EAST);
		
		
		JButton go = new JButton("render");
		
		go.addActionListener( e -> renderAll(mf) );
		controls.add(go);
	}
	
	private void renderAll( MegaFeatures mf ) {
		
		int c = 0;
		for (ImageFeatures i : mf.features)
			for (MiniFacade m : i.miniFacades ) {
				
				if (m.width <  1)
					continue;
				
				BufferedImage bi = m.render( 80, Feature.DOOR, Feature.WINDOW );
				BufferedImage bi2 = m.render( 80 );
				
				try {
					ImageIO.write( bi, "png", new File ("/home/twak/Desktop/features"+c+".png") );
					ImageIO.write( bi2, "png", new File ("/home/twak/Desktop/rectified"+c+".png") );
				} catch ( IOException e ) {
					e.printStackTrace();
				}
				
				c++;
			}
	}




	public static void main (String[] args) {
		
		
		JFrame go = new JFrame("2D align");
//		go.add( new MiniStandalone2d( "/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/georgian/features_rendered/1003.4428411375009_-17.44980132326505/0/0/") );
		go.add( new MiniStandalone2d( "/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/georgian/features_rendered/-289.50965350676336_51.349370434170204/0/4") );
		go.setExtendedState( go.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		go.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		go.pack();
		go.setVisible( true );
	}
	
	 
}
