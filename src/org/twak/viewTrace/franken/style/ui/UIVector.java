package org.twak.viewTrace.franken.style.ui;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.NSliders;
import org.twak.viewTrace.franken.App;
import org.twak.viewTrace.franken.Pix2Pix;

public class UIVector extends JPanel {

	App exemplar;
	double[] vector;
	JToggleButton method ;
	
	public UIVector( double[] vector, App exemplar, boolean showManual, Runnable update ) {
		
		this.exemplar = exemplar;
		this.vector = vector;
		
		setLayout( new BorderLayout() );
		
		method = new JToggleButton("manual");
		
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
			ImageFileDrop drop = new ImageFileDrop() {
				public BufferedImage process( java.io.File f ) {
					return new Pix2Pix(exemplar).encode( f, vector, update );
				};
				
				@Override
				public void rightClick() {
					super.rightClick();
					method.doClick();
				}
			};
			out.add( drop );
		} else {

			NSliders sliders = new NSliders( vector, update );
			out.add( sliders );
		}
		
		out.repaint();
		out.revalidate();
	}
}
