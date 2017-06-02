package org.twak.viewTrace.facades;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.tweed.Tweed;
import org.twak.tweed.TweedSettings;
import org.twak.tweed.dbg.Plot;
import org.twak.tweed.gen.FeatureGen;
import org.twak.tweed.gen.FeatureGen.ImageFeatures;
import org.twak.tweed.gen.FeatureGen.MegaFeatures;
import org.twak.tweed.gen.SkelGen.SimpleMass;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaintU;
import org.twak.utils.geom.Line;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.PanMouseAdaptor;

import com.thoughtworks.xstream.XStream;

public class AlignStandalone2d extends JPanel {
	
	Plot plot = new Plot();
	
	String folder;
	JSlider imageSlide, facadeSlide;//, massSlide;
	List<ImageFeatures> features;
	List<File> facadeFolders;
	
	public AlignStandalone2d( String folder ) {
		
		super (new BorderLayout());
		
		TweedSettings.load( new File(folder).getParentFile() );
		
		this.folder = folder;
		
		add(plot, BorderLayout.CENTER);
		
		JPanel controls = new JPanel(new ListDownLayout());
		add(controls, BorderLayout.EAST);
		
		facadeFolders = new ArrayList ( Arrays.asList( new File (folder).listFiles() ) );
		Collections.sort(facadeFolders, FILE_COMPARATOR);
		
		facadeSlide = new JSlider(0, facadeFolders.size()-1, 0);
		controls.add(facadeSlide);
		facadeSlide.addChangeListener( new ChangeListener() {
			@Override
			public void stateChanged( ChangeEvent e ) {
				setFolder( facadeFolders.get ( facadeSlide.getValue() ));
			}
		} );
		
		imageSlide = new JSlider(-1, 1);
		controls.add(imageSlide);
		imageSlide.addChangeListener( new ChangeListener() {
			@Override
			public void stateChanged( ChangeEvent e ) {
				plot();
			}
		} );
		
		JButton dump = new JButton("dump");
		controls.add(dump);
		dump.addActionListener( new ActionListener() {
			
			@Override
			public void actionPerformed( ActionEvent e ) {
				for (int i = facadeSlide.getMinimum(); i <= facadeSlide.getMaximum(); i++) {
					facadeSlide.setValue( i );
					
					for (int j = imageSlide.getMinimum(); j < imageSlide.getMaximum(); j++) {
						imageSlide.setValue( j );
						plot();
						plot.writeImage(Tweed.CONFIG+i+"_"+(j==-1? "all":("image_"+j) )+"_align");
					}
				}
			}
		} );
		
		setFolder( facadeFolders.get ( facadeSlide.getValue() ));	
	}

	private void setFolder( File folder ) {

		if (!folder.exists())
			return;
		
		MegaFeatures mf = new MegaFeatures((Line) new XStream().fromXML( new File (folder, "line.xml") ));
		
		features = new ArrayList<>();
		
		File[] files = folder.listFiles();
		
		
		Arrays.sort( files, FILE_COMPARATOR );
		
		for ( File f : files )
			if (f.isDirectory() )  {
				System.out.println(features.size()+" :: " + f.getName());
				ImageFeatures imf = FeatureGen.readFeatures (f, mf);
				if (imf != null)
					features.add( imf );
			}
		
		imageSlide.setMaximum( features.size() - 1 );
		imageSlide.setValue( 0 );
		
		plot();
	}
	
	private void plot() {
		
		if (features == null)
			return;
		
		plot.toPaint.clear();
		
		for (int i = 0; i < features.size(); i++) 
			if (imageSlide.getValue() == -1 || imageSlide.getValue() == i) {
				
				ImageFeatures f = features.get(i);
				
				plot.toPaint.add( f );
			}
		
		for (int i = 0; i < features.size(); i++) 
			if (imageSlide.getValue() == -1 || imageSlide.getValue() == i) {
				
				ImageFeatures f = features.get(i);
				
				for (MiniFacade mf : f.miniFacades) 
					plot.toPaint.add(mf);
		}
		
		plot.toPaint.add( facadeFolders.get ( facadeSlide.getValue() ).getName() + "/" + imageSlide.getValue() );
		plot.repaint();
	}
	
	Comparator<File> FILE_COMPARATOR = new Comparator<File>() {

		@Override
		public int compare( File o1, File o2 ) {
			
			String 
					a = o1.getName(), 
					b = o2.getName();
			
			try {
				return Integer.compare( Integer.parseInt(a), Integer.parseInt( b ) );
			}
			catch (Throwable th) {
				return a.compareTo( b );
			}
		}
	};


	public static void main (String[] args) {
		
		PaintThing.lookup.put(SimpleMass.class, new ICanPaintU() {
			@Override
			public void paint( Object o_, Graphics2D g, PanMouseAdaptor ma ) {
				SimpleMass o = (SimpleMass)o_;
				
				g.drawLine ( 0, ma.toY(0), 10000, ma.toY(0) );
				g.setColor( new Color( 50, 50, 50, 200 ) );
				g.fillRect( ma.toX( o.start ), ma.toY(0), -10 , 10 );
				
				int width = Math.abs ( ma.toZoom( o.start - o.end ) ), height = ma.toZoom( o.height );
				double left = Math.min (o.start, o.end);
				
				g.fillRect( ma.toX( left ), ma.toY(0)-height, width, height );
				g.drawRect( ma.toX( left ), ma.toY(0)-height, width, height );
			}
		});
		
		
		
		JFrame go = new JFrame("2D align");
//		go.add( new AlignStandalone2d( "/home/twak/Downloads/locations_april_6/madrid/") );
//		go.add( new AlignStandalone2d( "/home/twak/data/regent/features/") );
//		go.add( new AlignStandalone2d( "/home/twak/data/oviedo/euromaster") );
//		go.add( new AlignStandalone2d( "/home/twak/data/oviedo/features") );
//		go.add( new AlignStandalone2d( "/home/twak/data/oviedo/features/12.633729738662927_-21.125014753917988/") );
		go.add( new AlignStandalone2d( "/home/twak/data/regent/features/652.9836272423689_-455.4482046683377") );
//		go.add( new AlignStandalone2d( "/home/twak/data/ny/features") );
//		go.add( new AlignStandalone2d( "/home/twak/data/regent/March_30/congo/") );
//		go.add( new AlignStandalone2d( "/home/twak/Downloads/media/femianjc/My Book/facade-output/location_11_April/detroit-masks") );
//		go.add( new AlignStandalone2d( "/home/twak/Downloads/media/femianjc/My Book/facade-output/location_11_April/glasgow-masks-2") );
//		go.add( new AlignStandalone2d( "/home/twak/Downloads/media/femianjc/My Book/facade-output/location_11_April/madrid-masks-2") );
//		go.add( new AlignStandalone2d( "/home/twak/data/regent/May_4/cock") );
		go.setExtendedState( go.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		go.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		go.pack();
		go.setVisible( true );
	}
	
	 
}
