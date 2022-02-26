package org.twak.viewTrace.facades;

import java.awt.BorderLayout;
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
import org.twak.tweed.gen.FeatureCache;
import org.twak.tweed.gen.FeatureCache.ImageFeatures;
import org.twak.tweed.gen.FeatureCache.MegaFeatures;
import org.twak.utils.Line;
import org.twak.utils.ui.ListDownLayout;
import org.twak.utils.ui.Plot;
import org.twak.utils.ui.SaveLoad;
import org.twak.utils.ui.WindowManager;

import com.thoughtworks.xstream.XStream;

public class AlignStandalone2d extends JPanel {
	
	Plot plot = new Plot();
	
	String folder;
	JSlider imageSlide, facadeSlide;//, massSlide;
	List<ImageFeatures> features;
	List<File> facadeFolders;
	
	public AlignStandalone2d( String folder ) {
		
		super (new BorderLayout());
		
//		TweedSettings.load( new File(folder).getParentFile() );
		
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
		
//		JButton dump = new JButton("dump");
//		controls.add(dump);
//		dump.addActionListener( new ActionListener() {
//			
//			@Override
//			public void actionPerformed( ActionEvent e ) {
//				for (int i = facadeSlide.getMinimum(); i <= facadeSlide.getMaximum(); i++) {
//					facadeSlide.setValue( i );
//					
//					for (int j = imageSlide.getMinimum(); j < imageSlide.getMaximum(); j++) {
//						imageSlide.setValue( j );
//						plot();
//						plot.writeImage(Tweed.SCRATCH+i+"_"+(j==-1? "all":("image_"+j) )+"_align");
//					}
//				}
//			}
//		} );
//		
		setFolder( facadeFolders.get ( facadeSlide.getValue() ));	
	}

	private void setFolder( File folder ) {

		if (!folder.exists())
			return;
		
		MegaFeatures mf = new MegaFeatures((Line) SaveLoad.createXStream().fromXML( new File (folder, "line.xml") ));
		
		features = new ArrayList<>();
		
		File[] files = folder.listFiles();
		
		
		Arrays.sort( files, FILE_COMPARATOR );
		
		for ( File f : files )
			if (f.isDirectory() )  {
				System.out.println(features.size()+" :: " + f.getName());
				ImageFeatures imf = FeatureCache.readFeatures (f, mf);
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
				
//				plot.toPaint.add( f );
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

	
	public static JFrame show (String file ) {
		JFrame go = new JFrame("2D align");
		
		go.add( new AlignStandalone2d( file ) );
		go.setExtendedState( go.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		
		go.pack();
		go.setVisible( true );
		
		WindowManager.register( go );
		
		return go;
	}

	public static void main (String[] args) {
		show ( "/home/twak/data/regent/features/652.9836272423689_-455.4482046683377").setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		
	}
	
	 
}
