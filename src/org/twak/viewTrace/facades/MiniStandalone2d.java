package org.twak.viewTrace.facades;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;

import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.utils.Parallel;
import org.twak.utils.Parallel.Work;
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
		go.addActionListener( e -> renderAll( mf, "/home/twak/Desktop" ) );
		controls.add(go);
		
		JButton bulk = new JButton("render all");
		bulk.addActionListener( e -> bulk() );
		controls.add(bulk);
	}
	

	private void renderAll( MegaFeatures mf, String out ) {
		
		System.out.println( "processing " + mf + " into " + out );
		
		int c = 0;
		
		for (ImageFeatures i : mf.features)
			for (MiniFacade m : i.miniFacades ) {
				
				if (m.width <  3 || m.height < 3)
					continue;
				
				BufferedImage bi = m.render( 40, Feature.DOOR, Feature.WINDOW );
				BufferedImage bi2 = m.render( 40 );
				
				File outF = new File (out).getParentFile();
				outF.mkdirs();
				
				try {
					ImageIO.write( bi, "jpg", new File ( out +"_"+c+"_im.jpg") );
					ImageIO.write( bi2, "jpg", new File ( out +"_"+c+"_ft.jpg") );
				} catch ( IOException e ) {
					e.printStackTrace();
				}
				
				c++;
			}
	}
	
//	upload to /media/data/guerrero/projects/greeble/data/facade_sources/features

	private void bulk() {
		
		File parentFile = new File ("/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/small");
		File outDir = new File ("/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/f2");
		
		try {
			
			List<File> togo = Files.walk( parentFile.toPath() ).
				map( p -> p.toFile() ).
				filter( f -> f.isDirectory() && new File (f, "parameters.yml").exists() ).
				collect( Collectors.toList() );
			
			new Parallel<File, Integer>( togo, new Work<File, Integer>() {
				public Integer work( File f ) {
					renderAll( new MegaFeatures( f ), 
							new File( outDir, parentFile.toPath().relativize( f.toPath() ).toString() ).getPath() );
					return 1;
				}
			}, null, true );
//					togo , new Work<File, Integer>() {
//				
//			}, new Parallel.Complete<Integer>() {
//					
//			Files.walk( parentFile.toPath() ).
//			map( p -> p.toFile() ).
//			filter( f -> f.isDirectory() && new File (f, "parameters.yml").exists() ).
//			forEach( f -> );
		
//			}, true );
			
			
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}


	public static void main (String[] args) {
		
		
		JFrame go = new JFrame("2D align");
		go.add( new MiniStandalone2d( "/media/twak/8bc5e750-9a70-4180-8eee-ced2fbba6484/data/georgian/features_rendered/1003.4428411375009_-17.44980132326505/0/0/") );
//		go.add( new MiniStandalone2d( "/home/twak/Desktop//georgian/1786.5564175924799_-407.3284375105796_0_75") );
		go.setExtendedState( go.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		go.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		go.pack();
		go.setVisible( true );
	}
	
	 
}
