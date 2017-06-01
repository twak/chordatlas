package org.twak.viewTrace.facades;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import org.twak.tweed.dbg.Plot;
import org.twak.tweed.gen.FeatureGen;
import org.twak.tweed.gen.FeatureGen.ImageFeatures;
import org.twak.tweed.gen.FeatureGen.MegaFeatures;
import org.twak.tweed.gen.SkelGen.SimpleMass;
import org.twak.utils.Line;
import org.twak.utils.ListDownLayout;
import org.twak.utils.PaintThing;
import org.twak.utils.PaintThing.ICanPaintU;
import org.twak.utils.PanMouseAdaptor;

import com.thoughtworks.xstream.XStream;

public class MiniStandalone2d extends JPanel {
	
	Plot plot = new Plot();
	String folder;
	List<ImageFeatures> features;
	JSlider mfSlider;
	JCheckBox showOrig;
	List<Known> known = new ArrayList<>();
	JSlider ks, animate;
	
	public MiniStandalone2d( String folder ) {
		
		super (new BorderLayout());
		
		this.folder = folder;
		
		add(plot, BorderLayout.CENTER);
		
		JPanel controls = new JPanel(new ListDownLayout());
		add(controls, BorderLayout.EAST);
		
		
		JButton go = new JButton("go");
		go.addActionListener( e -> updateDataset() );
		controls.add(go);
		
		showOrig = new JCheckBox( "input" );
		showOrig.addActionListener( e -> plot() );
		controls.add(showOrig);
		
		known.add(new Known ("1", 0,2,1,2,2,0,3,0 ));
		known.add(new Known ("0", 4,2,5,1  ));
		known.add(new Known ("0", 3,3,4,1, 5,0  ));
		
		ks = new JSlider(0, known.size()-1);
		ks.addChangeListener( e -> setFolder() );
		controls.add(ks);
		
		mfSlider = new JSlider(-1, 3);
		controls.add(new JLabel("image"));
		controls.add(mfSlider);
		mfSlider.addChangeListener( e -> plot() );
		
		animate = new JSlider(0, 1000, 1000);
		controls.add(new JLabel("animate"));
		controls.add(animate);
		animate.addChangeListener( e -> plot() );
		
		setFolder();
	}

	private void setFolder() {
		
		Known k = known.get(ks.getValue());
		File dir = new File ( folder, k.folder );
		
		MegaFeatures mf = new MegaFeatures ( (Line) new XStream().fromXML( new File (dir, "line.xml") ) );
		
		features = new ArrayList<>();
		
		
		File[] files = dir.listFiles();
		Arrays.sort( files );
		
		for (File f : files) 
			if (f.isDirectory() )  {
				System.out.println(features.size()+" :: " + f.getName());
				ImageFeatures imf = FeatureGen.readFeatures (f, mf);
				if (imf != null)
					features.add( imf );
			}
		
		mfSlider.setMaximum( k.fM.size() );
		
		updateDataset();
	}
	
	private static class Known {
		String folder;
		List<int[]> fM = new ArrayList();
		public Known (String folder, int... fms) {
			this.folder = folder;
			for (int i = 0; i < fms.length; i+=2) {
				fM.add(new int[] {fms[i], fms[i+1]});
			}
		}
	}
	
	List<MiniFacade> mfs, mfs2;
	private void updateDataset() {
		
		Known k = known.get(ks.getValue());
		
		mfs = new ArrayList();
		
		for (int[] v : k.fM)
			mfs.add ( features.get( v[0] ).miniFacades.get(v[1]) );
		
		mfs2 = new Regularizer().goDebug(mfs , animate.getValue() / (double) animate.getMaximum(), null );
		
		plot();
	}
	
	private void plot() {
		
		plot.toPaint.clear();
		
		int toShow = mfSlider.getValue(); 
		
		
		MiniFacade.PAINT_IMAGE = showOrig.isSelected() && toShow >= 0;
		
		if (showOrig.isSelected()){
			for (int i = 0; i < mfs.size(); i++)
				if ( toShow == -1 || toShow -1 ==  i ) 
					plot.toPaint.add( mfs.get(i) );
		}
		else {
			new Thread() {
				public void run() {
					
					if (mfs2 != null)
					for (int i = 0; i < mfs2.size(); i++)
						if ( (toShow == -1 && i != 0 )|| toShow == i )
							plot.toPaint.add( mfs2.get(i) );
					plot.repaint();
				};
			}.start();
		}
				
		plot.repaint();
		
	}


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
//		go.add( new AlignStandalone2d( "/home/twak/Downloads/locations_april_6/ny/") );
		go.add( new MiniStandalone2d( "/home/twak/data/ny/features") );
		go.setExtendedState( go.getExtendedState() | JFrame.MAXIMIZED_BOTH);
		go.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		go.pack();
		go.setVisible( true );
	}
	
	 
}
