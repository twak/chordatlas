package org.twak.viewTrace.facades;

import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.twak.utils.WeakListener.Changed;
import org.twak.utils.ui.ListDownLayout;

public class NSliders extends JPanel {

	double[] results;
	JSlider[] sliders;
	boolean updating = false;
	Changed c;
	
	public NSliders (double[] result, Changed c) {
		
		this.results = result;
		this.c = c;
		
		sliders = new JSlider[result.length];

		setLayout( new ListDownLayout() );
		
		for (int i = 0; i < result.length; i++) {
			
			int i_ = i;
			JSlider s = new JSlider( 0, 1000, 500 );
			s.setPreferredSize( new Dimension (100, s.getPreferredSize().height) );
//			s.setOrientation( SwingConstants.HORIZONTAL );
			sliders[i] = s;
			add( s );
			
			s.addChangeListener( new ChangeListener() {
				@Override
				public void stateChanged( ChangeEvent e ) {
					change( (JSlider) e.getSource(), i_);
				}
			} );
		}
	}

	public void setValues (double[] newValues) {
		updating = true;
		for (int i = 0; i < newValues.length; i ++) {
			System.out.println( newValues[i] );
			sliders[i].setValue( (int) ( (newValues[i] + 2) * 250 ) );
		}
		updating = false;
		c.changed();
	}
	
	private void change(JSlider s, int i) {
		results[i] = s.getValue() / 250.  - 2;
		if (!updating && !s.getValueIsAdjusting()) {
			c.changed();
		}
	}
	
}
