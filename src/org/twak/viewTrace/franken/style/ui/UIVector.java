package org.twak.viewTrace.franken.style.ui;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.franken.NetInfo;
import org.twak.viewTrace.franken.Pix2Pix;

public class UIVector extends JPanel {

	double[] vector;
	JToggleButton method ;
	MeanImageProvider imageFile;
	Class target;
	
	public UIVector( double[] vector, MeanImageProvider imageFile, Class target, boolean showManual, Runnable update ) {

		this.target = target;
		this.vector = vector;
		this.imageFile = imageFile;
		
		setLayout( new BorderLayout() );
		
		method = new JToggleButton("manual");

		method.setSelected( false );//imageFile.getMeanImage() == null );
		
		JPanel options = new JPanel(new ListDownLayout());
		
		method.addActionListener( e -> setUI (options, !method.isSelected(), update ) );
		
		if (showManual)
			add (method, BorderLayout.NORTH );
		
		add (options, BorderLayout.CENTER );
		
		setUI (options, !method.isSelected(), update );
	}
	
	public void setUI (JPanel out, boolean byExample, Runnable update) {
		
		out.removeAll();

		
		if ( byExample ) {
			
			ImageFileDrop drop = new ImageFileDrop(imageFile.getMeanImage()) {
				
				public BufferedImage process( File f ) {
					imageFile.setMeanImage( f );
					return new Pix2Pix( NetInfo.index.get( target) ).encode( f, vector, update );
				};
				
				@Override
				public void rightClick() {
					super.rightClick();
					method.doClick();
				}
			};
			out.add( drop );
		} else {

//			imageFile.setMeanImage( null );
			
			NSliders sliders = new NSliders( vector, update, new Runnable() {
				@Override
				public void run() {
					method.doClick();
				}
				@Override
				public String toString() {
					return "from image";
				}
			} );
			out.add( sliders );
		}
		
		out.repaint();
		out.revalidate();
	}
	
	public interface MeanImageProvider {
		public BufferedImage getMeanImage();
		public void setMeanImage (File f);
	}
}
