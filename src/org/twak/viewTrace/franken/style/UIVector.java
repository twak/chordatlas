package org.twak.viewTrace.franken.style;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.twak.utils.ui.FileDrop;
import org.twak.utils.ui.ListDownLayout;
import org.twak.viewTrace.facades.NSliders;
import org.twak.viewTrace.facades.Pix2Pix;
import org.twak.viewTrace.franken.App;

public class UIVector extends JPanel {

	App exemplar;
	double[] vector;
	
	public UIVector( double[] vector, App exemplar, Runnable update ) {
		
		this.vector = vector;
		
		setLayout( new BorderLayout() );
		JToggleButton method = new JToggleButton("by example");
		JPanel options = new JPanel(new ListDownLayout());
		
		method.addActionListener( e -> setUI (options, method.isSelected(), update ) );
		
		add (method, BorderLayout.NORTH );
		add (options, BorderLayout.CENTER );
		
		setUI (options, method.isSelected(), update );
//		setPreferredSize( new Dimension (200, 400) );
	}
	
	public void setUI (JPanel out, boolean byExample, Runnable update) {
		
		out.removeAll();

		if ( byExample ) {
			FileDrop drop = new FileDrop( "style" ) {
				public void process( java.io.File f ) {
					new Pix2Pix().encode( f, exemplar.resolution, exemplar.netName, vector, update );
				};
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
